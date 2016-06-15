#include <JavaScriptCore/JavaScript.h>

JSStringRef to_string(JSContextRef ctx, JSValueRef val);
JSValueRef evaluate_script(JSContextRef ctx, char *script, char *source);

char *value_to_c_string(JSContextRef ctx, JSValueRef val);

JSValueRef c_string_to_value(JSContextRef ctx, char *s);