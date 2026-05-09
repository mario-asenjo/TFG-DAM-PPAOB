#include "agent_dynamic.h"

#include <stddef.h>

const char	*syscall_name(int syscall_number)
{
	if (syscall_number == 0)
		return ("read");
	if (syscall_number == 1)
		return ("write");
	if (syscall_number == 2)
		return ("open");
	if (syscall_number == 3)
		return ("close");
	if (syscall_number == 9)
		return ("mmap");
	if (syscall_number == 10)
		return ("mprotect");
	if (syscall_number == 11)
		return ("munmap");
	if (syscall_number == 39)
		return ("getpid");
	if (syscall_number == 59)
		return ("execve");
	if (syscall_number == 60)
		return ("exit");
	if (syscall_number == 231)
		return ("exit_group");
	return ("unknown");
}

static void	insert_candidate(t_top_syscall *top,
				size_t top_count,
				int number,
				unsigned long count)
{
	size_t	i;

	i = 0;
	while (i < top_count)
	{
		if (count > top[i].count)
		{
			while (top_count > i + 1)
			{
				top[top_count - 1] = top[top_count - 2];
				top_count--;
			}
			top[i].number = number;
			top[i].count = count;
			return ;
		}
		i++;
	}
}

void	compute_top_syscalls(const t_trace_result *result,
			t_top_syscall *top,
			size_t top_count)
{
	size_t	i;

	i = 0;
	while (i < top_count)
	{
		top[i].number = -1;
		top[i].count = 0;
		i++;
	}
	i = 0;
	while (i < SYSCALL_BUCKETS)
	{
		if (result->syscall_counts[i] > 0)
			insert_candidate(top, top_count, (int)i, result->syscall_counts[i]);
		i++;
	}
}
