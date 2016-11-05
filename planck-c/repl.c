#include <ctype.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <unistd.h>
#include<sys/socket.h>
#include<arpa/inet.h>

#include "linenoise.h"

#include "cljs.h"
#include "globals.h"
#include "str.h"
#include "theme.h"
#include "timers.h"

pthread_mutex_t eval_lock = PTHREAD_MUTEX_INITIALIZER;

JSContextRef global_ctx;

struct repl {
    char *current_ns;
    char *current_prompt;
    char *history_path;
    char *input;
    int indent_space_count;
    size_t num_previous_lines;
    char **previous_lines;
    int session_id;
};

typedef struct repl repl_t;

repl_t* make_repl() {
    repl_t* repl = malloc(sizeof(repl_t));
    repl->current_ns = strdup("cljs.user");
    repl->current_prompt = NULL;
    repl->history_path = NULL;
    repl->input = NULL;
    repl->indent_space_count = 0;
    repl->num_previous_lines = 0;
    repl->previous_lines = NULL;
    repl->session_id = 0;
    return repl;
}

void empty_previous_lines(repl_t* repl) {
    for (int i = 0; i < repl->num_previous_lines; i++) {
        free(repl->previous_lines[i]);
    }
    free(repl->previous_lines);
    repl->num_previous_lines = 0;
    repl->previous_lines = NULL;
}

char *form_prompt(char *current_ns, bool is_secondary) {
    char *prompt = NULL;
    if (!is_secondary) {
        if (strlen(current_ns) == 1 && !config.dumb_terminal) {
            prompt = malloc(6 * sizeof(char));
            sprintf(prompt, " %s=> ", current_ns);
        } else {
            prompt = str_concat(current_ns, "=> ");
        }
    } else {
        if (!config.dumb_terminal) {
            size_t len = strlen(current_ns) - 2;
            prompt = malloc((len + 6) * sizeof(char));
            memset(prompt, ' ', len);
            sprintf(prompt + len, "#_=> ");
        }
    }

    return prompt;
}

char *get_input() {
    char *line = NULL;
    size_t len = 0;
    ssize_t n = getline(&line, &len, stdin);
    if (n > 0) {
        if (line[n - 1] == '\n') {
            line[n - 1] = '\0';
        }
    }
    return line;
}

void display_prompt(char *prompt) {
    if (prompt != NULL) {
        fprintf(stdout, "%s", prompt);
        fflush(stdout);
    }
}

bool is_whitespace(char *s) {
    size_t len = strlen(s);
    for (int i = 0; i < len; i++) {
        if (!isspace(s[i])) {
            return false;
        }
    }

    return true;
}

bool process_line(repl_t* repl, JSContextRef ctx, char *input_line) {

    // Accumulate input lines

    if (repl->input == NULL) {
        repl->input = input_line;
    } else {
        repl->input = realloc(repl->input, (strlen(repl->input) + strlen(input_line) + 2) * sizeof(char));
        sprintf(repl->input + strlen(repl->input), "\n%s", input_line);
    }

    repl->num_previous_lines += 1;
    repl->previous_lines = realloc(repl->previous_lines, repl->num_previous_lines * sizeof(char *));
    repl->previous_lines[repl->num_previous_lines - 1] = strdup(input_line);

    // Check for explicit exit

    if (strcmp(repl->input, ":cljs/quit") == 0 ||
        strcmp(repl->input, "quit") == 0 ||
        strcmp(repl->input, "exit") == 0) {
        if (repl->session_id == 0) {
            exit_value = EXIT_SUCCESS_INTERNAL;
        }
        return true;
    }

    // Add input line to history

    if (repl->history_path != NULL && !is_whitespace(repl->input)) {
        linenoiseHistoryAdd(input_line);
        linenoiseHistorySave(repl->history_path);
    }

    // Check if we now have readable forms
    // and if so, evaluate them

    bool done = false;
    char *balance_text = NULL;

    while (!done) {
        if ((balance_text = cljs_is_readable(ctx, repl->input)) != NULL) {
            repl->input[strlen(repl->input) - strlen(balance_text)] = '\0';

            if (!is_whitespace(repl->input)) { // Guard against empty string being read
                pthread_mutex_lock(&eval_lock);

                return_termsize = !config.dumb_terminal;

                if (repl->session_id == 0) {
                    set_int_handler();
                }

                // TODO: set exit value

                char* theme = repl->session_id == 0 ? config.theme : "dumb";

                evaluate_source(ctx, "text", repl->input, true, true, repl->current_ns, theme, true,
                                repl->session_id);

                if (repl->session_id == 0) {
                    clear_int_handler();
                }

                return_termsize = false;

                pthread_mutex_unlock(&eval_lock);

                if (exit_value != 0) {
                    free(repl->input);
                    return true;
                }
            } else {
                printf("\n");
            }

            // Now that we've evaluated the input, reset for next round
            free(repl->input);
            repl->input = balance_text;

            empty_previous_lines(repl);

            // Fetch the current namespace and use it to set the prompt
            free(repl->current_ns);
            free(repl->current_prompt);

            repl->current_ns = get_current_ns(ctx);
            repl->current_prompt = form_prompt(repl->current_ns, false);

            if (is_whitespace(balance_text)) {
                done = true;
                free(repl->input);
                repl->input = NULL;
            }
        } else {
            // Prepare for reading non-1st of input with secondary prompt
            if (repl->history_path != NULL) {
                repl->indent_space_count = cljs_indent_space_count(ctx, repl->input);
            }

            free(repl->current_prompt);
            repl->current_prompt = form_prompt(repl->current_ns, true);
            done = true;
        }
    }

    return false;
}

void run_cmdline_loop(repl_t* repl, JSContextRef ctx) {
    while (true) {
        char *input_line = NULL;

        if (config.dumb_terminal) {
            display_prompt(repl->current_prompt);
            input_line = get_input();
            if (input_line == NULL) { // Ctrl-D pressed
                printf("\n");
                break;
            }
        } else {
            // Handle prints while processing linenoise input
            if (cljs_engine_ready) {
                cljs_set_print_sender(ctx, &linenoisePrintNow);
            }

            // If *print-newline* is off, we need to emit a newline now, otherwise
            // the linenoise prompt and line editing will overwrite any printed
            // output on the current line.
            if (cljs_engine_ready && !cljs_print_newline(ctx)) {
                fprintf(stdout, "\n");
            }

            char *line = linenoise(repl->current_prompt, prompt_ansi_code_for_theme(config.theme), repl->indent_space_count);

            // Reset printing handler back
            if (cljs_engine_ready) {
                cljs_set_print_sender(ctx, NULL);
            }

            repl->indent_space_count = 0;
            if (line == NULL) {
                if (errno == EAGAIN) { // Ctrl-C
                    errno = 0;
                    repl->input = NULL;
                    empty_previous_lines(repl);
                    repl->current_prompt = form_prompt(repl->current_ns, false);
                    printf("\n");
                    continue;
                } else { // Ctrl-D
                    exit_value = EXIT_SUCCESS_INTERNAL;
                    break;
                }
            }

            input_line = line;
        }

        bool break_out = process_line(repl, ctx, input_line);
        if (break_out) {
            break;
        }
    }
}

void completion(const char *buf, linenoiseCompletions *lc) {
    int num_completions = 0;
    char **completions = get_completions(global_ctx, buf, &num_completions);
    for (int i = 0; i < num_completions; i++) {
        linenoiseAddCompletion(lc, completions[i]);
        free(completions[i]);
    }
    free(completions);
}

pthread_mutex_t highlight_restore_sequence_mutex = PTHREAD_MUTEX_INITIALIZER;
int highlight_restore_sequence = 0;

struct hl_restore {
    int id;
    int num_lines_up;
    int relative_horiz;
};

struct hl_restore hl_restore = {0, 0, 0};

void do_highlight_restore(void* data) {

    struct hl_restore *hl_restore = data;

    int highlight_restore_sequence_value;
    pthread_mutex_lock(&highlight_restore_sequence_mutex);
    highlight_restore_sequence_value = highlight_restore_sequence;
    pthread_mutex_unlock(&highlight_restore_sequence_mutex);

    if (hl_restore->id == highlight_restore_sequence_value) {

        pthread_mutex_lock(&highlight_restore_sequence_mutex);
        ++highlight_restore_sequence;
        pthread_mutex_unlock(&highlight_restore_sequence_mutex);

        if (hl_restore->num_lines_up != 0) {
            fprintf(stdout, "\x1b[%dB", hl_restore->num_lines_up);
        }

        if (hl_restore->relative_horiz < 0) {
            fprintf(stdout, "\x1b[%dC", -hl_restore->relative_horiz);
        } else if (hl_restore->relative_horiz > 0) {
            fprintf(stdout, "\x0b[%dD", hl_restore->relative_horiz);
        }

        fflush(stdout);

    }

    free(hl_restore);
}

// Used when using linenoise
repl_t* s_repl;

void highlight(const char *buf, int pos) {
    char current = buf[pos];

    if (current == ']' || current == '}' || current == ')') {
        int num_lines_up = -1;
        int highlight_pos = 0;
        cljs_highlight_coords_for_pos(global_ctx, pos, buf, s_repl->num_previous_lines, s_repl->previous_lines, &num_lines_up,
                                      &highlight_pos);

        int current_pos = pos + 1;

        if (num_lines_up != -1) {
            int relative_horiz = highlight_pos - current_pos;

            if (num_lines_up != 0) {
                fprintf(stdout, "\x1b[%dA", num_lines_up);
            }

            if (relative_horiz < 0) {
                fprintf(stdout, "\x1b[%dD", -relative_horiz);
            } else if (relative_horiz > 0) {
                fprintf(stdout, "\x1b[%dC", relative_horiz);
            }

            fflush(stdout);

            struct hl_restore *hl_restore_local = malloc(sizeof(struct hl_restore));
            pthread_mutex_lock(&highlight_restore_sequence_mutex);
            hl_restore_local->id = ++highlight_restore_sequence;
            pthread_mutex_unlock(&highlight_restore_sequence_mutex);
            hl_restore_local->num_lines_up = num_lines_up;
            hl_restore_local->relative_horiz = relative_horiz;

            hl_restore = *hl_restore_local;

            start_timer(500, do_highlight_restore, (void *) hl_restore_local);
        }
    }
}

void highlight_cancel() {
    if (hl_restore.id != 0) {
        struct hl_restore *hl_restore_tmp = malloc(sizeof(struct hl_restore));
        *hl_restore_tmp = hl_restore;
        do_highlight_restore(hl_restore_tmp);
    }
}

int sock_to_write_to = 0;

void socket_sender(const char *text) {
    if (sock_to_write_to) {
        write(sock_to_write_to, text, strlen(text));
    }
}

static int session_id_counter = 0;

void *connection_handler(void *socket_desc) {
    repl_t* repl = make_repl();
    repl->current_prompt = form_prompt(repl->current_ns, false);

    repl->session_id = ++session_id_counter;

    int sock = *(int *) socket_desc;
    ssize_t read_size;
    char client_message[4096];

    write(sock, repl->current_prompt, strlen(repl->current_prompt));

    while ((read_size = recv(sock, client_message, 4095, 0)) > 0) {
        sock_to_write_to = sock;
        cljs_set_print_sender(global_ctx, &socket_sender);

        client_message[read_size] = '\0';
        process_line(repl, global_ctx, strdup(client_message));

        cljs_set_print_sender(global_ctx, NULL);
        sock_to_write_to = 0;

        write(sock, repl->current_prompt, strlen(repl->current_prompt));
    }

    free(socket_desc);

    return NULL;
}

void *accept_connections(void *data) {

    int socket_desc, new_socket, c, *new_sock;
    struct sockaddr_in server, client;

    socket_desc = socket(AF_INET, SOCK_STREAM, 0);
    if (socket_desc == -1) {
        perror("Could not create listen socket");
        return NULL;
    }

    server.sin_family = AF_INET;
    server.sin_addr.s_addr = INADDR_ANY;
    server.sin_port = htons(config.socket_repl_port);

    if (bind(socket_desc, (struct sockaddr *) &server, sizeof(server)) < 0) {
        perror("Socket bind failed");
        return NULL;
    }

    listen(socket_desc, 3);

    if (!config.quiet) {
        fprintf(stdout, "Planck socket REPL listening at %s:%d.\n", config.socket_repl_host, config.socket_repl_port);
    }

    c = sizeof(struct sockaddr_in);
    while ((new_socket = accept(socket_desc, (struct sockaddr *) &client, (socklen_t *) &c))) {

        pthread_t handler_thread;
        new_sock = malloc(1);
        *new_sock = new_socket;

        if (pthread_create(&handler_thread, NULL, connection_handler, (void *) new_sock) < 0) {
            perror("could not create thread");
            return NULL;
        }
    }

    if (new_socket < 0) {
        perror("accept failed");
        return NULL;
    }

    return NULL;
}

int run_repl(JSContextRef ctx) {
    repl_t* repl = make_repl();
    s_repl = repl;

    global_ctx = ctx;

    repl->current_ns = strdup("cljs.user");
    repl->current_prompt = form_prompt(repl->current_ns, false);

    // Per-type initialization

    if (!config.dumb_terminal) {
        char *home = getenv("HOME");
        if (home != NULL) {
            char history_name[] = ".planck_history";
            size_t len = strlen(home) + strlen(history_name) + 2;
            repl->history_path = malloc(len * sizeof(char));
            snprintf(repl->history_path, len, "%s/%s", home, history_name);

            linenoiseHistoryLoad(repl->history_path);

            // TODO: load keymap
        }

        linenoiseSetMultiLine(1);
        linenoiseSetCompletionCallback(completion);
        linenoiseSetHighlightCallback(highlight);
        linenoiseSetHighlightCancelCallback(highlight_cancel);
    }

    if (config.socket_repl_port) {
        pthread_t thread;
        pthread_create(&thread, NULL, accept_connections, NULL);
    }

    run_cmdline_loop(repl, ctx);

    return exit_value;
}
