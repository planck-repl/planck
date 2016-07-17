#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <pwd.h>
#include <grp.h>
#include <dirent.h>

#include <JavaScriptCore/JavaScript.h>

#include "bundle.h"
#include "globals.h"
#include "io.h"
#include "jsc_utils.h"
#include "str.h"
#include "zip.h"
#include "file.h"

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
	size_t num_files = 0;
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
		size_t prefix_len = strlen(cache_prefix);
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
	if (return_termsize) {
		struct winsize w;
		ioctl(STDOUT_FILENO, TIOCGWINSZ, &w);
		JSValueRef  arguments[2];
		arguments[0] = JSValueMakeNumber(ctx, w.ws_row);
		arguments[1] = JSValueMakeNumber(ctx, w.ws_col);
		return JSObjectMakeArray(ctx, 2, arguments, NULL);
	} else {
		return JSValueMakeNull(ctx);
	}
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
		exit_value = (int)JSValueToNumber(ctx, args[0], NULL);
	}
	return JSValueMakeNull(ctx);
}

JSValueRef function_raw_read_stdin(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
	char buf[1024 + 1];

	size_t n = fread(buf, 1, config.is_tty ? 1 : 1024, stdin);
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

uint64_t descriptor_str_to_int(const char* s) {
	return (uint64_t)atoll(s);
}

char * descriptor_int_to_str(uint64_t i) {
	char* rv = malloc(21);
	sprintf(rv, "%llu", (unsigned long long)i);
	return rv;
}

JSValueRef function_file_reader_open(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									 size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 2
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *path = value_to_c_string(ctx, args[0]);
		char *encoding = value_to_c_string(ctx, args[1]);

		uint64_t descriptor = ufile_open_read(path, encoding);

		free(path);
		free(encoding);

		char* descriptor_str = descriptor_int_to_str(descriptor);
		JSValueRef rv =  c_string_to_value(ctx, descriptor_str);
		free(descriptor_str);

		return rv;
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_file_reader_read(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									 size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *descriptor = value_to_c_string(ctx, args[0]);

		JSStringRef result = ufile_read(descriptor_str_to_int(descriptor));

		free(descriptor);

		JSValueRef arguments[2];
		if (result != NULL) {
			arguments[0] = JSValueMakeString(ctx, result);
			JSStringRelease(result);
		} else {
			arguments[0] =  JSValueMakeNull(ctx);
		}
		arguments[1] = JSValueMakeNull(ctx);
		return JSObjectMakeArray(ctx, 2, arguments, NULL);
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_file_reader_close(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									 size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *descriptor = value_to_c_string(ctx, args[0]);
		ufile_close(descriptor_str_to_int(descriptor));
		free(descriptor);
	}
	return JSValueMakeNull(ctx);
}

JSValueRef function_file_writer_open(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									 size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 3
		&& JSValueGetType(ctx, args[0]) == kJSTypeString
		&& JSValueGetType(ctx, args[1]) == kJSTypeBoolean) {

		char *path = value_to_c_string(ctx, args[0]);
		bool append = JSValueToBoolean(ctx, args[1]);
		char *encoding = value_to_c_string(ctx, args[2]);

		uint64_t descriptor =  ufile_open_write(path, append, encoding);

		free(path);
		free(encoding);

		char* descriptor_str = descriptor_int_to_str(descriptor);
		JSValueRef rv =  c_string_to_value(ctx, descriptor_str);
		free(descriptor_str);

		return rv;
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_file_writer_write(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									 size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 2
		&& JSValueGetType(ctx, args[0]) == kJSTypeString
		&& JSValueGetType(ctx, args[1]) == kJSTypeString) {

		char *descriptor = value_to_c_string(ctx, args[0]);
		JSStringRef str_ref = JSValueToStringCopy(ctx, args[1], NULL);

		ufile_write(descriptor_str_to_int(descriptor), str_ref);

		free(descriptor);
		JSStringRelease(str_ref);
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_file_writer_close(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									  size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *descriptor = value_to_c_string(ctx, args[0]);
		ufile_close(descriptor_str_to_int(descriptor));
		free(descriptor);
	}
	return JSValueMakeNull(ctx);
}

JSValueRef function_file_input_stream_open(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									 size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *path = value_to_c_string(ctx, args[0]);

		uint64_t descriptor = file_open_read(path);

		free(path);

		char* descriptor_str = descriptor_int_to_str(descriptor);
		JSValueRef rv =  c_string_to_value(ctx, descriptor_str);
		free(descriptor_str);

		return rv;
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_file_input_stream_read(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									 size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *descriptor = value_to_c_string(ctx, args[0]);

		size_t buf_size = 4096;
		uint8_t* buf = malloc(buf_size * sizeof(uint8_t));

		size_t read = file_read(descriptor_str_to_int(descriptor), buf_size, buf);

		free(descriptor);

		JSValueRef arguments[read];
		int num_arguments = (int)read;
		for (int i=0; i<num_arguments; i++) {
			arguments[i] = JSValueMakeNumber(ctx, buf[i]);
		}

		return JSObjectMakeArray(ctx, num_arguments, arguments, NULL);
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_file_input_stream_close(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									  size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *descriptor = value_to_c_string(ctx, args[0]);
		file_close(descriptor_str_to_int(descriptor));
		free(descriptor);
	}
	return JSValueMakeNull(ctx);
}

JSValueRef function_file_output_stream_open(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									 size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 2
		&& JSValueGetType(ctx, args[0]) == kJSTypeString
		&& JSValueGetType(ctx, args[1]) == kJSTypeBoolean) {

		char *path = value_to_c_string(ctx, args[0]);
		bool append = JSValueToBoolean(ctx, args[1]);

		uint64_t descriptor =  file_open_write(path, append);

		free(path);

		char* descriptor_str = descriptor_int_to_str(descriptor);
		JSValueRef rv =  c_string_to_value(ctx, descriptor_str);
		free(descriptor_str);

		return rv;
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_file_output_stream_write(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									  size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 2
		&& JSValueGetType(ctx, args[0]) == kJSTypeString
		&& JSValueGetType(ctx, args[1]) == kJSTypeObject) {

		char *descriptor = value_to_c_string(ctx, args[0]);

		unsigned int count = (unsigned int)array_get_count(ctx, (JSObjectRef)args[1]);
		uint8_t buf[count];
		for (unsigned int i=0; i<count; i++) {
			JSValueRef v = array_get_value_at_index(ctx, (JSObjectRef)args[1], i);
			if (JSValueIsNumber(ctx, v)) {
				double n = JSValueToNumber(ctx, v, NULL);
				if (0 <= n && n <=255) {
					buf[i] = (uint8_t)n;
				} else {
					fprintf(stderr, "Output stream value out of range %f", n);
				}
			} else {
				fprintf(stderr, "Output stream value not a number");
			}
		}

		file_write(descriptor_str_to_int(descriptor), count, buf);

		free(descriptor);
	}

	return JSValueMakeNull(ctx);
}

JSValueRef function_file_output_stream_close(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									  size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *descriptor = value_to_c_string(ctx, args[0]);
		file_close(descriptor_str_to_int(descriptor));
		free(descriptor);
	}
	return JSValueMakeNull(ctx);
}

JSValueRef function_delete_file(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
											 size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *path = value_to_c_string(ctx, args[0]);
		remove(path);
		free(path);
	}
	return JSValueMakeNull(ctx);
}

JSValueRef function_list_files(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
								size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *path = value_to_c_string(ctx, args[0]);

        size_t capacity = 32;
        size_t count = 0;
        JSValueRef* paths = malloc(capacity*sizeof(paths));

        DIR *d;
        struct dirent *dir;
        d = opendir(path);

        size_t path_len = strlen(path);
        if (path_len && path[path_len - 1] == '/') {
            path[--path_len] = 0;
        }

        if (d) {
            while ((dir = readdir(d)) != NULL) {
                if (strcmp(dir->d_name, ".") && strcmp(dir->d_name, "..")) {

                    char* buf = malloc((path_len + strlen(dir->d_name) + 2));
                    sprintf(buf, "%s/%s", path, dir->d_name);
                    paths[count++] = c_string_to_value(ctx, buf);
                    free(buf);

                    if (count == capacity) {
                        capacity *= 2;
                        paths = realloc(paths, capacity * sizeof(paths));
                    }
                }
            }

            closedir(d);
        }

        free(path);

        JSValueRef rv = JSObjectMakeArray(ctx, count, paths, NULL);
        free(paths);

        return rv;
	}
	return JSValueMakeNull(ctx);
}


JSValueRef function_is_directory(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
											 size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *path = value_to_c_string(ctx, args[0]);

		bool is_directory = false;

		struct stat file_stat;

		int retval = stat(path, &file_stat);

		free(path);

		if (retval == 0) {
			is_directory = S_ISDIR(file_stat.st_mode);
		}

		return JSValueMakeBoolean(ctx, is_directory);

	}
	return JSValueMakeNull(ctx);
}

JSValueRef function_fstat(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
									  size_t argc, const JSValueRef args[], JSValueRef* exception) {
	if (argc == 1
		&& JSValueGetType(ctx, args[0]) == kJSTypeString) {

		char *path = value_to_c_string(ctx, args[0]);

		struct stat file_stat;

		int retval = lstat(path, &file_stat);

		if (retval == 0) {
			JSObjectRef result = JSObjectMake(ctx, NULL, NULL);

			char *type = "unknown";
			if (S_ISDIR(file_stat.st_mode)) {
				type = "directory";
			} else if (S_ISREG(file_stat.st_mode)) {
				type = "file";
			} else if (S_ISLNK(file_stat.st_mode)) {
				type = "symbolic-link";
			} else if (S_ISSOCK(file_stat.st_mode)) {
				type = "socket";
			} else if (S_ISFIFO(file_stat.st_mode)) {
				type = "fifo";
			} else if (S_ISCHR(file_stat.st_mode)) {
				type = "character-special";
			} else if (S_ISBLK(file_stat.st_mode)) {
				type = "block-special";
			}

			JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("type"),
								c_string_to_value(ctx, type),
								kJSPropertyAttributeReadOnly, NULL);


			double device_id = (double)file_stat.st_rdev;
			if (device_id) {
				JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("device-id"),
									JSValueMakeNumber(ctx, device_id),
									kJSPropertyAttributeReadOnly, NULL);
			}

			double file_number = (double)file_stat.st_ino;
			if (file_number) {
				JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("file-number"),
									JSValueMakeNumber(ctx, file_number),
									kJSPropertyAttributeReadOnly, NULL);
			}

			JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("permissions"),
								JSValueMakeNumber(ctx, (double)(ACCESSPERMS & file_stat.st_mode)),
								kJSPropertyAttributeReadOnly, NULL);

			JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("reference-count"),
								JSValueMakeNumber(ctx, (double)file_stat.st_nlink),
								kJSPropertyAttributeReadOnly, NULL);

			JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("uid"),
								JSValueMakeNumber(ctx, (double)file_stat.st_uid),
								kJSPropertyAttributeReadOnly, NULL);

			struct passwd * uid_passwd = getpwuid(file_stat.st_uid);

			if (uid_passwd) {
				JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("uname"),
									c_string_to_value(ctx, uid_passwd->pw_name),
									kJSPropertyAttributeReadOnly, NULL);
			}

			JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("gid"),
								JSValueMakeNumber(ctx, (double)file_stat.st_gid),
								kJSPropertyAttributeReadOnly, NULL);

			struct group * gid_group = getgrgid(file_stat.st_gid);

			if (gid_group) {
				JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("gname"),
									c_string_to_value(ctx, gid_group->gr_name),
									kJSPropertyAttributeReadOnly, NULL);
			}

			JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("file-size"),
								JSValueMakeNumber(ctx, (double)file_stat.st_size),
								kJSPropertyAttributeReadOnly, NULL);

#ifdef __APPLE__
#define birthtime(x) x.st_birthtime
#else
#define birthtime(x) x.st_ctime
#endif

			JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("created"),
								JSValueMakeNumber(ctx, 1000*birthtime(file_stat)),
								kJSPropertyAttributeReadOnly, NULL);

			JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("modified"),
								JSValueMakeNumber(ctx, 1000*file_stat.st_mtime),
								kJSPropertyAttributeReadOnly, NULL);

			return result;
		}

		free(path);
	}
	return JSValueMakeNull(ctx);
}
