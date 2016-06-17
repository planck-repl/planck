#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <JavaScriptCore/JavaScript.h>

#include "bundle.h"
#include "globals.h"
#include "io.h"
#include "jsc_utils.h"
#include "str.h"
#include "zip.h"

#define CONSOLE_LOG_BUF_SIZE 1000
char console_log_buf[CONSOLE_LOG_BUF_SIZE];

JSValueRef function_console_log(JSContextRef ctx, JSObjectRef function, JSObjectRef this_object,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	for (int i = 0; i < argc; i++) {
		if (i > 0) {
			fprintf(stdout, " ");
		}

		JSStringRef str = to_string(ctx, args[i]);
		JSStringGetUTF8CString(str, console_log_buf, CONSOLE_LOG_BUF_SIZE);
		fprintf(stdout, "%s", console_log_buf);
	}
	fprintf(stdout, "\n");

	return JSValueMakeUndefined(ctx);
}

JSValueRef function_console_error(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	for (int i = 0; i < argc; i++) {
		if (i > 0) {
			fprintf(stderr, " ");
		}

		JSStringRef str = to_string(ctx, args[i]);
		JSStringGetUTF8CString(str, console_log_buf, CONSOLE_LOG_BUF_SIZE);
		fprintf(stderr, "%s", console_log_buf);
	}
	fprintf(stderr, "\n");

	return JSValueMakeUndefined(ctx);
}

JSValueRef function_read_file(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	// TODO: implement fully

	if (argc == 1 && JSValueGetType(ctx, args[0]) == kJSTypeString) {
		char path[100];
		JSStringRef path_str = JSValueToStringCopy(ctx, args[0], NULL);
		assert(JSStringGetLength(path_str) < 100);
		JSStringGetUTF8CString(path_str, path, 100);
		JSStringRelease(path_str);

		// debug_print_value("read_file", ctx, args[0]);

		time_t last_modified = 0;
		char *contents = get_contents(path, &last_modified);
		if (contents != NULL) {
			JSStringRef contents_str = JSStringCreateWithUTF8CString(contents);
			free(contents);

			JSValueRef res[2];
			res[0] = JSValueMakeString(ctx, contents_str);
			res[1] = JSValueMakeNumber(ctx, last_modified);
			return JSObjectMakeArray(ctx, 2, res, NULL);
		}
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_load(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	// TODO: implement fully

	if (argc == 1 && JSValueGetType(ctx, args[0]) == kJSTypeString) {
		char path[100];
		JSStringRef path_str = JSValueToStringCopy(ctx, args[0], NULL);
		assert(JSStringGetLength(path_str) < 100);
		JSStringGetUTF8CString(path_str, path, 100);
		JSStringRelease(path_str);

		// debug_print_value("load", ctx, args[0]);

		time_t last_modified = 0;
		char *contents = NULL;

		bool developing = (config.num_src_paths == 1 &&
		                   strcmp(config.src_paths[0].type, "src") == 0 &&
		                   str_has_suffix(config.src_paths[0].path, "/planck-cljs/src/") == 0);

		if (!developing) {
			contents = bundle_get_contents(path);
			last_modified = 0;
		}

		// load from classpath
		if (contents == NULL) {
			for (int i = 0; i < config.num_src_paths; i++) {
				char *type = config.src_paths[i].type;
				char *location = config.src_paths[i].path;

				if (strcmp(type, "src") == 0) {
					char *full_path = str_concat(location, path);
					contents = get_contents(full_path, &last_modified);
					free(full_path);
				} else if (strcmp(type, "jar") == 0) {
					contents = get_contents_zip(location, path, &last_modified);
				}

				if (contents != NULL) {
					break;
				}
			}
		}

		// load from out/
		if (contents == NULL) {
			if (config.out_path != NULL) {
				char *full_path = str_concat(config.out_path, path);
				contents = get_contents(full_path, &last_modified);
				free(full_path);
			}
		}

		if (developing && contents == NULL) {
			contents = bundle_get_contents(path);
			last_modified = 0;
		}

		if (contents != NULL) {
			JSStringRef contents_str = JSStringCreateWithUTF8CString(contents);
			free(contents);

			JSValueRef res[2];
			res[0] = JSValueMakeString(ctx, contents_str);
			res[1] = JSValueMakeNumber(ctx, last_modified);
			return JSObjectMakeArray(ctx, 2, res, NULL);
		}
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_load_deps_cljs_files(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	int num_files = 0;
	char **deps_cljs_files = NULL;

	if (argc == 0) {
		for (int i = 0; i < config.num_src_paths; i++) {
			char *type = config.src_paths[i].type;
			char *location = config.src_paths[i].path;

			if (strcmp(type, "jar") == 0) {
				char *source = get_contents_zip(location, "deps.cljs", NULL);
				if (source != NULL) {
					num_files += 1;
					deps_cljs_files = realloc(deps_cljs_files, num_files * sizeof(char*));
					deps_cljs_files[num_files - 1] = source;
				}
			}
		}
	}

	JSValueRef files[num_files];
	for (int i = 0; i < num_files; i++) {
		JSStringRef file = JSStringCreateWithUTF8CString(deps_cljs_files[i]);
		files[i] = JSValueMakeString(ctx, file);
		free(deps_cljs_files[i]);
	}
	free(deps_cljs_files);

	return JSObjectMakeArray(ctx, num_files, files, NULL);
}

JSValueRef function_cache(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 4 &&
			JSValueGetType (ctx, args[0]) == kJSTypeString &&
			JSValueGetType (ctx, args[1]) == kJSTypeString &&
			(JSValueGetType (ctx, args[2]) == kJSTypeString
				|| JSValueGetType (ctx, args[2]) == kJSTypeNull) &&
			(JSValueGetType (ctx, args[3]) == kJSTypeString
				|| JSValueGetType (ctx, args[3]) == kJSTypeNull)) {
		// debug_print_value("cache", ctx, args[0]);

		char *cache_prefix = value_to_c_string(ctx, args[0]);
		char *source = value_to_c_string(ctx, args[1]);
		char *cache = value_to_c_string(ctx, args[2]);
		char *sourcemap = value_to_c_string(ctx, args[3]);

		char *suffix = NULL;
		int max_suffix_len = 20;
		int prefix_len = strlen(cache_prefix);
		char *path = malloc((prefix_len + max_suffix_len) * sizeof(char));
		memset(path, 0, prefix_len + max_suffix_len);

		suffix = ".js";
		strcpy(path, cache_prefix);
		strcat(path, suffix);
		write_contents(path, source);

		suffix = ".cache.json";
		strcpy(path, cache_prefix);
		strcat(path, suffix);
		write_contents(path, cache);

		suffix = ".js.map.json";
		strcpy(path, cache_prefix);
		strcat(path, suffix);
		write_contents(path, sourcemap);

		free(cache_prefix);
		free(source);
		free(cache);
		free(sourcemap);

		free(path);
	}

	return  JSValueMakeNull(ctx);
}

JSValueRef function_eval(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	JSValueRef val = NULL;

	if (argc == 2
		&& JSValueGetType(ctx, args[0]) == kJSTypeString
		&& JSValueGetType(ctx, args[1]) == kJSTypeString) {
		// debug_print_value("eval", ctx, args[0]);

		JSStringRef sourceRef = JSValueToStringCopy(ctx, args[0], NULL);
		JSStringRef pathRef = JSValueToStringCopy(ctx, args[1], NULL);

		JSEvaluateScript(ctx, sourceRef, NULL, pathRef, 0, &val);

		JSStringRelease(pathRef);
		JSStringRelease(sourceRef);
	}

	return val != NULL ? val : JSValueMakeNull(ctx);
}

JSValueRef function_get_term_size(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	// if (return_term_size)
	struct winsize w;
	ioctl(STDOUT_FILENO, TIOCGWINSZ, &w);
	JSValueRef  arguments[2];
	arguments[0] = JSValueMakeNumber(ctx, w.ws_row);
	arguments[1] = JSValueMakeNumber(ctx, w.ws_col);
	return JSObjectMakeArray(ctx, 2, arguments, NULL);
	// return JSValueMakeNull(ctx);
}

JSValueRef function_print_fn(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1 && JSValueIsString(ctx, args[0])) {
		char *str = value_to_c_string(ctx, args[0]);

		fprintf(stdout, "%s", str);
		fflush(stdout);

		free(str);
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_print_err_fn(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1 && JSValueIsString(ctx, args[0])) {
		char *str = value_to_c_string(ctx, args[0]);

		fprintf(stderr, "%s", str);
		fflush(stderr);

		free(str);
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_set_exit_value(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1 && JSValueGetType (ctx, args[0]) == kJSTypeNumber) {
		exit_value = JSValueToNumber(ctx, args[0], NULL);
	}
	return JSValueMakeNull(ctx);
}

JSValueRef function_raw_read_stdin(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	char buf[1024 + 1];

	int n = fread(buf, 1, config.is_tty ? 1 : 1024, stdin);
	if (n > 0) {
		buf[n] = '\0';
		return c_string_to_value(ctx, buf);
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_raw_write_stdout(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1 && JSValueGetType(ctx, args[0]) == kJSTypeString) {
		char *s = value_to_c_string(ctx, args[0]);
		fprintf(stdout, "%s", s);
		free(s);
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_raw_flush_stdout(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	fflush(stdout);

	return JSValueMakeNull(ctx);
}

JSValueRef function_raw_write_stderr(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1 && JSValueGetType(ctx, args[0]) == kJSTypeString) {
		char *s = value_to_c_string(ctx, args[0]);
		fprintf(stderr, "%s", s);
		free(s);
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_raw_flush_stderr(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	fflush(stderr);

	return JSValueMakeNull(ctx);
}

JSValueRef function_import_script(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1 && JSValueGetType(ctx, args[0]) == kJSTypeString) {
		JSStringRef path_str_ref = JSValueToStringCopy(ctx, args[0], NULL);
		assert(JSStringGetLength(path_str_ref) < 100);
		char tmp[100];
		tmp[0] = '\0';
		JSStringGetUTF8CString(path_str_ref, tmp, 100);
		JSStringRelease(path_str_ref);

		char *path = tmp;
		if (str_has_prefix(path, "goog/../") == 0) {
			path = path + 8;
		}

		char *source = NULL;
		if (config.out_path == NULL) {
			source = bundle_get_contents(path);
		} else {
			char *full_path = str_concat(config.out_path, path);
			source = get_contents(full_path, NULL);
			free(full_path);
		}

		if (source != NULL) {
			evaluate_script(ctx, source, path);
			free(source);
		}
	}

	return JSValueMakeUndefined(ctx);
}