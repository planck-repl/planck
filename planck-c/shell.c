#include <assert.h>
#include <pthread.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <sys/wait.h>
#include <JavaScriptCore/JavaScript.h>
#include "cljs.h"
#include "jsc_utils.h"
#include "repl.h"

static int JSArrayGetCount(JSContextRef ctx, JSObjectRef arr)
{
  JSStringRef pname = JSStringCreateWithUTF8CString("length");
  JSValueRef val = JSObjectGetProperty(ctx, arr, pname, NULL);
  JSStringRelease(pname);
  return JSValueToNumber(ctx, val, NULL);
}

#define JSArrayGetValueAtIndex(ctx, array, i) JSObjectGetPropertyAtIndex(ctx, array, i, NULL)

static char** cmd(JSContextRef ctx, const JSObjectRef array) {
  int argc = JSArrayGetCount(ctx, array);
  char** result = NULL;
  if (argc > 0) {
    result = malloc(sizeof(char*) * (argc + 1));
    for (int i = 0; i < argc; i++) {
      JSValueRef val = JSArrayGetValueAtIndex(ctx, array, i);
      if (JSValueGetType(ctx, val) != kJSTypeString) {
        for (int j = 0; j < i; j++)
          free(result[j]);
        free(result);
        return NULL;
      }
      result[i] = value_to_c_string(ctx, val);
    }
    result[argc] = 0;
  }
  return result;
}

static char** env(JSContextRef ctx, const JSObjectRef map) {
  int argc = JSArrayGetCount(ctx, map);
  char** result = NULL;
  if (argc > 0) {
    result = malloc(sizeof(char*) * (argc + 1));
    for (int i = 0; i < argc; i++) {
      JSObjectRef keyVal = (JSObjectRef) JSArrayGetValueAtIndex(ctx, map, i);
      char* key = value_to_c_string(ctx, JSArrayGetValueAtIndex(ctx, keyVal, 0));
      char* value = value_to_c_string(ctx, JSArrayGetValueAtIndex(ctx, keyVal, 1));
      int len = strlen(key) + strlen(value) + 2;
      char* combined = malloc(len);
      combined[0] = 0;
      snprintf(combined, len, "%s=%s", key, value);
      free(key);
      free(value);
      result[i] = combined;
#ifdef SHELLDBG
      printf("env[%d]: %s\n", i, combined);
#endif
    }
    result[argc] = 0;
  }
  return result;
}

static void preopen(int old, int new) {
  dup2(old, new);
  close(old);
}

static char* read_child_pipe(int pipe) {
  const int BLOCK_SIZE = 1024;
  int block_count = 1;
  char* res = malloc(BLOCK_SIZE * block_count);
  int count = 0, total = 0, num_to_read = BLOCK_SIZE - 1;
  while ((count = read(pipe, res + total, num_to_read)) > 0) {
    total += count;
    if (count < num_to_read) num_to_read -= count;
    else {
      block_count += 1;
      res = realloc(res, BLOCK_SIZE * block_count);
      num_to_read = BLOCK_SIZE;
    }
  }
  res[total] = 0;
  return res;
}

struct SystemResult {
  int status;
  char* stdout;
  char* stderr;
};

static JSObjectRef result_to_object_ref(JSContextRef ctx, struct SystemResult* result) {
  JSValueRef arguments[3];
  arguments[0] = JSValueMakeNumber(ctx, result->status);
  arguments[1] = c_string_to_value(ctx, result->stdout);
  arguments[2] = c_string_to_value(ctx, result->stderr);

#ifdef SHELLDBG
  printf("stdout: %s\n", result->stdout);
  printf("stderr: %s\n", result->stderr);
#endif

  free(result->stdout);
  free(result->stderr);

  return JSObjectMakeArray(ctx, 3, arguments, NULL);
}

struct ThreadParams {
  struct SystemResult res;
  int errpipe;
  int outpipe;
  pid_t pid;
  int cb_idx;
};

static struct SystemResult* wait_for_child(struct ThreadParams* params) {
  if (waitpid(params->pid, &params->res.status, 0) != params->pid) params->res.status = -1;
  else {
    if (WIFEXITED(params->res.status)) {
      params->res.status = WEXITSTATUS(params->res.status);
    } else if (WIFSIGNALED(params->res.status)) {
      params->res.status = 128 + WTERMSIG(params->res.status);
    } else {
      params->res.status = -1;
    }
    params->res.stderr = read_child_pipe(params->errpipe);
    params->res.stdout = read_child_pipe(params->outpipe);
  }
  if (params->cb_idx == -1) return &params->res;
  else {
    JSValueRef args[1];
    args[0] = result_to_object_ref(global_ctx, &params->res);
    JSObjectRef translateResult = get_function(global_ctx, "global", "translate_async_result");
    JSObjectRef result = (JSObjectRef)JSObjectCallAsFunction(global_ctx, translateResult, NULL,
                                                             1, args, NULL);

    args[0] = JSValueMakeNumber(global_ctx, params->cb_idx);
    JSObjectCallAsFunction(global_ctx, get_function(global_ctx, "global", "do_async_sh_callback"),
                           result, 1, args, NULL);

    free(params);
    return NULL;
  }
}

static void* thread_proc(void* params) {
  return (void*)wait_for_child((struct ThreadParams*) params);
}

static JSValueRef system_call(JSContextRef ctx, char** cmd, char** env, char* dir, int cb_idx) {
  struct SystemResult result = {0};
  struct SystemResult* res = &result;

  int in[2]; pipe(in);
  int out[2]; pipe(out);
  int err[2]; pipe(err);

  pid_t pid;
  pid = fork();
  if (pid == 0) {
    if (dir) chdir(dir);
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
    if (pid < 0) res->status = -1;
    else {
      close(out[0]);
      close(err[1]);
      close(in[1]);

      struct ThreadParams tp;
      struct ThreadParams* params = &tp;
      if (cb_idx != -1) {
        params = malloc(sizeof(struct ThreadParams));
      }
      params->res = result;
      params->errpipe = err[0];
      params->outpipe = in[0];
      params->pid = pid;
      params->cb_idx = cb_idx;
      if (cb_idx == -1) res = wait_for_child(params);
      else {
        pthread_t thrd;
        pthread_create(&thrd, NULL, thread_proc, params);
      }
    }

    close(out[1]);
    close(err[0]);
    close(in[0]);

    for (int i = 0; cmd[i] != NULL; i++)
      free(cmd[i]);
    free(cmd);
    if (env) {
      for (int i = 0; env[i] != NULL; i++)
        free(env[i]);
      free(env);
    }
    free(dir);

    if (cb_idx != -1) return JSValueMakeNull(ctx);
    else return (JSValueRef)result_to_object_ref(ctx, res);
  }
  return JSValueMakeNull(ctx);
}

JSValueRef function_shellexec(JSContextRef ctx, JSObjectRef function, JSObjectRef this_object,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
  if (argc == 7) {
    char** command = cmd(ctx, (JSObjectRef) args[0]);
    if (command) {
#ifdef SHELLDBG
      printf("cmd: [");
      for (int i = 0; command[i] != NULL; i++)
        printf("%s\"%s\"", i > 0 ? ", " : "", command[i]);
      printf("]\n");
#endif
      char** environment = NULL;
      if (!JSValueIsNull(ctx, args[4])) {
        environment = env(ctx, (JSObjectRef) args[4]);
      }
      char* dir = NULL;
      if (!JSValueIsNull(ctx, args[5])) {
        dir = value_to_c_string(ctx, args[5]);
#ifdef SHELLDBG
        printf("dir: %s\n", dir);
#endif
      }
      int callback_idx = -1;
      if (!JSValueIsNull(ctx, args[6]) && JSValueIsNumber(ctx, args[6])) {
        callback_idx = JSValueToNumber(ctx, args[6], NULL);
      }
      return system_call(ctx, command, environment, dir, callback_idx);
    }
  }
  return JSValueMakeNull(ctx);
}
