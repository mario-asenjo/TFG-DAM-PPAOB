#include "agent_dynamic.h"

#include <stdio.h>

static void	print_usage(void)
{
	fprintf(stderr,
		"Usage: agent_dynamic --input <binary> --output <json> --trace-output <ndjson> [--timeout-ms <100..120000>]\n");
}

int	main(int argc, char **argv)
{
	t_agent_args	args;
	t_trace_result	result;
	FILE		*trace_file;

	if (parse_args(argc, argv, &args) != 0)
		return (print_usage(), 2);
	trace_file = fopen(args.trace_output_path, "w");
	if (!trace_file)
		return (fprintf(stderr, "trace file open failed\n"), 5);
	fclose(trace_file);
	if (trace_program(&args, &result) != 0)
		return (fprintf(stderr, "trace_program failed\n"), 3);
	if (write_json_output(&args, &result) != 0)
		return (fprintf(stderr, "write_json_output failed\n"), 4);
	return (0);
}
