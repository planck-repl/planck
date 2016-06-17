#include <ctype.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <unistd.h>

#include "linenoise.h"

#include "cljs.h"
#include "globals.h"
#include "str.h"

#define EXIT_SUCCESS_INTERNAL 0

pthread_mutex_t eval_lock = PTHREAD_MUTEX_INITIALIZER;

JSContextRef global_ctx;

char *current_ns = "cljs.user";
char *current_prompt = NULL;

char *history_path = NULL;

char *input = NULL;
int indent_space_count = 0;

int num_previous_lines = 0;
char **previous_lines = NULL;

int socket_repl_session_id = 0;

void empty_previous_lines() {
	for (int i = 0; i < num_previous_lines; i++) {
		free(previous_lines[i]);
	}
	free(previous_lines);
	num_previous_lines = 0;
	previous_lines = NULL;
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
			int len = strlen(current_ns) - 2;
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
	int n = getline(&line, &len, stdin);
	if (n > 0) {
		if (line[n-1] == '\n') {
			line[n-1] = '\0';
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
	int len = strlen(s);
	for (int i = 0; i < len; i++) {
		if (!isspace(s[i])) {
			return false;
		}
	}

	return true;
}

bool process_line(JSContextRef ctx, char *input_line) {
	// Accumulate input lines

	if (input == NULL) {
		input = input_line;
	} else {
		input = realloc(input, (strlen(input) + strlen(input_line) + 2) * sizeof(char));
		sprintf(input + strlen(input), "\n%s", input_line);
	}

	num_previous_lines += 1;
	previous_lines = realloc(previous_lines, num_previous_lines * sizeof(char*));
	previous_lines[num_previous_lines - 1] = strdup(input_line);

	// Check for explicit exit

	if (strcmp(input, ":cljs/quit") == 0 ||
			strcmp(input, "quit") == 0 ||
			strcmp(input, "exit") == 0) {
		if (socket_repl_session_id == 0) {
			exit_value = EXIT_SUCCESS_INTERNAL;
		}
		return true;
	}

	// Add input line to history

	if (history_path != NULL && !is_whitespace(input)) {
		linenoiseHistoryAdd(input_line);
		linenoiseHistorySave(history_path);
	}

	// Check if we now have readable forms
	// and if so, evaluate them

	bool done = false;
	char *balance_text = NULL;

	while (!done) {
		if ((balance_text = cljs_is_readable(ctx, input)) != NULL) {
			input[strlen(input) - strlen(balance_text)] = '\0';

			if (!is_whitespace(input)) { // Guard against empty string being read
				pthread_mutex_lock(&eval_lock);

				return_termsize = !config.dumb_terminal;
				// TODO: set exit value
				evaluate_source(ctx, "text", input, true, true, current_ns, config.theme, true);
				return_termsize = false;

				pthread_mutex_unlock(&eval_lock);

				if (exit_value != 0) {
					free(input);
					return true;
				}
			} else {
				printf("\n");
			}

			// Now that we've evaluated the input, reset for next round
			free(input);
			input = balance_text;

			empty_previous_lines();

			// Fetch the current namespace and use it to set the prompt
			free(current_ns);
			free(current_prompt);

			current_ns = get_current_ns(ctx);
			current_prompt = form_prompt(current_ns, false);

			if (is_whitespace(balance_text)) {
				done = true;
				free(input);
				input = NULL;
			}
		} else {
			// Prepare for reading non-1st of input with secondary prompt
			if (history_path != NULL) {
				indent_space_count = cljs_indent_space_count(ctx, input);
			}

			free(current_prompt);
			current_prompt = form_prompt(current_ns, true);
			done = true;
		}
	}

	return false;
}

void run_cmdline_loop(JSContextRef ctx) {
	while (true) {
		char *input_line = NULL;

		if (config.dumb_terminal) {
			display_prompt(current_prompt);
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

			char *line = linenoise(current_prompt, "\x1b[36m", indent_space_count);

			// Reset printing handler back
			if (cljs_engine_ready) {
				cljs_set_print_sender(ctx, NULL);
			}

			indent_space_count = 0;
			if (line == NULL) {
				if (errno == EAGAIN) { // Ctrl-C
					errno = 0;
					input = NULL;
					empty_previous_lines();
					current_prompt = form_prompt(current_ns, false);
					printf("\n");
					continue;
				} else { // Ctrl-D
					exit_value = EXIT_SUCCESS_INTERNAL;
					break;
				}
			}

			input_line = line;
		}

		bool break_out = process_line(ctx, input_line);
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

struct hl_restore {
	bool should_restore;
	int num_lines_up;
	int relative_horiz;

};
struct hl_restore hl_restore;

void *do_highlight_restore(void *data) {
	if (data != NULL) {
		int *timeout = data;
		struct timespec t;
		t.tv_sec = 0;
		t.tv_nsec = *timeout;
#ifdef CLOCK_REALTIME
		clock_nanosleep(CLOCK_REALTIME, 0, &t, NULL);
#else
		nanosleep(&t, NULL);
#endif
	}

	if (hl_restore.num_lines_up != 0) {
		fprintf(stdout,"\x1b[%dB", hl_restore.num_lines_up);
	}

	if (hl_restore.relative_horiz < 0) {
		fprintf(stdout,"\x1b[%dC", -hl_restore.relative_horiz);
	} else if (hl_restore.relative_horiz > 0){
		fprintf(stdout,"\x1b[%dD", hl_restore.relative_horiz);
	}

	fflush(stdout);

	hl_restore.should_restore = false;
	hl_restore.num_lines_up = 0;
	hl_restore.relative_horiz = 0;

	return NULL;
}

void highlight(const char *buf, int pos) {
	char current = buf[pos];

	if (current == ']' || current == '}' || current == ')') {
		int num_lines_up = -1;
		int highlight_pos = 0;
		char *buf_copy = malloc((pos + 1) * sizeof(char));
		strncpy(buf_copy, buf, pos);
		buf_copy[pos + 1] = '\0';
		cljs_highlight_coords_for_pos(global_ctx, pos, (char*)buf, num_previous_lines, previous_lines, &num_lines_up, &highlight_pos);
		free(buf_copy);

		int current_pos = pos + 1;

		if (num_lines_up != -1) {
			int relative_horiz = highlight_pos - current_pos;

			if (num_lines_up != 0) {
				fprintf(stdout,"\x1b[%dA", num_lines_up);
			}

			if (relative_horiz < 0) {
				fprintf(stdout,"\x1b[%dD", -relative_horiz);
			} else if (relative_horiz > 0){
				fprintf(stdout,"\x1b[%dC", relative_horiz);
			}

			fflush(stdout);

			// struct hl_restore *hl_restore = malloc(sizeof(struct hl_restore));
			hl_restore.should_restore = true;
			hl_restore.num_lines_up = num_lines_up;
			hl_restore.relative_horiz = relative_horiz;

			int timeout = 500 * 1000 * 1000;
			pthread_attr_t attr;
			pthread_attr_init(&attr);
			pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
			pthread_t thread;
			pthread_create(&thread, &attr, do_highlight_restore, (void*)&timeout);
		}
	}
}

void highlight_cancel() {
	if (hl_restore.should_restore) {
		do_highlight_restore(NULL);
	}
}

int run_repl(JSContextRef ctx) {
	global_ctx = ctx;

	current_ns = strdup("cljs.user");
	current_prompt = form_prompt(current_ns, false);

	// Per-type initialization

	if (!config.dumb_terminal) {
		char *home = getenv("HOME");
		if (home != NULL) {
			char history_name[] = ".planck_history";
			int len = strlen(home) + strlen(history_name) + 2;
			history_path = malloc(len * sizeof(char));
			snprintf(history_path, len, "%s/%s", home, history_name);

			linenoiseHistoryLoad(history_path);

			// TODO: load keymap
		}

		linenoiseSetMultiLine(1);
		linenoiseSetCompletionCallback(completion);
		linenoiseSetHighlightCallback(highlight);
		linenoiseSetHighlightCancelCallback(highlight_cancel);
	}

	run_cmdline_loop(ctx);

	return exit_value;
}