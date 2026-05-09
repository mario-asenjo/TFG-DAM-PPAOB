#include "agent_dynamic.h"

#include <stdio.h>
#include <string.h>

int	append_trace_event(const t_agent_args *args,
		unsigned long seq,
		int entering,
		int syscall_number,
		long retval,
		const char *path)
{
	FILE	*file;

	if (!args || !args->trace_output_path)
		return (0);
	file = fopen(args->trace_output_path, "a");
	if (!file)
		return (-1);
	fprintf(file,
		"{\"seq\":%lu,\"phase\":\"%s\",\"number\":%d,\"name\":\"%s\",\"return\":%ld",
		seq,
		entering ? "enter" : "exit",
		syscall_number,
		syscall_name(syscall_number),
		retval);
	if (path && path[0] != '\0')
		fprintf(file, ",\"path\":\"%s\"", path);
	fprintf(file, "}\n");
	fclose(file);
	return (0);
}

static void	write_signals(FILE *file, const t_trace_result *result)
{
	size_t	i;

	fprintf(file, "\"signals\":[");
	i = 0;
	while (i < result->signal_count)
	{
		if (i > 0)
			fprintf(file, ",");
		fprintf(file, "%d", result->signal_history[i]);
		i++;
	}
	fprintf(file, "],");
}

static void	write_syscalls(FILE *file, const t_trace_result *result)
{
	t_top_syscall	top[12];
	size_t		i;

	compute_top_syscalls(result, top, 12);
	fprintf(file, "\"topSyscalls\":[");
	i = 0;
	while (i < 12)
	{
		if (top[i].count == 0)
			break ;
		if (i > 0)
			fprintf(file, ",");
		fprintf(file,
			"{\"number\":%d,\"name\":\"%s\",\"count\":%lu}",
			top[i].number,
			syscall_name(top[i].number),
			top[i].count);
		i++;
	}
	fprintf(file, "]");
}

static void	write_filesystem(FILE *file, const t_trace_result *result)
{
	size_t	i;

	fprintf(file, "\"filesystem\":{\"uniquePaths\":%zu,\"paths\":[", result->fs_count);
	i = 0;
	while (i < result->fs_count)
	{
		if (i > 0)
			fprintf(file, ",");
		fprintf(file,
			"{\"syscall\":\"%s\",\"number\":%d,\"path\":\"%s\"}",
			syscall_name(result->fs_syscalls[i]),
			result->fs_syscalls[i],
			result->fs_paths[i]);
		i++;
	}
	fprintf(file, "]},");
}

int	write_json_output(const t_agent_args *args, const t_trace_result *result)
{
	FILE	*file;
	const char	*termination_reason;

	file = fopen(args->output_path, "w");
	if (!file)
		return (-1);
	termination_reason = "NORMAL";
	if (result->timed_out)
		termination_reason = "TIMEOUT";
	else if (result->termination_signal > 0)
		termination_reason = "SIGNAL";
	fprintf(file, "{");
	fprintf(file, "\"schemaVersion\":1,");
	fprintf(file, "\"tool\":\"agent-dynamic-c\",");
	fprintf(file, "\"profile\":\"DYNAMIC_BASELINE\",");
	fprintf(file,
		"\"runtime\":{\"durationMs\":%lu,\"exitCode\":%d,\"timedOut\":%s,\"terminationReason\":\"%s\",\"signalNumber\":%d},",
		result->duration_ms,
		result->exit_code,
		result->timed_out ? "true" : "false",
		termination_reason,
		result->termination_signal);
	fprintf(file,
		"\"policy\":{\"name\":\"SECCOMP_BASELINE_V1\",\"deniedCount\":%lu},",
		result->denied_syscalls);
	write_filesystem(file, result);
	write_signals(file, result);
	write_syscalls(file, result);
	fprintf(file, "}");
	fclose(file);
	return (0);
}
