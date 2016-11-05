#include <assert.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

#include <JavaScriptCore/JavaScript.h>

#include "bundle.h"
#include "functions.h"
#include "globals.h"
#include "http.h"
#include "shell.h"
#include "io.h"
#include "jsc_utils.h"
#include "str.h"
#include "cljs.h"

static volatile int keep_running = 1;

void int_handler(int dummy) {
    keep_running = 0;
    signal(SIGINT, NULL);
}

void set_int_handler() {
    signal(SIGINT, int_handler);
}

void clear_int_handler() {
    signal(SIGINT, NULL);
    keep_running = 1;
}

bool should_keep_running() {
    return keep_running != 0;
}

bool cljs_engine_ready = false;
pthread_mutex_t engine_init_lock = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t engine_init_cond = PTHREAD_COND_INITIALIZER;
pthread_t engine_init_thread;

void block_until_engine_ready() {
    pthread_mutex_lock(&engine_init_lock);
    while (!cljs_engine_ready) {
        pthread_cond_wait(&engine_init_cond, &engine_init_lock);
    }
    pthread_mutex_unlock(&engine_init_lock);
}

void signal_engine_ready() {
    pthread_mutex_lock(&engine_init_lock);
    cljs_engine_ready = true;
    pthread_cond_signal(&engine_init_cond);
    pthread_mutex_unlock(&engine_init_lock);
}

char *munge(char *s) {
    size_t len = strlen(s);
    size_t new_len = 0;
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

    char *ms = malloc((new_len + 1) * sizeof(char));
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
    char *ns_tmp = strdup(namespace);
    char *ns_part = strtok(ns_tmp, ".");
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
    free(ns_tmp);

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

JSValueRef
evaluate_source(JSContextRef ctx, char *type, char *source, bool expression, bool print_nil, char *set_ns, char *theme,
                bool block_until_ready, int session_id) {
    if (block_until_ready) {
        block_until_engine_ready();
    }

    JSValueRef args[6];
    size_t num_args = 6;

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
    args[5] = JSValueMakeNumber(ctx, session_id);

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
    evaluate_script(ctx, "CLOSURE_IMPORT_SCRIPT = function(src) { AMBLY_IMPORT_SCRIPT('goog/' + src); return true; }",
                    source);

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

    evaluate_script(ctx,
                    "goog.require = function (name) { return CLOSURE_IMPORT_SCRIPT(goog.dependencies_.nameToPath[name]); };",
                    source);

    evaluate_script(ctx, "goog.require('cljs.core');", source);

    // redef goog.require to track loaded libs
    evaluate_script(ctx,
                    "cljs.core._STAR_loaded_libs_STAR_ = cljs.core.into.call(null, cljs.core.PersistentHashSet.EMPTY, [\"cljs.core\"]);\n"
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

void run_main_in_ns(JSContextRef ctx, char *ns, size_t argc, char **argv) {
    block_until_engine_ready();

    size_t num_arguments = argc + 1;
    JSValueRef arguments[num_arguments];
    arguments[0] = c_string_to_value(ctx, ns);
    for (int i = 1; i < num_arguments; i++) {
        arguments[i] = c_string_to_value(ctx, argv[i - 1]);
    }

    JSObjectRef global_obj = JSContextGetGlobalObject(ctx);
    JSObjectRef run_main_fn = get_function(ctx, "planck.repl", "run-main");
    JSObjectCallAsFunction(ctx, run_main_fn, global_obj, num_arguments, arguments, NULL);
}

char *get_current_ns(JSContextRef ctx) {
    block_until_engine_ready();

    size_t num_arguments = 0;
    JSValueRef arguments[num_arguments];
    JSObjectRef get_current_ns_fn = get_function(ctx, "planck.repl", "get-current-ns");
    JSValueRef result = JSObjectCallAsFunction(ctx, get_current_ns_fn, JSContextGetGlobalObject(ctx), num_arguments,
                                               arguments, NULL);
    return value_to_c_string(ctx, result);
}

char **get_completions(JSContextRef ctx, const char *buffer, int *num_completions) {
    block_until_engine_ready();

    size_t num_arguments = 1;
    JSValueRef arguments[num_arguments];
    arguments[0] = c_string_to_value(ctx, (char *) buffer);
    JSObjectRef completions_fn = get_function(ctx, "planck.repl", "get-completions");
    JSValueRef result = JSObjectCallAsFunction(ctx, completions_fn, JSContextGetGlobalObject(ctx), num_arguments,
                                               arguments, NULL);

    assert(JSValueIsObject(ctx, result));
    JSObjectRef array = JSValueToObject(ctx, result, NULL);
    JSStringRef length_prop = JSStringCreateWithUTF8CString("length");
    JSValueRef array_len = JSObjectGetProperty(ctx, array, length_prop, NULL);
    JSStringRelease(length_prop);
    assert(JSValueIsNumber(ctx, array_len));
    int n = (int) JSValueToNumber(ctx, array_len, NULL);

    char **completions = malloc(n * sizeof(char *));

    for (unsigned int i = 0; i < n; i++) {
        JSValueRef v = JSObjectGetPropertyAtIndex(ctx, array, i, NULL);
        completions[i] = value_to_c_string(ctx, v);
    }

    *num_completions = n;
    return completions;
}

void register_global_function(JSContextRef ctx, char *name, JSObjectCallAsFunctionCallback handler) {
    JSObjectRef global_obj = JSContextGetGlobalObject(ctx);

    JSStringRef fn_name = JSStringCreateWithUTF8CString(name);
    JSObjectRef fn_obj = JSObjectMakeFunctionWithCallback(ctx, fn_name, handler);

    JSObjectSetProperty(ctx, global_obj, fn_name, fn_obj, kJSPropertyAttributeNone, NULL);
}

void discarding_sender(const char *msg) {
    /* Intentionally empty. */
}

void *cljs_do_engine_init(void *data) {
    JSGlobalContextRef ctx = data;

    JSStringRef nameRef = JSStringCreateWithUTF8CString("planck");
    JSGlobalContextSetName(ctx, nameRef);

    evaluate_script(ctx, "var global = this;", "<init>");

    register_global_function(ctx, "AMBLY_IMPORT_SCRIPT", function_import_script);
    bootstrap(ctx, config.out_path);

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

    register_global_function(ctx, "PLANCK_SET_EXIT_VALUE", function_set_exit_value);

    register_global_function(ctx, "PLANCK_SHELL_SH", function_shellexec);

    register_global_function(ctx, "PLANCK_RAW_READ_STDIN", function_raw_read_stdin);
    register_global_function(ctx, "PLANCK_RAW_WRITE_STDOUT", function_raw_write_stdout);
    register_global_function(ctx, "PLANCK_RAW_FLUSH_STDOUT", function_raw_flush_stdout);
    register_global_function(ctx, "PLANCK_RAW_WRITE_STDERR", function_raw_write_stderr);
    register_global_function(ctx, "PLANCK_RAW_FLUSH_STDERR", function_raw_flush_stderr);

    register_global_function(ctx, "PLANCK_FILE_READER_OPEN", function_file_reader_open);
    register_global_function(ctx, "PLANCK_FILE_READER_READ", function_file_reader_read);
    register_global_function(ctx, "PLANCK_FILE_READER_CLOSE", function_file_reader_close);

    register_global_function(ctx, "PLANCK_FILE_WRITER_OPEN", function_file_writer_open);
    register_global_function(ctx, "PLANCK_FILE_WRITER_WRITE", function_file_writer_write);
    register_global_function(ctx, "PLANCK_FILE_WRITER_CLOSE", function_file_writer_close);

    register_global_function(ctx, "PLANCK_FILE_INPUT_STREAM_OPEN", function_file_input_stream_open);
    register_global_function(ctx, "PLANCK_FILE_INPUT_STREAM_READ", function_file_input_stream_read);
    register_global_function(ctx, "PLANCK_FILE_INPUT_STREAM_CLOSE", function_file_input_stream_close);

    register_global_function(ctx, "PLANCK_FILE_OUTPUT_STREAM_OPEN", function_file_output_stream_open);
    register_global_function(ctx, "PLANCK_FILE_OUTPUT_STREAM_WRITE", function_file_output_stream_write);
    register_global_function(ctx, "PLANCK_FILE_OUTPUT_STREAM_CLOSE", function_file_output_stream_close);

    register_global_function(ctx, "PLANCK_DELETE", function_delete_file);

    register_global_function(ctx, "PLANCK_LIST_FILES", function_list_files);

    register_global_function(ctx, "PLANCK_IS_DIRECTORY", function_is_directory);

    register_global_function(ctx, "PLANCK_FSTAT", function_fstat);

    register_global_function(ctx, "PLANCK_REQUEST", function_http_request);

    register_global_function(ctx, "PLANCK_READ_PASSWORD", function_read_password);

    {
        JSValueRef arguments[config.num_rest_args];
        for (int i = 0; i < config.num_rest_args; i++) {
            arguments[i] = c_string_to_value(ctx, config.rest_args[i]);
        }
        JSValueRef args_ref = JSObjectMakeArray(ctx, config.num_rest_args, arguments, NULL);

        JSValueRef global_obj = JSContextGetGlobalObject(ctx);
        JSStringRef prop = JSStringCreateWithUTF8CString("PLANCK_INITIAL_COMMAND_LINE_ARGS");
        JSObjectSetProperty(ctx, JSValueToObject(ctx, global_obj, NULL), prop, args_ref, kJSPropertyAttributeNone,
                            NULL);
        JSStringRelease(prop);
    }

    register_global_function(ctx, "PLANCK_SET_TIMEOUT", function_set_timeout);
    evaluate_script(ctx, "var PLANCK_CALLBACK_STORE = {};\nvar setTimeout = function( fn, ms ) {\nPLANCK_CALLBACK_STORE[PLANCK_SET_TIMEOUT(ms)] = fn;\n}\nvar PLANCK_RUN_TIMEOUT = function( id ) {\nif( PLANCK_CALLBACK_STORE[id] )\nPLANCK_CALLBACK_STORE[id]();\nPLANCK_CALLBACK_STORE[id] = null;\n}\n", "<init>");

    cljs_set_print_sender(ctx, &discarding_sender);

    {
        JSValueRef arguments[5];
        arguments[0] = JSValueMakeBoolean(ctx, config.repl);
        arguments[1] = JSValueMakeBoolean(ctx, config.verbose);
        JSValueRef cache_path_ref = NULL;
        if (config.cache_path != NULL) {
            JSStringRef cache_path_str = JSStringCreateWithUTF8CString(config.cache_path);
            cache_path_ref = JSValueMakeString(ctx, cache_path_str);
        }
        arguments[2] = cache_path_ref;
        arguments[3] = JSValueMakeBoolean(ctx, config.static_fns);
        arguments[4] = JSValueMakeBoolean(ctx, config.elide_asserts);
        JSValueRef ex = NULL;
        JSObjectCallAsFunction(ctx, get_function(ctx, "planck.repl", "init"), JSContextGetGlobalObject(ctx), 5,
                               arguments, &ex);
        debug_print_value("planck.repl/init", ctx, ex);
    }

    if (config.repl) {
        evaluate_source(ctx, "text", "(require '[planck.repl :refer-macros [apropos dir find-doc doc source pst]])",
                        true, false, "cljs.user", "dumb", false, 0);
    }

    cljs_set_print_sender(ctx, NULL);

    evaluate_script(ctx, "goog.provide('cljs.user');", "<init>");
    evaluate_script(ctx, "goog.require('cljs.core');", "<init>");

    signal_engine_ready();

    return NULL;
}

void cljs_engine_init(JSContextRef ctx) {
    sigset_t set;
    sigemptyset(&set);
    // FIXME: Figure out where SIGUSR2 comes from
    //   (Without blocking SIGUSR2 things mysteriously don't work)
    sigaddset(&set, SIGUSR2);
    pthread_sigmask(SIG_BLOCK, &set, NULL);

    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    int ret = pthread_create(&engine_init_thread, &attr, cljs_do_engine_init, (void *) ctx);
    if (ret != 0) {
        perror("pthread_create");
        exit(1);
    }
    pthread_attr_destroy(&attr);
}

void (*cljs_sender)(const char *msg) = NULL;

JSValueRef function_print_fn_sender(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,
                                    size_t argc, const JSValueRef args[], JSValueRef *exception) {
    if (argc == 1 && JSValueIsString(ctx, args[0])) {
        char *str = value_to_c_string(ctx, args[0]);

        cljs_sender(str);

        free(str);
    }

    return JSValueMakeNull(ctx);
}

void cljs_set_print_sender(JSContextRef ctx, void (*sender)(const char *msg)) {
    if (sender) {
        cljs_sender = sender;
        register_global_function(ctx, "PLANCK_PRINT_FN", function_print_fn_sender);
        register_global_function(ctx, "PLANCK_PRINT_ERR_FN", function_print_fn_sender);
    } else {
        register_global_function(ctx, "PLANCK_PRINT_FN", function_print_fn);
        register_global_function(ctx, "PLANCK_PRINT_ERR_FN", function_print_err_fn);
    }

    evaluate_script(ctx, "cljs.core.set_print_fn_BANG_.call(null,PLANCK_PRINT_FN);", "<init>");
    evaluate_script(ctx, "cljs.core.set_print_err_fn_BANG_.call(null,PLANCK_PRINT_ERR_FN);", "<init>");

}

bool cljs_print_newline(JSContextRef ctx) {
    return JSValueToBoolean(ctx,
                            evaluate_script(ctx, "cljs.core._STAR_print_newline_STAR_", "<cljs.c:cljs_print_newline>"));
}

char *cljs_is_readable(JSContextRef ctx, char *expression) {
    block_until_engine_ready();

    size_t num_arguments = 2;
    JSValueRef arguments[num_arguments];
    arguments[0] = c_string_to_value(ctx, expression);
    arguments[1] = c_string_to_value(ctx, config.theme);
    JSValueRef result = JSObjectCallAsFunction(ctx, get_function(ctx, "planck.repl", "is-readable?"),
                                               JSContextGetGlobalObject(ctx), num_arguments, arguments, NULL);
    return value_to_c_string(ctx, result);
}

int cljs_indent_space_count(JSContextRef ctx, char *text) {
    block_until_engine_ready();

    size_t num_arguments = 1;
    JSValueRef arguments[num_arguments];
    arguments[0] = c_string_to_value(ctx, text);
    JSValueRef result = JSObjectCallAsFunction(ctx, get_function(ctx, "planck.repl", "indent-space-count"),
                                               JSContextGetGlobalObject(ctx), num_arguments, arguments, NULL);
    return (int) JSValueToNumber(ctx, result, NULL);
}

void cljs_highlight_coords_for_pos(JSContextRef ctx, int pos, const char *buf, size_t num_previous_lines,
                                   char **previous_lines, int *num_lines_up, int *highlight_pos) {
    block_until_engine_ready();

    size_t num_arguments = 3;
    JSValueRef arguments[num_arguments];
    arguments[0] = JSValueMakeNumber(ctx, pos);
    arguments[1] = c_string_to_value(ctx, buf);
    JSValueRef prev_lines[num_previous_lines];
    for (int i = 0; i < num_previous_lines; i++) {
        prev_lines[i] = c_string_to_value(ctx, previous_lines[i]);
    }
    arguments[2] = JSObjectMakeArray(ctx, num_previous_lines, prev_lines, NULL);
    JSValueRef result = JSObjectCallAsFunction(ctx, get_function(ctx, "planck.repl", "get-highlight-coords"),
                                               JSContextGetGlobalObject(ctx), num_arguments, arguments, NULL);

    JSObjectRef array = JSValueToObject(ctx, result, NULL);
    if (array) {
        *num_lines_up = (int) JSValueToNumber(ctx, JSObjectGetPropertyAtIndex(ctx, array, 0, NULL), NULL);
        *highlight_pos = (int) JSValueToNumber(ctx, JSObjectGetPropertyAtIndex(ctx, array, 1, NULL), NULL);
    }
}
