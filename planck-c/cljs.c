#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <JavaScriptCore/JavaScript.h>

#include "bundle.h"
#include "io.h"
#include "jsc_utils.h"
#include "str.h"

char *munge(char *s) {
	int len = strlen(s);
	int new_len = 0;
	for (int i = 0; i < len; i++) {
		switch (s[i]) {
		case '!':
			new_len += 6; // _BANG_
			break;
		case '?':
			new_len += 7; // _QMARK_
			break;
		default:
			new_len += 1;
		}
	}

	char *ms = malloc((new_len+1) * sizeof(char));
	int j = 0;
	for (int i = 0; i < len; i++) {
		switch (s[i]) {
		case '-':
			ms[j++] = '_';
			break;
		case '!':
			ms[j++] = '_';
			ms[j++] = 'B';
			ms[j++] = 'A';
			ms[j++] = 'N';
			ms[j++] = 'G';
			ms[j++] = '_';
			break;
		case '?':
			ms[j++] = '_';
			ms[j++] = 'Q';
			ms[j++] = 'M';
			ms[j++] = 'A';
			ms[j++] = 'R';
			ms[j++] = 'K';
			ms[j++] = '_';
			break;

		default:
			ms[j++] = s[i];
		}
	}
	ms[new_len] = '\0';

	return ms;
}

JSValueRef get_value_on_object(JSContextRef ctx, JSObjectRef obj, char *name) {
	JSStringRef name_str = JSStringCreateWithUTF8CString(name);
	JSValueRef val = JSObjectGetProperty(ctx, obj, name_str, NULL);
	JSStringRelease(name_str);
	return val;
}

JSValueRef get_value(JSContextRef ctx, char *namespace, char *name) {
	JSValueRef ns_val = NULL;

	// printf("get_value: '%s'\n", namespace);
	int len = strlen(namespace) + 1;
	char *ns_tmp = malloc(len * sizeof(char));
	strncpy(ns_tmp, namespace, len);
	char *ns_part = strtok(ns_tmp, ".");
	ns_tmp = NULL;
	while (ns_part != NULL) {
		char *munged_ns_part = munge(ns_part);
		if (ns_val) {
			ns_val = get_value_on_object(ctx, JSValueToObject(ctx, ns_val, NULL), munged_ns_part);
		} else {
			ns_val = get_value_on_object(ctx, JSContextGetGlobalObject(ctx), munged_ns_part);
		}
		free(munged_ns_part); // TODO: Use a fixed buffer for this?  (Which would restrict namespace part length...)

		ns_part = strtok(NULL, ".");
	}
	//free(ns_tmp);

	char *munged_name = munge(name);
	JSValueRef val = get_value_on_object(ctx, JSValueToObject(ctx, ns_val, NULL), munged_name);
	free(munged_name);
	return val;
}

JSObjectRef get_function(JSContextRef ctx, char *namespace, char *name) {
	JSValueRef val = get_value(ctx, namespace, name);
	assert(!JSValueIsUndefined(ctx, val));
	return JSValueToObject(ctx, val, NULL);
}

JSValueRef evaluate_source(JSContextRef ctx, char *type, char *source, bool expression, bool print_nil, char *set_ns, char *theme) {
	JSValueRef args[6];
	int num_args = 6;

	{
		JSValueRef source_args[2];
		JSStringRef type_str = JSStringCreateWithUTF8CString(type);
		source_args[0] = JSValueMakeString(ctx, type_str);
		JSStringRef source_str = JSStringCreateWithUTF8CString(source);
		source_args[1] = JSValueMakeString(ctx, source_str);
		args[0] = JSObjectMakeArray(ctx, 2, source_args, NULL);
	}

	args[1] = JSValueMakeBoolean(ctx, expression);
	args[2] = JSValueMakeBoolean(ctx, print_nil);
	JSValueRef set_ns_val = NULL;
	if (set_ns != NULL) {
		JSStringRef set_ns_str = JSStringCreateWithUTF8CString(set_ns);
		set_ns_val = JSValueMakeString(ctx, set_ns_str);
	}
	args[3] = set_ns_val;
	JSStringRef theme_str = JSStringCreateWithUTF8CString(theme);
	args[4] = JSValueMakeString(ctx, theme_str);
	args[5] = JSValueMakeNumber(ctx, 0);

	JSObjectRef execute_fn = get_function(ctx, "planck.repl", "execute");
	JSObjectRef global_obj = JSContextGetGlobalObject(ctx);
	JSValueRef ex = NULL;
	JSValueRef val = JSObjectCallAsFunction(ctx, execute_fn, global_obj, num_args, args, &ex);

	// debug_print_value("planck.repl/execute", ctx, ex);

	return ex != NULL ? ex : val;
}

void bootstrap(JSContextRef ctx, char *out_path) {
	char *deps_file_path = "main.js";
	char *goog_base_path = "goog/base.js";
	if (out_path != NULL) {
		deps_file_path = str_concat(out_path, deps_file_path);
		goog_base_path = str_concat(out_path, goog_base_path);
	}

	char source[] = "<bootstrap>";

	// Setup CLOSURE_IMPORT_SCRIPT
	evaluate_script(ctx, "CLOSURE_IMPORT_SCRIPT = function(src) { AMBLY_IMPORT_SCRIPT('goog/' + src); return true; }", source);

	// Load goog base
	char *base_script_str = NULL;
	if (out_path) {
		base_script_str = get_contents(goog_base_path, NULL);
		free(goog_base_path);
	} else {
		base_script_str = bundle_get_contents(goog_base_path);
	}
	if (base_script_str == NULL) {
		fprintf(stderr, "The goog base JavaScript text could not be loaded\n");
		exit(1);
	}
	evaluate_script(ctx, base_script_str, "<bootstrap:base>");
	free(base_script_str);

	// Load the deps file
	char *deps_script_str = NULL;
	if (out_path) {
		deps_script_str = get_contents(deps_file_path, NULL);
		free(deps_file_path);
	} else {
		deps_script_str = bundle_get_contents(deps_file_path);
	}
	if (deps_script_str == NULL) {
		fprintf(stderr, "The deps JavaScript text could not be loaded\n");
		exit(1);
	}
	evaluate_script(ctx, deps_script_str, "<bootstrap:deps>");
	free(deps_script_str);

	evaluate_script(ctx, "goog.isProvided_ = function(x) { return false; };", source);

	evaluate_script(ctx, "goog.require = function (name) { return CLOSURE_IMPORT_SCRIPT(goog.dependencies_.nameToPath[name]); };", source);

	evaluate_script(ctx, "goog.require('cljs.core');", source);

	// redef goog.require to track loaded libs
	evaluate_script(ctx, "cljs.core._STAR_loaded_libs_STAR_ = cljs.core.into.call(null, cljs.core.PersistentHashSet.EMPTY, [\"cljs.core\"]);\n"
			"goog.require = function (name, reload) {\n"
			"    if(!cljs.core.contains_QMARK_(cljs.core._STAR_loaded_libs_STAR_, name) || reload) {\n"
			"        var AMBLY_TMP = cljs.core.PersistentHashSet.EMPTY;\n"
			"        if (cljs.core._STAR_loaded_libs_STAR_) {\n"
			"            AMBLY_TMP = cljs.core._STAR_loaded_libs_STAR_;\n"
			"        }\n"
			"        cljs.core._STAR_loaded_libs_STAR_ = cljs.core.into.call(null, AMBLY_TMP, [name]);\n"
			"        CLOSURE_IMPORT_SCRIPT(goog.dependencies_.nameToPath[name]);\n"
			"    }\n"
			"};", source);
}

void run_main_in_ns(JSContextRef ctx, char *ns, int argc, char **argv) {
	int num_arguments = argc + 1;
	JSValueRef arguments[num_arguments];
	arguments[0] = c_string_to_value(ctx, ns);
	for (int i=1; i<num_arguments; i++) {
		arguments[i] = c_string_to_value(ctx, argv[i-1]);
	}

	JSObjectRef global_obj = JSContextGetGlobalObject(ctx);
	JSObjectRef run_main_fn = get_function(ctx, "planck.repl", "run-main");
	JSObjectCallAsFunction(ctx, run_main_fn, global_obj, num_arguments, arguments, NULL);
}

char *get_current_ns(JSContextRef ctx) {
	int num_arguments = 0;
	JSValueRef arguments[num_arguments];
	JSObjectRef get_current_ns_fn = get_function(ctx, "planck.repl", "get-current-ns");
	JSValueRef result = JSObjectCallAsFunction(ctx, get_current_ns_fn, JSContextGetGlobalObject(ctx), num_arguments, arguments, NULL);
	return value_to_c_string(ctx, result);
}

char **get_completions(JSContextRef ctx, const char *buffer, int *num_completions) {
	int num_arguments = 1;
	JSValueRef arguments[num_arguments];
	arguments[0] = c_string_to_value(ctx, (char *)buffer);
	JSObjectRef completions_fn = get_function(ctx, "planck.repl", "get-completions");
	JSValueRef result = JSObjectCallAsFunction(ctx, completions_fn, JSContextGetGlobalObject(ctx), num_arguments, arguments, NULL);

	assert(JSValueIsObject(ctx, result));
	JSObjectRef array = JSValueToObject(ctx, result, NULL);
	JSStringRef length_prop = JSStringCreateWithUTF8CString("length");
	JSValueRef array_len = JSObjectGetProperty(ctx, array, length_prop, NULL);
	JSStringRelease(length_prop);
	assert(JSValueIsNumber(ctx, array_len));
	int n = (int)JSValueToNumber(ctx, array_len, NULL);

	char **completions = malloc(n * sizeof(char*));

	for (int i = 0; i < n; i++) {
		JSValueRef v = JSObjectGetPropertyAtIndex(ctx, array, i, NULL);
		completions[i] = value_to_c_string(ctx, v);
	}

	*num_completions = n;
	return completions;
}