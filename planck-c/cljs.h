#include <JavaScriptCore/JavaScript.h>

JSValueRef evaluate_source(JSContextRef ctx, char *type, char *source_value, bool expression, bool print_nil, char *set_ns, char *theme);
char *munge(char *s);

void bootstrap(JSContextRef ctx, char *out_path);
JSObjectRef get_function(JSContextRef ctx, char *namespace, char *name);

void run_main_in_ns(JSContextRef ctx, char *ns, int argc, char **argv);

char *get_current_ns();
