#include <assert.h>
#include <errno.h>
#include <getopt.h>
#include <libgen.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#include <JavaScriptCore/JavaScript.h>

#include "linenoise.h"

#include "bundle.h"
#include "cljs.h"
#include "io.h"
#include "jsc_utils.h"
#include "legal.h"
#include "str.h"
#include "zip.h"

#define PLANCK_VERSION "2.0"

#define CONSOLE_LOG_BUF_SIZE 1000
char console_log_buf[CONSOLE_LOG_BUF_SIZE];

bool is_tty = false;
int exit_value = 0;
struct src_path {
	char *type;
	char *path;
};
struct src_path *src_paths = NULL;
int num_src_paths = 0;
char *out_path = NULL;

#ifdef DEBUG
#define debug_print_value(prefix, ctx, val)	print_value(prefix ": ", ctx, val)
#else
#define debug_print_value(prefix, ctx, val)
#endif

void print_value(char *prefix, JSContextRef ctx, JSValueRef val) {
	if (val != NULL) {
		JSStringRef str = to_string(ctx, val);
		char *ex_str = value_to_c_string(ctx, JSValueMakeString(ctx, str));
		printf("%s%s\n", prefix, ex_str);
		free(ex_str);
	}
}

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

		bool developing = (num_src_paths == 1 &&
		                   strcmp(src_paths[0].type, "src") == 0 &&
		                   str_has_suffix(src_paths[0].path, "/planck-cljs/src/") == 0);

		if (!developing) {
			contents = bundle_get_contents(path);
			last_modified = 0;
		}

		// load from classpath
		if (contents == NULL) {
			for (int i = 0; i < num_src_paths; i++) {
				char *type = src_paths[i].type;
				char *location = src_paths[i].path;

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
			if (out_path != NULL) {
				char *full_path = str_concat(out_path, path);
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
		for (int i = 0; i < num_src_paths; i++) {
			char *type = src_paths[i].type;
			char *location = src_paths[i].path;

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

	int n = fread(buf, 1, is_tty ? 1 : 1024, stdin);
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
		if (out_path == NULL) {
			source = bundle_get_contents(path);
		} else {
			char *full_path = str_concat(out_path, path);
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

void register_global_function(JSContextRef ctx, char *name, JSObjectCallAsFunctionCallback handler) {
	JSObjectRef global_obj = JSContextGetGlobalObject(ctx);

	JSStringRef fn_name = JSStringCreateWithUTF8CString(name);
	JSObjectRef fn_obj = JSObjectMakeFunctionWithCallback(ctx, fn_name, handler);

	JSObjectSetProperty(ctx, global_obj, fn_name, fn_obj, kJSPropertyAttributeNone, NULL);
}

void usage(char *program_name) {
	printf("Planck %s\n", PLANCK_VERSION);
	printf("Usage:  %s [init-opt*] [main-opt] [arg*]\n", program_name);
	printf("\n");
	printf("  With no options or args, runs an interactive Read-Eval-Print Loop\n");
	printf("\n");
	printf("  init options:\n");
	printf("    -i path, --init=path     Load a file or resource\n");
	printf("    -e string, --eval=string Evaluate expressions in string; print non-nil\n");
	printf("                             values\n");
	printf("    -c cp, --classpath=cp    Use colon-delimited cp for source directories and\n");
	printf("                             JARs\n");
	printf("    -K, --auto-cache         Create and use .planck_cache dir for cache\n");
	printf("    -k path, --cache=path    If dir exists at path, use it for cache\n");
	printf("    -q, --quiet              Quiet mode\n");
	printf("    -v, --verbose            Emit verbose diagnostic output\n");
	// printf("    -d, --dumb-terminal      Disable line editing / VT100 terminal control\n");
	printf("    -t theme, --theme=theme  Set the color theme\n");
	// printf("    -n x, --socket-repl=x    Enable socket REPL where x is port or IP:port\n");
	printf("    -s, --static-fns         Generate static dispatch function calls\n");
	printf("    -a, --elide-asserts      Set *assert* to false to remove asserts\n");
	printf("\n");
	printf("  main options:\n");
	printf("    -m ns-name, --main=ns-name Call the -main function from a namespace with\n");
	printf("                               args\n");
	printf("    -r, --repl                 Run a repl\n");
	// printf("    path                       Run a script from a file or resource\n");
	// printf("    -                          Run a script from standard input\n");
	printf("    -h, -?, --help             Print this help message and exit\n");
	printf("    -l, --legal                Show legal info (licenses and copyrights)\n");
	printf("\n");
	printf("  operation:\n");
	printf("\n");
	printf("    - Enters the cljs.user namespace\n");
	// printf("    - Binds planck.core/*command-line-args* to a seq of strings containing\n");
	// printf("      command line args that appear after any main option\n");
	printf("    - Runs all init options in order\n");
	// printf("    - Calls a -main function or runs a repl or script if requested\n");
	printf("    - Runs a repl or script if requested\n");
	printf("\n");
	printf("  The init options may be repeated and mixed freely, but must appear before\n");
	printf("  any main option.\n");
	printf("\n");
	printf("  Paths may be absolute or relative in the filesystem.\n");
	printf("\n");
	printf("  A comprehensive User Guide for Planck can be found at http://planck-repl.org\n");
	printf("\n");
}

char *get_cljs_version() {
	char *bundle_js = bundle_get_contents("planck/bundle.js");
	if (bundle_js != NULL) {
		char *start = bundle_js + 29;
		char *version = strtok(start, " ");
		version = strdup(version);
		free(bundle_js);
		return version;
	} else {
		return "(Unknown)";
	}
}

void banner() {
	printf("Planck %s\n", PLANCK_VERSION);
	printf("ClojureScript %s\n", get_cljs_version());

	printf("    Docs: (doc function-name-here)\n");
	printf("          (find-doc \"part-of-name-here\")\n");
	printf("  Source: (source function-name-here)\n");
	printf("    Exit: Control+D or :cljs/quit or exit or quit\n");
	printf(" Results: Stored in vars *1, *2, *3, an exception in *e\n");

	printf("\n");
}

bool verbose = false;
bool quiet = false;
bool repl = false;
bool static_fns = false;
bool elide_asserts = false;
char *cache_path = NULL;
char *theme = "light";
char *main_ns_name = NULL;

bool javascript = false;

struct script {
	char *type;
	bool expression;
	char *source;
};
struct script *scripts = NULL;
int num_scripts = 0;

int main(int argc, char **argv) {
	struct option long_options[] = {
		{"help", no_argument, NULL, 'h'},
		{"legal", no_argument, NULL, 'l'},
		{"verbose", no_argument, NULL, 'v'},
		{"quiet", no_argument, NULL, 'q'},
		{"repl", no_argument, NULL, 'r'},
		{"static-fns", no_argument, NULL, 's'},
		{"elide-asserts", no_argument, NULL, 'a'},
		{"cache", required_argument, NULL, 'k'},
		{"eval", required_argument, NULL, 'e'},
		{"theme", required_argument, NULL, 't'},
		{"classpath", required_argument, NULL, 'c'},
		{"auto-cache", no_argument, NULL, 'K'},
		{"init", required_argument, NULL, 'i'},
		{"main", required_argument, NULL, 'm'},

		// development options
		{"javascript", no_argument, NULL, 'j'},
		{"out", required_argument, NULL, 'o'},

		{0, 0, 0, 0}
	};
	int opt, option_index;
	while ((opt = getopt_long(argc, argv, "h?lvrsak:je:t:c:o:Ki:qm:", long_options, &option_index)) != -1) {
		switch (opt) {
		case 'h':
			usage(argv[0]);
			exit(0);
		case 'l':
			legal();
			return 0;
		case 'v':
			verbose = true;
			break;
		case 'q':
			quiet = true;
			break;
		case 'r':
			repl = true;
			break;
		case 's':
			static_fns = true;
			break;
		case 'a':
			elide_asserts = true;
			break;
		case 'k':
			cache_path = argv[optind - 1];
			break;
		case 'K':
			cache_path = ".planck_cache";
			{
				char *path_copy = strdup(cache_path);
				char *dir = dirname(path_copy);
				if (mkdir_p(dir) < 0) {
					fprintf(stderr, "Could not create %s: %s\n", cache_path, strerror(errno));
				}
				free(path_copy);
			}
			break;
		case 'j':
			javascript = true;
			break;
		case 'e':
			num_scripts += 1;
			scripts = realloc(scripts, num_scripts * sizeof(struct script));
			scripts[num_scripts - 1].type = "text";
			scripts[num_scripts - 1].expression = true;
			scripts[num_scripts - 1].source = argv[optind - 1];
			break;
		case 'i':
			num_scripts += 1;
			scripts = realloc(scripts, num_scripts * sizeof(struct script));
			scripts[num_scripts - 1].type = "path";
			scripts[num_scripts - 1].expression = false;
			scripts[num_scripts - 1].source = argv[optind - 1];
			break;
		case 'm':
			main_ns_name = argv[optind - 1];
		case 't':
			theme = argv[optind - 1];
			break;
		case 'c':
			{
				char *classpath = argv[optind - 1];
				char *source = strtok(classpath, ":");
				while (source != NULL) {
					char *type = "src";
					if (str_has_suffix(source, ".jar") == 0) {
						type = "jar";
					}

					num_src_paths += 1;
					src_paths = realloc(src_paths, num_src_paths * sizeof(struct src_path));
					src_paths[num_src_paths - 1].type = type;
					src_paths[num_src_paths - 1].path = strdup(source);

					source = strtok(NULL, ":");
				}

				break;
			}
		case 'o':
			out_path = argv[optind - 1];
			break;
		case '?':
			usage(argv[0]);
			exit(1);
		default:
			printf("unhandled argument: %c\n", opt);
		}
	}

	int num_rest_args = 0;
	char **rest_args = NULL;
	if (optind < argc) {
		num_rest_args = argc - optind;
		rest_args = malloc((argc - optind) * sizeof(char*));
		int i = 0;
		while (optind < argc) {
			rest_args[i++] = argv[optind++];
		}
	}

	if (num_scripts == 0 && main_ns_name == NULL && num_rest_args == 0) {
		repl = true;
	}

	if (main_ns_name != NULL && repl) {
		printf("Only one main-opt can be specified.");
	}

	JSGlobalContextRef ctx = JSGlobalContextCreate(NULL);

	JSStringRef nameRef = JSStringCreateWithUTF8CString("planck");
	JSGlobalContextSetName(ctx, nameRef);

	evaluate_script(ctx, "var global = this;", "<init>");

	register_global_function(ctx, "AMBLY_IMPORT_SCRIPT", function_import_script);
	bootstrap(ctx, out_path);

	register_global_function(ctx, "PLANCK_CONSOLE_LOG", function_console_log);
	register_global_function(ctx, "PLANCK_CONSOLE_ERROR", function_console_error);

	evaluate_script(ctx, "var console = {};"\
			"console.log = PLANCK_CONSOLE_LOG;"\
			"console.error = PLANCK_CONSOLE_ERROR;", "<init>");

	evaluate_script(ctx, "var PLANCK_VERSION = \"" PLANCK_VERSION "\";", "<init>");

	// require app namespaces
	evaluate_script(ctx, "goog.require('planck.repl');", "<init>");

	// without this things won't work
	evaluate_script(ctx, "var window = global;", "<init>");

	register_global_function(ctx, "PLANCK_READ_FILE", function_read_file);
	register_global_function(ctx, "PLANCK_LOAD", function_load);
	register_global_function(ctx, "PLANCK_LOAD_DEPS_CLJS_FILES", function_load_deps_cljs_files);
	register_global_function(ctx, "PLANCK_CACHE", function_cache);

	register_global_function(ctx, "PLANCK_EVAL", function_eval);

	register_global_function(ctx, "PLANCK_GET_TERM_SIZE", function_get_term_size);
	register_global_function(ctx, "PLANCK_PRINT_FN", function_print_fn);
	register_global_function(ctx, "PLANCK_PRINT_ERR_FN", function_print_err_fn);

	register_global_function(ctx, "PLANCK_SET_EXIT_VALUE", function_set_exit_value);

	is_tty = isatty(STDIN_FILENO) == 1;
	register_global_function(ctx, "PLANCK_RAW_READ_STDIN", function_raw_read_stdin);
	register_global_function(ctx, "PLANCK_RAW_WRITE_STDOUT", function_raw_write_stdout);
	register_global_function(ctx, "PLANCK_RAW_FLUSH_STDOUT", function_raw_flush_stdout);
	register_global_function(ctx, "PLANCK_RAW_WRITE_STDERR", function_raw_write_stderr);
	register_global_function(ctx, "PLANCK_RAW_FLUSH_STDERR", function_raw_flush_stderr);

	{
		JSValueRef arguments[num_rest_args];
		for (int i = 0; i < num_rest_args; i++) {
			arguments[i] = c_string_to_value(ctx, rest_args[i]);
		}
		JSValueRef args_ref = JSObjectMakeArray(ctx, num_rest_args, arguments, NULL);

		JSValueRef global_obj = JSContextGetGlobalObject(ctx);
		JSStringRef prop = JSStringCreateWithUTF8CString("PLANCK_INITIAL_COMMAND_LINE_ARGS");
		JSObjectSetProperty(ctx, JSValueToObject(ctx, global_obj, NULL), prop, args_ref, kJSPropertyAttributeNone, NULL);
		JSStringRelease(prop);
	}

	evaluate_script(ctx, "cljs.core.set_print_fn_BANG_.call(null,PLANCK_PRINT_FN);", "<init>");
	evaluate_script(ctx, "cljs.core.set_print_err_fn_BANG_.call(null,PLANCK_PRINT_ERR_FN);", "<init>");

	char *elide_script = str_concat("cljs.core._STAR_assert_STAR_ = ", elide_asserts ? "false" : "true");
	evaluate_script(ctx, elide_script, "<init>");
	free(elide_script);

	{
		JSValueRef arguments[4];
		arguments[0] = JSValueMakeBoolean(ctx, repl);
		arguments[1] = JSValueMakeBoolean(ctx, verbose);
		JSValueRef cache_path_ref = NULL;
		if (cache_path != NULL) {
			JSStringRef cache_path_str = JSStringCreateWithUTF8CString(cache_path);
			cache_path_ref = JSValueMakeString(ctx, cache_path_str);
		}
		arguments[2] = cache_path_ref;
		arguments[3] = JSValueMakeBoolean(ctx, static_fns);
		JSValueRef ex = NULL;
		JSObjectCallAsFunction(ctx, get_function(ctx, "planck.repl", "init"), JSContextGetGlobalObject(ctx), 4, arguments, &ex);
		debug_print_value("planck.repl/init", ctx, ex);
	}

	if (repl) {
		evaluate_source(ctx, "text", "(require '[planck.repl :refer-macros [apropos dir find-doc doc source pst]])", true, false, "cljs.user", "dumb");
	}

	evaluate_script(ctx, "goog.provide('cljs.user');", "<init>");
	evaluate_script(ctx, "goog.require('cljs.core');", "<init>");

	evaluate_script(ctx, "cljs.core._STAR_assert_STAR_ = true;", "<init>");

	// Process init arguments

	for (int i = 0; i < num_scripts; i++) {
		// TODO: exit if not successfull
		evaluate_source(ctx, scripts[i].type, scripts[i].source, scripts[i].expression, false, NULL, theme);
	}

	// Process main arguments

	if (main_ns_name != NULL) {
		run_main_in_ns(ctx, main_ns_name, num_rest_args, rest_args);
	} else if (!repl && num_rest_args > 0) {
		char *path = rest_args[0];

		struct script script;
		if (strcmp(path, "-") == 0) {
			char *source = read_all(stdin);
			script.type = "text";
			script.source = source;
			script.expression = false;
		} else {
			script.type = "path";
			script.source = path;
			script.expression = false;
		}

		evaluate_source(ctx, script.type, script.source, script.expression, false, NULL, theme);
	} else if (repl) {
		if (!quiet) {
			banner();
		}

		char *home = getenv("HOME");
		char *history_path = NULL;
		if (home != NULL) {
			char history_name[] = ".planck_history";
			int len = strlen(home) + strlen(history_name) + 2;
			history_path = malloc(len * sizeof(char));
			snprintf(history_path, len, "%s/%s", home, history_name);

			linenoiseHistoryLoad(history_path);
		}

		char *prompt = javascript ? " > " : "cljs.user=> ";
		char *current_ns = get_current_ns(ctx);
		if (!javascript) {
			prompt = str_concat(current_ns, "=> ");
		}

		char *line;
		while ((line = linenoise(prompt, "\x1b[36m", 0)) != NULL) {
			if (javascript) {
				JSValueRef res = evaluate_script(ctx, line, "<stdin>");
				print_value("", ctx, res);
			} else {
				evaluate_source(ctx, "text", line, true, true, current_ns, theme);
				char *new_ns = get_current_ns(ctx);
				free(current_ns);
				free(prompt);
				current_ns = new_ns;
				prompt = str_concat(current_ns, "=> ");
			}
			linenoiseHistoryAdd(line);
			if (history_path != NULL) {
				linenoiseHistorySave(history_path);
			}
			free(line);
		}
	}

	return exit_value;
}
