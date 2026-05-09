#include "agent_dynamic.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int	is_flag(const char *value, const char *expected)
{
	if (!value || !expected)
		return (0);
	return (strcmp(value, expected) == 0);
}

static int	parse_timeout(const char *value)
{
	long	parsed;

	parsed = strtol(value, NULL, 10);
	if (parsed < 100 || parsed > 120000)
		return (-1);
	return ((int)parsed);
}

int	parse_args(int argc, char **argv, t_agent_args *args)
{
	int	i;

	if (!args)
		return (-1);
	memset(args, 0, sizeof(*args));
	args->timeout_ms = 5000;
	i = 1;
	while (i < argc)
	{
		if (is_flag(argv[i], "--input") && i + 1 < argc)
			args->input_path = argv[++i];
		else if (is_flag(argv[i], "--output") && i + 1 < argc)
			args->output_path = argv[++i];
		else if (is_flag(argv[i], "--trace-output") && i + 1 < argc)
			args->trace_output_path = argv[++i];
		else if (is_flag(argv[i], "--timeout-ms") && i + 1 < argc)
			args->timeout_ms = parse_timeout(argv[++i]);
		else
			return (-1);
		i++;
	}
	if (!args->input_path || !args->output_path || !args->trace_output_path || args->timeout_ms < 0)
		return (-1);
	return (0);
}
