#include <fcntl.h>
#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>

int main(void)
{
    const char *paths[] = {
        "/etc/passwd",
        "/etc/shadow",
        "/root/.ssh/id_rsa",
        "/var/run/docker.sock"
    };
    int i;

    setgid(getgid());
    setuid(getuid());

    i = 0;
    while (i < 4)
    {
        int fd = open(paths[i], O_RDONLY);
        if (fd >= 0)
            close(fd);
        i++;
    }

    puts("privilege and sensitive path sample");
    return 0;
}
