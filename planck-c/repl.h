#include "prepl.h"

typedef struct repl {
    char *current_ns;
    char *current_prompt;
    char *history_path;
    char *input;
    int indent_space_count;
    size_t num_previous_lines;
    prepl_t *prepl;
    char **previous_lines;
    int session_id;
} repl_t;

bool is_whitespace(char *s);

bool is_exit_command(char *input, bool is_socket_repl);

repl_t *make_repl();

int run_repl();
