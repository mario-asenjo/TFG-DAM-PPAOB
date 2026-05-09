#ifndef AGENT_DYNAMIC_H
# define AGENT_DYNAMIC_H

# include <stddef.h>
# include <sys/types.h>

# define SYSCALL_BUCKETS 1024

typedef struct s_agent_args
{
	const char	*input_path;
	const char	*output_path;
	const char	*trace_output_path;
	int		timeout_ms;
} t_agent_args;

typedef struct s_top_syscall
{
	int		number;
	unsigned long	count;
} t_top_syscall;

typedef struct s_trace_result
{
	int		exit_code;
	int		timed_out;
	int		termination_signal;
	unsigned long	duration_ms;
	unsigned long	total_syscalls;
	unsigned long	denied_syscalls;
	unsigned long	total_events;
	unsigned long	syscall_counts[SYSCALL_BUCKETS];
	int		signal_history[32];
	int		fs_syscalls[64];
	char		fs_paths[64][256];
	size_t		fs_count;
	int		last_syscall;
	unsigned long	last_seq;
	size_t		signal_count;
} t_trace_result;

int	parse_args(int argc, char **argv, t_agent_args *args);
int	install_seccomp_baseline(void);
int	trace_program(const t_agent_args *args, t_trace_result *result);
int	write_json_output(const t_agent_args *args, const t_trace_result *result);
int	append_trace_event(const t_agent_args *args,
		unsigned long seq,
		int entering,
		int syscall_number,
		long retval,
		const char *path);
int	read_remote_cstring(pid_t pid, unsigned long addr, char *out, size_t out_len);
void	compute_top_syscalls(const t_trace_result *result,
			t_top_syscall *top,
			size_t top_count);
const char	*syscall_name(int syscall_number);

#endif
