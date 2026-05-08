# Example Programs

Small C samples to exercise current static/dynamic correlation cases.

All files are intended to compile with:

```bash
cc file.c -o file
```

## Files

- `benign_hello.c`: baseline benign sample.
- `unsafe_strcpy.c`: unsafe copy pattern (`strcpy`).
- `command_exec_system.c`: command execution pattern (`system`, `/bin/sh`).
- `network_client_socket.c`: network behavior (`socket`, `connect`, `send`).
- `privilege_and_sensitive_paths.c`: privilege and sensitive path indicators (`setuid`, `setgid`, `/etc/passwd`, `/etc/shadow`, `/root/.ssh`, `/var/run/docker.sock`).
- `combined_remote_exec_pattern.c`: combined chain (`strcpy` + network + command execution).
- `timeout_like_loop.c`: long-running loop useful for timeout behavior checks.
