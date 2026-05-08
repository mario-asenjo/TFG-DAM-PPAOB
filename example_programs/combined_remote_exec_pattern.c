#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

int main(int argc, char **argv)
{
    char cmd[128];
    int fd;
    struct sockaddr_in addr;

    if (argc > 1)
        strcpy(cmd, argv[1]);
    else
        strcpy(cmd, "/bin/sh -c 'echo combined sample'");

    fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd >= 0)
    {
        memset(&addr, 0, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_port = htons(8080);
        inet_pton(AF_INET, "127.0.0.1", &addr.sin_addr);
        connect(fd, (struct sockaddr *)&addr, sizeof(addr));
        close(fd);
    }

    return system(cmd);
}
