NAME = agent_dynamic

CC = cc
CFLAGS = -Wall -Wextra -Werror
LDFLAGS = -lseccomp

SRC = src/main.c src/args.c src/seccomp_policy.c src/tracer.c src/json_output.c src/syscall_names.c
OBJ = $(SRC:.c=.o)

all: $(NAME)

$(NAME): $(OBJ)
	$(CC) $(CFLAGS) $(OBJ) -o $(NAME) $(LDFLAGS)

clean:
	rm -f $(OBJ)

fclean: clean
	rm -f $(NAME)

re: fclean all

.PHONY: all clean fclean re
