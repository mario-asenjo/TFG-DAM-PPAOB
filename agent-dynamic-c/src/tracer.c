#include "agent_dynamic.h"

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/user.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static unsigned long	now_ms(void)
{
	struct timespec	ts;

	clock_gettime(CLOCK_MONOTONIC, &ts);
	return ((unsigned long)(ts.tv_sec * 1000UL) + (unsigned long)(ts.tv_nsec / 1000000UL));
}

static void	init_result(t_trace_result *result)
{
	memset(result, 0, sizeof(*result));
	result->exit_code = -1;
	result->termination_signal = 0;
	result->last_syscall = -1;
}

int	read_remote_cstring(pid_t pid, unsigned long addr, char *out, size_t out_len)
{
	unsigned long	word;
	size_t		i;
	size_t		j;
	unsigned long	current;

	if (!out || out_len == 0 || addr == 0)
		return (-1);
	i = 0;
	while (i + sizeof(unsigned long) <= out_len)
	{
		errno = 0;
		current = addr + (unsigned long)i;
		word = (unsigned long)ptrace(PTRACE_PEEKDATA, pid, (void *)current, NULL);
		if (errno != 0)
			break ;
		j = 0;
		while (j < sizeof(unsigned long) && i + j < out_len - 1)
		{
			out[i + j] = (char)((word >> (j * 8)) & 0xff);
			if (out[i + j] == '\0')
				return (0);
			j++;
		}
		i += sizeof(unsigned long);
	}
	out[out_len - 1] = '\0';
	return (0);
}

static int	is_fs_syscall(long id)
{
	return (id == 2 || id == 21 || id == 257 || id == 4 || id == 262 || id == 59);
}

static void	remember_fs_path(t_trace_result *result, int syscall_number, const char *path)
{
	size_t	i;

	if (!path || !*path || result->fs_count >= 64)
		return ;
	i = 0;
	while (i < result->fs_count)
	{
		if (strncmp(result->fs_paths[i], path, 255) == 0)
			return ;
		i++;
	}
	strncpy(result->fs_paths[result->fs_count], path, 255);
	result->fs_paths[result->fs_count][255] = '\0';
	result->fs_syscalls[result->fs_count] = syscall_number;
	result->fs_count++;
}

static int	spawn_child(const t_agent_args *args)
{
	pid_t	pid;
	int	devnull;

	pid = fork();
	if (pid == 0)
	{
		devnull = open("/dev/null", O_RDWR);
		if (devnull >= 0)
		{
			dup2(devnull, STDIN_FILENO);
			dup2(devnull, STDOUT_FILENO);
			dup2(devnull, STDERR_FILENO);
			close(devnull);
		}
		ptrace(PTRACE_TRACEME, 0, NULL, NULL);
		raise(SIGSTOP);
		if (install_seccomp_baseline() != 0)
			_exit(126);
		execl(args->input_path, args->input_path, (char *)NULL);
		_exit(127);
	}
	if (pid < 0)
		return (-1);
	return ((int)pid);
}

static void	record_signal(t_trace_result *result, int signal_number)
{
	if (signal_number == (SIGTRAP | 0x80))
		return ;
	if (result->signal_count < 32)
		result->signal_history[result->signal_count++] = signal_number;
}

static void	record_syscall(t_trace_result *result, const t_agent_args *args, pid_t pid)
{
	struct user_regs_struct	regs;
	long			id;
	long			retval;
	char			path[256];
	unsigned long	path_addr;
	int			entering;

	if (ptrace(PTRACE_GETREGS, pid, NULL, &regs) != 0)
		return ;
	id = (long)regs.orig_rax;
	retval = (long)regs.rax;
	entering = (result->last_syscall < 0);
	path[0] = '\0';
	if (entering)
	{
		if (is_fs_syscall(id))
		{
			path_addr = (unsigned long)regs.rdi;
			if (id == 257 || id == 262)
				path_addr = (unsigned long)regs.rsi;
			read_remote_cstring(pid, path_addr, path, sizeof(path));
			remember_fs_path(result, (int)id, path);
		}
		result->last_syscall = (int)id;
	}
	else
	{
		if (result->last_syscall >= 0 && result->last_syscall < SYSCALL_BUCKETS)
		{
			result->syscall_counts[result->last_syscall]++;
			result->total_syscalls++;
		}
		if (retval == -EPERM)
			result->denied_syscalls++;
		result->last_syscall = -1;
	}
	result->last_seq++;
	result->total_events++;
	append_trace_event(args, result->last_seq, entering, (int)id, retval, path[0] ? path : NULL);
}

static int	wait_initial_stop(pid_t pid)
{
	int	status;

	if (waitpid(pid, &status, 0) < 0)
		return (-1);
	if (!WIFSTOPPED(status))
		return (-1);
	if (ptrace(PTRACE_SETOPTIONS, pid, NULL,
			PTRACE_O_TRACESYSGOOD | PTRACE_O_EXITKILL) != 0)
		return (-1);
	if (ptrace(PTRACE_SYSCALL, pid, NULL, 0) != 0)
		return (-1);
	return (0);
}

static int	handle_wait_status(const t_agent_args *args, pid_t pid, int status, t_trace_result *result)
{
	int	signal_number;

	if (WIFEXITED(status))
	{
		result->exit_code = WEXITSTATUS(status);
		return (1);
	}
	if (WIFSIGNALED(status))
	{
		signal_number = WTERMSIG(status);
		result->termination_signal = signal_number;
		result->exit_code = 128 + signal_number;
		record_signal(result, signal_number);
		return (1);
	}
	if (WIFSTOPPED(status))
	{
		signal_number = WSTOPSIG(status);
		if (signal_number == (SIGTRAP | 0x80))
		{
			record_syscall(result, args, pid);
			if (ptrace(PTRACE_SYSCALL, pid, NULL, 0) != 0)
				return (-1);
			return (0);
		}
		if (signal_number == SIGTRAP)
		{
			if (ptrace(PTRACE_SYSCALL, pid, NULL, 0) != 0)
				return (-1);
			return (0);
		}
		record_signal(result, signal_number);
		if (ptrace(PTRACE_SYSCALL, pid, NULL, signal_number) != 0)
			return (-1);
	}
	return (0);
}

int	trace_program(const t_agent_args *args, t_trace_result *result)
{
	pid_t		pid;
	int		status;
	int		wait_result;
	unsigned long	started_ms;
	unsigned long	current_ms;

	init_result(result);
	pid = (pid_t)spawn_child(args);
	if (pid < 0 || wait_initial_stop(pid) != 0)
		return (-1);
	started_ms = now_ms();
	while (1)
	{
		current_ms = now_ms();
		if (!result->timed_out && (int)(current_ms - started_ms) > args->timeout_ms)
		{
			kill(pid, SIGKILL);
			result->timed_out = 1;
		}
		wait_result = waitpid(pid, &status, __WALL | WNOHANG);
		if (wait_result < 0)
			break ;
		if (wait_result == 0)
		{
			usleep(1000);
			continue ;
		}
		if (handle_wait_status(args, pid, status, result) != 0)
			break ;
	}
	result->duration_ms = now_ms() - started_ms;
	if (result->timed_out)
	{
		result->exit_code = 124;
		result->termination_signal = SIGKILL;
	}
	return (0);
}
