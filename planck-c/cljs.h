#include <JavaScriptCore/JavaScript.h>

extern JSGlobalContextRef ctx;

void cljs_acquire_eval_lock();

void cljs_release_eval_lock();

void set_int_handler();

void clear_int_handler();

bool should_keep_running();

JSValueRef
evaluate_source(char *type, char *source_value, bool expression, bool print_nil, char *set_ns,
                char *theme, bool block_until_ready, int session_id);

char *munge(char *s);

void bootstrap(char *out_path);

JSObjectRef cljs_get_function(char *namespace, char *name);

void cljs_run_main_in_ns(char *ns, size_t argc, char **argv);

char *cljs_get_current_ns();

char **cljs_get_completions(const char *buffer, int *num_completions);

extern bool cljs_engine_ready;

void cljs_engine_init();

void cljs_perror(const char* msg);

void cljs_print_message(const char* msg);

void cljs_set_print_sender(void (*sender)(const char *msg));

bool cljs_print_newline();

char *cljs_is_readable(char *expression);

int cljs_indent_space_count(char *text);

void cljs_highlight_coords_for_pos(int pos, const char *buf, size_t num_previous_lines,
                                   char **previous_lines, int *num_lines_up, int *highlight_pos);
