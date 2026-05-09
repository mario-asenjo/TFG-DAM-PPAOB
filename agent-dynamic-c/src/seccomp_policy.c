#include "agent_dynamic.h"

#include <seccomp.h>

static int	deny_rule(scmp_filter_ctx ctx, int syscall_number)
{
	if (syscall_number < 0)
		return (0);
	if (seccomp_rule_add(ctx, SCMP_ACT_ERRNO(1), syscall_number, 0) < 0)
		return (-1);
	return (0);
}

int	install_seccomp_baseline(void)
{
	scmp_filter_ctx	ctx;

	ctx = seccomp_init(SCMP_ACT_ALLOW);
	if (!ctx)
		return (-1);
	if (deny_rule(ctx, SCMP_SYS(ptrace)) < 0
		|| deny_rule(ctx, SCMP_SYS(kexec_load)) < 0
		|| deny_rule(ctx, SCMP_SYS(open_by_handle_at)) < 0
		|| deny_rule(ctx, SCMP_SYS(init_module)) < 0
		|| deny_rule(ctx, SCMP_SYS(finit_module)) < 0
		|| deny_rule(ctx, SCMP_SYS(delete_module)) < 0
		|| deny_rule(ctx, SCMP_SYS(mount)) < 0
		|| deny_rule(ctx, SCMP_SYS(umount2)) < 0
		|| deny_rule(ctx, SCMP_SYS(reboot)) < 0)
		return (seccomp_release(ctx), -1);
	if (seccomp_load(ctx) < 0)
		return (seccomp_release(ctx), -1);
	seccomp_release(ctx);
	return (0);
}
