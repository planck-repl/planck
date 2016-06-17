JSValueRef function_console_log(JSContextRef ctx, JSObjectRef function, JSObjectRef this_object, size_t argc, const JSValueRef args[], JSValueRef* exception);
JSValueRef function_console_error(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);

JSValueRef function_read_file(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);
JSValueRef function_load(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,	size_t argc, const JSValueRef args[], JSValueRef* exception);
JSValueRef function_load_deps_cljs_files(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,	size_t argc, const JSValueRef args[], JSValueRef* exception);
JSValueRef function_cache(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,	size_t argc, const JSValueRef args[], JSValueRef* exception);

JSValueRef function_eval(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject,	size_t argc, const JSValueRef args[], JSValueRef* exception);

JSValueRef function_get_term_size(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);

JSValueRef function_print_fn(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);
JSValueRef function_print_err_fn(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);

JSValueRef function_set_exit_value(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);

JSValueRef function_raw_read_stdin(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);
JSValueRef function_raw_write_stdout(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);
JSValueRef function_raw_flush_stdout(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);
JSValueRef function_raw_write_stderr(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);
JSValueRef function_raw_flush_stderr(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);

JSValueRef function_import_script(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef args[], JSValueRef* exception);
