#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <JavaScriptCore/JavaScript.h>

#include "jsc_utils.h"

JSStringRef to_string(JSContextRef ctx, JSValueRef val) {
    if (JSValueIsUndefined(ctx, val)) {
        return JSStringCreateWithUTF8CString("undefined");
    } else if (JSValueIsNull(ctx, val)) {
        return JSStringCreateWithUTF8CString("null");
    } else {
        JSStringRef to_string_name = JSStringCreateWithUTF8CString("toString");
        JSObjectRef obj = JSValueToObject(ctx, val, NULL);
        JSValueRef to_string = JSObjectGetProperty(ctx, obj, to_string_name, NULL);
        JSObjectRef to_string_obj = JSValueToObject(ctx, to_string, NULL);
        JSValueRef obj_val = JSObjectCallAsFunction(ctx, to_string_obj, obj, 0, NULL, NULL);

        return JSValueToStringCopy(ctx, obj_val, NULL);
    }
}

void print_value(char *prefix, JSContextRef ctx, JSValueRef val) {
    if (val != NULL) {
        JSStringRef str = to_string(ctx, val);
        char *ex_str = value_to_c_string(ctx, JSValueMakeString(ctx, str));
        printf("%s%s\n", prefix, ex_str);
        free(ex_str);
    }
}

JSValueRef evaluate_script(JSContextRef ctx, char *script, char *source) {
    JSStringRef script_ref = JSStringCreateWithUTF8CString(script);
    JSStringRef source_ref = NULL;
    if (source != NULL) {
        source_ref = JSStringCreateWithUTF8CString(source);
    }

    JSValueRef ex = NULL;
    JSValueRef val = JSEvaluateScript(ctx, script_ref, NULL, source_ref, 0, &ex);
    JSStringRelease(script_ref);
    if (source != NULL) {
        JSStringRelease(source_ref);
    }

    // debug_print_value("evaluate_script", ctx, ex);

    return val;
}

char *value_to_c_string_ext(JSContextRef ctx, JSValueRef val, bool handle_non_string_values) {

    if (!handle_non_string_values && JSValueIsNull(ctx, val)) {
        return NULL;
    }

    if (!JSValueIsString(ctx, val)) {
        if (handle_non_string_values) {

            JSStringRef error_str = JSStringCreateWithUTF8CString("Error");
            JSValueRef error_prop = JSObjectGetProperty(ctx, JSContextGetGlobalObject(ctx), error_str, NULL);
            JSObjectRef error_constructor_obj = JSValueToObject(ctx, error_prop, NULL);

            if (JSValueIsInstanceOfConstructor(ctx, val, error_constructor_obj, NULL)) {
                JSObjectRef error_obj = JSValueToObject(ctx, val, NULL);
                JSStringRef message_str = JSStringCreateWithUTF8CString("message");
                JSValueRef message_prop = JSObjectGetProperty(ctx, error_obj, message_str, NULL);
                char* message = value_to_c_string(ctx, message_prop);
                JSStringRef stack_str = JSStringCreateWithUTF8CString("stack");
                JSValueRef stack_prop = JSObjectGetProperty(ctx, error_obj, stack_str, NULL);
                char* stack = value_to_c_string(ctx, stack_prop);
                char* result = malloc(sizeof(char) * (strlen(message) + strlen(stack) + 2));
                sprintf(result, "%s\n%s", message, stack);
                return result;
            } else {
                static JSObjectRef stringify_fn = NULL;

                if (!stringify_fn) {
                    JSStringRef json_str = JSStringCreateWithUTF8CString("JSON");
                    JSValueRef json_prop = JSObjectGetProperty(ctx, JSContextGetGlobalObject(ctx), json_str, NULL);
                    JSObjectRef json_obj = JSValueToObject(ctx, json_prop, NULL);
                    JSStringRelease(json_str);
                    JSStringRef stringify_str = JSStringCreateWithUTF8CString("stringify");
                    JSValueRef stringify_prop = JSObjectGetProperty(ctx, json_obj, stringify_str, NULL);
                    JSStringRelease(stringify_str);
                    stringify_fn = JSValueToObject(ctx, stringify_prop, NULL);
                    JSValueProtect(ctx, stringify_fn);
                }

                size_t num_arguments = 3;
                JSValueRef arguments[num_arguments];
                arguments[0] = val;
                arguments[1] = JSValueMakeNull(ctx);
                arguments[2] = c_string_to_value(ctx, " ");
                JSValueRef result = JSObjectCallAsFunction(ctx, stringify_fn, JSContextGetGlobalObject(ctx),
                                                           num_arguments, arguments, NULL);

                return value_to_c_string(ctx, result);
            }
        } else {
            return NULL;
        }
    }

    JSStringRef str_ref = JSValueToStringCopy(ctx, val, NULL);
    size_t len = JSStringGetMaximumUTF8CStringSize(str_ref);
    char *str = malloc(len * sizeof(char));
    JSStringGetUTF8CString(str_ref, str, len);
    JSStringRelease(str_ref);

    return str;
}

char *value_to_c_string(JSContextRef ctx, JSValueRef val) {
    return value_to_c_string_ext(ctx, val, false);
}

JSValueRef c_string_to_value(JSContextRef ctx, const char *s) {
    JSStringRef str = JSStringCreateWithUTF8CString(s);
    JSValueRef rv = JSValueMakeString(ctx, str);
    JSStringRelease(str);
    return rv;
}

int array_get_count(JSContextRef ctx, JSObjectRef arr) {
    JSStringRef pname = JSStringCreateWithUTF8CString("length");
    JSValueRef val = JSObjectGetProperty(ctx, arr, pname, NULL);
    JSStringRelease(pname);
    return (int) JSValueToNumber(ctx, val, NULL);
}
