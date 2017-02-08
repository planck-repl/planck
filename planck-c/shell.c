#include <assert.h>
#include <pthread.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <sys/wait.h>
#include <JavaScriptCore/JavaScript.h>
#include <poll.h>
#include <sysexits.h>
#include "engine.h"
#include "jsc_utils.h"
#include "tasks.h"

static char **cmd(JSContextRef ctx, const JSObjectRef array) {
    int argc = array_get_count(ctx, array);
    char **result = NULL;
    if (argc > 0) {
        result = malloc(sizeof(char *) * (argc + 1));
        unsigned int i;
        for (i = 0; i < argc; i++) {
            JSValueRef val = array_get_value_at_index(ctx, array, i);
            if (JSValueGetType(ctx, val) != kJSTypeString) {
                int j;
                for (j = 0; j < i; j++) {
                    free(result[j]);
                }
                free(result);
                return NULL;
            }
            result[i] = value_to_c_string(ctx, val);
        }
        result[argc] = 0;
    }
    return result;
}

static char **env(JSContextRef ctx, const JSObjectRef map) {
    int argc = array_get_count(ctx, map);
    char **result = NULL;
    if (argc > 0) {
        result = malloc(sizeof(char *) * (argc + 1));
        unsigned int i;
        for (i = 0; i < argc; i++) {
            JSObjectRef keyVal = (JSObjectRef) array_get_value_at_index(ctx, map, i);
            char *key = value_to_c_string(ctx, array_get_value_at_index(ctx, keyVal, 0));
            char *value = value_to_c_string(ctx, array_get_value_at_index(ctx, keyVal, 1));
            size_t len = strlen(key) + strlen(value) + 2;
            char *combined = malloc(len);
            combined[0] = 0;
            snprintf(combined, len, "%s=%s", key, value);
            free(key);
            free(value);
            result[i] = combined;
        }
        result[argc] = 0;
    }
    return result;
}

static void preopen(int old, int new) {
    dup2(old, new);
    close(old);
}

struct SystemResult {
    int status;
    char *stdout;
    char *stderr;
};

static JSObjectRef create_shell_result(JSContextRef ctx, int status, char *out, char *err) {
    JSValueRef arguments[3];
    arguments[0] = JSValueMakeNumber(ctx, status);
    arguments[1] = c_string_to_value(ctx, out);
    arguments[2] = c_string_to_value(ctx, err);

    return JSObjectMakeArray(ctx, 3, arguments, NULL);
}

static JSObjectRef result_to_object_ref(JSContextRef ctx, struct SystemResult *result) {

    // Hack to avoid an optimizer bug. Grab the items we want to free now.
    char *x = result->stdout;
    char *y = result->stderr;

    JSObjectRef rv = create_shell_result(ctx, result->status, result->stdout, result->stderr);

    free(x);
    free(y);

    return rv;
}

struct ThreadParams {
    struct SystemResult res;
    int errpipe;
    int outpipe;
    int inpipe;
    pid_t pid;
    int cb_idx;
};

int read_child_pipe(int pipe, char **buf_p, size_t *total_p) {

    const size_t BLOCK_SIZE = 4096;

    if (!*buf_p) {
        *buf_p = malloc(BLOCK_SIZE);
        *total_p = 0;
    }

    size_t num_to_read = BLOCK_SIZE - *total_p % BLOCK_SIZE;
    ssize_t num_read = 0;

    if ((num_read = read(pipe, *buf_p + *total_p, num_to_read)) > 0) {
        *total_p += num_read;
        if (num_read == num_to_read) {
            *buf_p = realloc(*buf_p, *total_p + BLOCK_SIZE);
        }
        return 1;
    } else if (num_read == 0) {
        (*buf_p)[*total_p] = 0;
        return 0;
    } else {
        // TODO. num_read negative?
        engine_println("error reading");
        return -1;
    }
}

void read_child_pipes(struct ThreadParams *params) {

    char *out_buf = NULL;
    char *err_buf = NULL;
    size_t out_total = 0;
    size_t err_total = 0;

    bool out_eof = false;
    bool err_eof = false;

    while (!out_eof || !err_eof) {

        struct pollfd fds[2];
        nfds_t fd_count = 0;

        if (!out_eof && !err_eof) {
            fds[0].fd = params->outpipe;
            fds[0].events = POLLIN | POLLHUP;
            fds[1].fd = params->errpipe;
            fds[1].events = POLLIN | POLLHUP;
            fd_count = 2;
        } else if (!out_eof) {
            fds[0].fd = params->outpipe;
            fds[0].events = POLLIN | POLLHUP;
            fd_count = 1;
        } else {
            fds[0].fd = params->errpipe;
            fds[0].events = POLLIN | POLLHUP;
            fd_count = 1;
        }

        int rv = poll(fds, fd_count, 10000);

        if (rv == -1) {
            if (errno == EINTR) {
                continue;
            }
            engine_perror("planck.shell poll on child stdout/stderr");
            params->res.status = -1;
            goto done;
        } else if (rv == 0) {
            // Timeout
            continue;
        } else {
            if (fds[0].revents & (POLLIN | POLLHUP)) {
                if (fds[0].fd == params->outpipe) {
                    int res = read_child_pipe(params->outpipe, &out_buf, &out_total);
                    if (res == 0) {
                        out_eof = true;
                    }
                } else {
                    int res = read_child_pipe(params->errpipe, &err_buf, &err_total);
                    if (res == 0) {
                        err_eof = true;
                    }
                }
            }

            if (fd_count == 2) {
                if (fds[1].revents & (POLLIN | POLLHUP)) {
                    if (fds[1].fd == params->outpipe) {
                        int res = read_child_pipe(params->outpipe, &out_buf, &out_total);
                        if (res == 0) {
                            out_eof = true;
                        }
                    } else {
                        int res = read_child_pipe(params->errpipe, &err_buf, &err_total);
                        if (res == 0) {
                            err_eof = true;
                        }
                    }
                }
            }
        }
    }

    done:
    params->res.stdout = out_buf ? out_buf : strdup("");
    params->res.stderr = err_buf ? err_buf : strdup("");
}

static struct SystemResult *wait_for_child(struct ThreadParams *params) {

    params->res.status = 0;

    read_child_pipes(params);

    close(params->errpipe);
    close(params->outpipe);
    close(params->inpipe);

    if (params->res.status != -1) {
        if (waitpid(params->pid, &params->res.status, 0) != params->pid) {
            params->res.status = -1;
        } else {
            if (WIFEXITED(params->res.status)) {
                params->res.status = WEXITSTATUS(params->res.status);
            } else if (WIFSIGNALED(params->res.status)) {
                params->res.status = 128 + WTERMSIG(params->res.status);
            } else {
                params->res.status = -1;
            }
        }
    }

    if (params->cb_idx == -1) {
        return &params->res;
    } else {
        JSValueRef args[1];
        args[0] = result_to_object_ref(ctx, &params->res);
        static JSObjectRef translate_async_result_fn = NULL;
        if (!translate_async_result_fn) {
            translate_async_result_fn = get_function("global", "translate_async_result");
            JSValueProtect(ctx, translate_async_result_fn);
        }
        JSObjectRef result = (JSObjectRef) JSObjectCallAsFunction(ctx, translate_async_result_fn, NULL,
                                                                  1, args, NULL);

        args[0] = JSValueMakeNumber(ctx, params->cb_idx);
        static JSObjectRef do_async_sh_callback_fn = NULL;
        if (!do_async_sh_callback_fn) {
            do_async_sh_callback_fn = get_function("global", "do_async_sh_callback");
            JSValueProtect(ctx, do_async_sh_callback_fn);
        }
        JSObjectCallAsFunction(ctx, do_async_sh_callback_fn, result, 1, args, NULL);

        free(params);

        int err = signal_task_complete();
        if (err) {
            engine_print_err_message("shell signal_task_complete", err);
        }

        return NULL;
    }
}

static void *thread_proc(void *params) {
    return (void *) wait_for_child((struct ThreadParams *) params);
}

static JSValueRef system_call(JSContextRef ctx, char **cmd, char **env, char *dir, int cb_idx) {
    struct SystemResult result = {0, NULL, NULL};
    struct SystemResult *res = &result;

    int err_rv;
    int in[2];
    err_rv = pipe(in);
    if (err_rv) {
        engine_perror("planck.shell setting up in pipe");
        return create_shell_result(ctx, EX_OSERR, "", "");
    }
    int out[2];
    err_rv = pipe(out);
    if (err_rv) {
        engine_perror("planck.shell setting up out pipe");
        return create_shell_result(ctx, EX_OSERR, "", "");
    }
    int err[2];
    err_rv = pipe(err);
    if (err_rv) {
        engine_perror("planck.shell setting up err pipe");
        return create_shell_result(ctx, EX_OSERR, "", "");
    }

    pid_t pid;
    pid = fork();
    if (pid == -1) {
        engine_perror("planck.shell forking subprocess");
        return create_shell_result(ctx, EX_OSERR, "", "");
    } else if (pid == 0) {
        if (dir) {
            if (chdir(dir) == -1) {
                exit(1);
            }
        }
        preopen(out[0], STDIN_FILENO);
        preopen(err[1], STDERR_FILENO);
        preopen(in[1], STDOUT_FILENO);
        close(out[1]);
        close(err[0]);
        close(in[0]);
        if (env) {
            extern char **environ;
            environ = env;
        }
        execvp(cmd[0], cmd);
        if (errno == EACCES || errno == EPERM) {
            exit(126);
        } else if (errno == ENOENT) {
            exit(127);
        } else {
            exit(1);
        }
    } else {
        struct ThreadParams *params = NULL;
        if (pid < 0) {
            res->status = -1;
        } else {
            close(out[0]);
            close(err[1]);
            close(in[1]);

            params = malloc(sizeof(struct ThreadParams));
            params->res = result;
            params->errpipe = err[0];
            params->outpipe = in[0];
            params->inpipe = out[1];
            params->pid = pid;
            params->cb_idx = cb_idx;
            if (cb_idx == -1) {
                res = wait_for_child(params);
            } else {
                int err = signal_task_started();
                if (err) {
                    engine_print_err_message("shell signal_task_started", err);
                }

                pthread_t thrd;
                pthread_create(&thrd, NULL, thread_proc, params);
            }
        }

        int i;
        for (i = 0; cmd[i] != NULL; i++) {
            free(cmd[i]);
        }
        free(cmd);
        if (env) {
            int i;
            for (i = 0; env[i] != NULL; i++) {
                free(env[i]);
            }
            free(env);
        }
        free(dir);

        if (cb_idx != -1) {
            return JSValueMakeNull(ctx);
        } else {
            JSValueRef rv = (JSValueRef) result_to_object_ref(ctx, res);
            free(params);
            return rv;
        }
    }
}

JSValueRef function_shellexec(JSContextRef ctx, JSObjectRef function, JSObjectRef this_object,
                              size_t argc, const JSValueRef args[], JSValueRef *exception) {
    if (argc == 7) {
        char **command = cmd(ctx, (JSObjectRef) args[0]);
        if (command) {
            char **environment = NULL;
            if (!JSValueIsNull(ctx, args[4])) {
                environment = env(ctx, (JSObjectRef) args[4]);
            }
            char *dir = NULL;
            if (!JSValueIsNull(ctx, args[5])) {
                dir = value_to_c_string(ctx, args[5]);
            }
            int callback_idx = -1;
            if (!JSValueIsNull(ctx, args[6]) && JSValueIsNumber(ctx, args[6])) {
                callback_idx = (int) JSValueToNumber(ctx, args[6], NULL);
            }
            return system_call(ctx, command, environment, dir, callback_idx);
        }
    }
    return JSValueMakeNull(ctx);
}
