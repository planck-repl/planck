#include <JavaScriptCore/JavaScript.h>

extern JSGlobalContextRef ctx;

void acquire_eval_lock();

void release_eval_lock();

void set_int_handler();

void clear_int_handler();

bool should_keep_running();

void evaluate_source(char *type, char *source_value, bool expression, bool print_nil, char *set_ns,
                           const char *theme, bool block_until_ready, int session_id);

char *munge(char *s);

void bootstrap(char *out_path);

int block_until_engine_ready();

const char *block_until_engine_ready_failed_msg;

JSObjectRef get_function(char *namespace, char *name);

void run_main_in_ns(char *ns, size_t argc, char **argv);

char *get_current_ns();

char **get_completions(const char *buffer, int *num_completions);

extern bool engine_ready;

void engine_init();

void engine_shutdown();

void engine_perror(const char *msg);

void engine_print_err_message(const char *msg, int err);

void engine_print(const char *msg);

void engine_println(const char *msg);

void set_print_sender(void (*sender)(const char *msg));

bool engine_print_newline();

char *is_readable(char *expression);

int indent_space_count(char *text);

void highlight_coords_for_pos(int pos, const char *buf, size_t num_previous_lines,
                              char **previous_lines, int *num_lines_up, int *highlight_pos);
