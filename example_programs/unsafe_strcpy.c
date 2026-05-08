#include <stdio.h>
#include <string.h>

int main(int argc, char **argv)
{
    char buffer[32];
    const char *src;

    src = (argc > 1) ? argv[1] : "default-inputdefault-inputdefault-inputdefault-input";
    strcpy(buffer, src);
    printf("copied: %s\n", buffer);
    return 0;
}
