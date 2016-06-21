#include <JavaScriptCore/JavaScript.h>

JSValueRef evaluate_source(JSContextRef ctx, char *type, char *source_value, bool expression, bool print_nil, char *set_ns, char *theme, bool block_until_ready);
char *munge(char *s);

void bootstrap(JSContextRef ctx, char *out_path);
JSObjectRef get_function(JSContextRef ctx, char *namespace, char *name);

void run_main_in_ns(JSContextRef ctx, char *ns, int argc, char **argv);

char *get_current_ns();
char **get_completions(JSContextRef ctx, const char *buffer, int *num_completions);

extern bool cljs_engine_ready;
void cljs_engine_init(JSContextRef ctx);

void cljs_set_print_sender(JSContextRef ctx, void (*sender)(const char* msg));
bool cljs_print_newline(JSContextRef ctx);

char *cljs_is_readable(JSContextRef ctx, char *expression);
int cljs_indent_space_count(JSContextRef ctx, char *text);
void cljs_highlight_coords_for_pos(JSContextRef ctx, int pos, const char *buf, int num_previous_lines, char **previous_lines, int *num_lines_up, int *highlight_pos);
