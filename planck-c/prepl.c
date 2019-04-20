#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "engine.h"
#include "globals.h"
#include "jsc_utils.h"
#include "repl.h"
#include "sockets.h"
#include "str.h"

prepl_t *make_prepl() {
    prepl_t *prepl = malloc(sizeof(prepl_t));
    prepl->in = NULL;
    prepl->out = NULL;
    prepl->tap = NULL;
    return prepl;
}

static void evaluate_source_and_structure(char *source, char *set_ns, int session_id, prepl_t *prepl) {
    int err = block_until_engine_ready();
    if (err) {
        engine_println(block_until_engine_ready_failed_msg);
        return;
    }

    JSValueRef args[5];
    size_t num_args = 5;

    JSStringRef source_str = JSStringCreateWithUTF8CString(source);
    args[0] = JSValueMakeString(ctx, source_str);

    JSStringRef set_ns_str = JSStringCreateWithUTF8CString(set_ns);
    args[1] = JSValueMakeString(ctx, set_ns_str);

    args[2] = JSValueMakeNumber(ctx, session_id);

    args[3] = prepl->in;

    args[4] = prepl->out;

    static JSObjectRef execute_fn = NULL;
    if (!execute_fn) {
        execute_fn = get_function("planck.prepl", "execute");
        JSValueProtect(ctx, execute_fn);
    }

    JSObjectRef global_obj = JSContextGetGlobalObject(ctx);

    acquire_eval_lock();
    JSObjectCallAsFunction(ctx, execute_fn, global_obj, num_args, args, NULL);
    release_eval_lock();
}

static bool process_line(int sock, repl_t *repl, char *input_line, bool split_on_newlines) {
    // Accumulate input lines

    if (repl->input == NULL) {
        repl->input = input_line;
    } else {
        repl->input = realloc(repl->input, (strlen(repl->input) + strlen(input_line) + 2) * sizeof(char));
        sprintf(repl->input + strlen(repl->input), "\n%s", input_line);
    }

    // Check for explicit exit
    if (is_exit_command(repl->input, true)) {
        return true;
    }

    // Check if we now have readable forms
    // and if so, evaluate them

    bool done = false;
    char *balance_text = NULL;

    while (!done) {
        if ((balance_text = is_readable(repl->input)) != NULL) {
            repl->input[strlen(repl->input) - strlen(balance_text)] = '\0';

            if (!is_whitespace(repl->input)) { // Guard against empty string being read
                // TODO: set exit value

                evaluate_source_and_structure(repl->input, repl->current_ns, repl->session_id, repl->prepl);

                if (exit_value != 0) {
                    free(repl->input);
                    return true;
                }
            } else {
                // TODO: Change to socket write?
                engine_print("\n");
            }

            // Now that we've evaluated the input, reset for next round

            free(repl->input);
            repl->input = balance_text;

            // Fetch the current namespace and use it to set the prompt

            char *current_ns = get_current_ns();
            if (current_ns) {
                free(repl->current_ns);
                repl->current_ns = current_ns;
            }

            if (is_whitespace(balance_text)) {
                done = true;
                free(repl->input);
                repl->input = NULL;
            }
        } else {
            done = true;
        }
    }

    return false;
}

static void setup(prepl_t *prepl, int sock) {
    int err = block_until_engine_ready();
    if (err) {
        engine_println(block_until_engine_ready_failed_msg);
        return;
    }

    JSValueRef args[1];
    size_t num_args = 1;

    args[0] = JSValueMakeNumber(ctx, sock);

    static JSObjectRef execute_fn = NULL;
    if (!execute_fn) {
        execute_fn = get_function("planck.prepl", "channels");
        JSValueProtect(ctx, execute_fn);
    }

    JSObjectRef global_obj = JSContextGetGlobalObject(ctx);

    acquire_eval_lock();
    JSValueRef res = JSObjectCallAsFunction(ctx, execute_fn, global_obj, num_args, args, NULL);
    release_eval_lock();

    JSObjectRef res_obj = JSValueToObject(ctx, res, NULL);
    prepl->in = JSObjectGetProperty(ctx, res_obj, JSStringCreateWithUTF8CString("in"), NULL);
    prepl->out = JSObjectGetProperty(ctx, res_obj, JSStringCreateWithUTF8CString("out"), NULL);
    prepl->tap = JSObjectGetProperty(ctx, res_obj, JSStringCreateWithUTF8CString("tap"), NULL);

    args[0] = prepl->tap;

    static JSObjectRef add_tap_fn = NULL;
    if (!add_tap_fn) {
        add_tap_fn = get_function("planck.prepl", "add-tap");
        JSValueProtect(ctx, add_tap_fn);
    }

    acquire_eval_lock();
    JSObjectCallAsFunction(ctx, add_tap_fn, global_obj, num_args, args, NULL);
    release_eval_lock();
}

static void teardown(prepl_t *prepl) {
    int err = block_until_engine_ready();
    if (err) {
        engine_println(block_until_engine_ready_failed_msg);
        return;
    }

    JSValueRef args[1];
    size_t num_args = 1;

    args[0] = prepl->tap;

    static JSObjectRef remove_tap_fn = NULL;
    if (!remove_tap_fn) {
        remove_tap_fn = get_function("planck.prepl", "remove-tap");
        JSValueProtect(ctx, remove_tap_fn);
    }

    JSObjectRef global_obj = JSContextGetGlobalObject(ctx);

    acquire_eval_lock();
    JSObjectCallAsFunction(ctx, remove_tap_fn, global_obj, num_args, args, NULL);
    release_eval_lock();
}

conn_data_cb_ret_t *prepl_data_arrived(char *data, int sock, void *state) {
    int err = 0;
    bool exit = false;
    repl_t *repl = state;

    if (data) {
        if (str_has_suffix(data, "\r\n") == 0) {
            data[strlen(data) - 2] = '\0';
        } else if (str_has_suffix(data, "\n") == 0) {
            data[strlen(data) - 1] = '\0';
        }

        exit = process_line(sock, repl, strdup(data), false);
    } else {
        exit = true;
    }

    if (exit) {
        teardown(repl->prepl);
    }

    conn_data_cb_ret_t *connection_data_arrived_return = malloc(sizeof(conn_data_cb_ret_t));

    connection_data_arrived_return->err = err;
    connection_data_arrived_return->close = exit;

    return connection_data_arrived_return;
}

accepted_conn_cb_ret_t *prepl_accepted_connection(int sock, void *info) {
    repl_t *repl = make_repl();
    repl->prepl = make_prepl();
    repl->current_prompt = "";
    repl->session_id = ++session_id_counter;

    setup(repl->prepl, sock);

    int err = write_to_socket(sock, "");

    accepted_conn_cb_ret_t *accepted_connection_cb_return = malloc(sizeof(accepted_conn_cb_ret_t));

    accepted_connection_cb_return->err = err;
    accepted_connection_cb_return->info = repl;

    return accepted_connection_cb_return;
}

void prepl_listen_successful_cb() {
    if (!config.quiet) {
        char msg[1024];
        snprintf(msg, 1024, "Planck pREPL listening at %s:%d.\n", config.prepl_host,
                 config.prepl_port);
        engine_print(msg);
    }
}
