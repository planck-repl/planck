#include <JavaScriptCore/JavaScript.h>

JSStringRef to_string(JSContextRef ctx, JSValueRef val);

#ifdef DEBUG
#define debug_print_value(prefix, ctx, val)	print_value(prefix ": ", ctx, val)
#else
#define debug_print_value(prefix, ctx, val)
#endif

void print_value(char *prefix, JSContextRef ctx, JSValueRef val);

JSValueRef evaluate_script(JSContextRef ctx, char *script, char *source);

char *value_to_c_string(JSContextRef ctx, JSValueRef val);

JSValueRef c_string_to_value(JSContextRef ctx, const char *s);

int array_get_count(JSContextRef ctx, JSObjectRef arr);

#define array_get_value_at_index(ctx, array, i) JSObjectGetPropertyAtIndex(ctx, array, i, NULL)
