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

char *value_to_c_string(JSContextRef ctx, JSValueRef val) {
	if (JSValueIsNull(ctx, val)) {
		return NULL;
	}

	if (!JSValueIsString(ctx, val)) {
#ifdef DEBUG
		fprintf(stderr, "WARN: not a string\n");
#endif
		return NULL;
	}

	JSStringRef str_ref = JSValueToStringCopy(ctx, val, NULL);
	size_t len = JSStringGetMaximumUTF8CStringSize(str_ref);
	char *str = malloc(len * sizeof(char));
	memset(str, 0, len);
	JSStringGetUTF8CString(str_ref, str, len);
	JSStringRelease(str_ref);

	return str;
}

JSValueRef c_string_to_value(JSContextRef ctx, const char *s) {
	JSStringRef str = JSStringCreateWithUTF8CString(s);
	return JSValueMakeString(ctx, str);
}

int array_get_count(JSContextRef ctx, JSObjectRef arr)
{
	JSStringRef pname = JSStringCreateWithUTF8CString("length");
	JSValueRef val = JSObjectGetProperty(ctx, arr, pname, NULL);
	JSStringRelease(pname);
	return (int)JSValueToNumber(ctx, val, NULL);
}
