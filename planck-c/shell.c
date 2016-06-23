#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <JavaScriptCore/JavaScript.h>
#include "jsc_utils.h"

static int JSArrayGetCount(JSContextRef ctx, JSObjectRef arr)
{
  JSStringRef pname = JSStringCreateWithUTF8CString("length");
  JSValueRef val = JSObjectGetProperty(ctx, arr, pname, NULL);
  JSStringRelease(pname);
  return JSValueToNumber(ctx, val, NULL);
}

#define JSArrayGetValueAtIndex(ctx, array, i) JSObjectGetPropertyAtIndex(ctx, array, i, NULL)

static char* cmd(JSContextRef ctx, const JSObjectRef array) {
  int argc = JSArrayGetCount(ctx, array);
  char** args = malloc(sizeof(char*) * argc);
  int total_size = 0;
  for (int i = 0; i < argc; i++) {
    JSValueRef val = JSArrayGetValueAtIndex(ctx, array, i);
    if (JSValueGetType(ctx, val) != kJSTypeString) return NULL;
    args[i] = value_to_c_string(ctx, val);
    total_size += strlen(args[i]) + 1;
  }
  char* result = malloc(total_size + 1 + argc);
  result[0] = 0;
  for (int i = 0; i < argc; i++) {
    strcat(result, args[i]);
    strcat(result, " ");
    free(args[i]);
  }
  free(args);
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
#ifdef DEBUG
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
  int count = 0;
  int total = 0;
  do {
    count = read(pipe, res + total, BLOCK_SIZE);
    if (count > 0) {
      total += count;
      block_count += 1;
      res = realloc(res, BLOCK_SIZE * block_count);
    }
  } while (count == BLOCK_SIZE);
  res[total] = 0;
  return res;
}

struct SystemResult {
  int status;
  char* stdout;
  char* stderr;
};

struct SystemResult system_call(char* cmd, char** env, char* dir) {
  struct SystemResult res = {0};

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
    char* argv[] = { "/bin/sh", "-c", cmd, 0 };
    execve(argv[0], &argv[0], env);
    _exit(EXIT_FAILURE);
  } else if (pid < 0) {
    res.status = -1;
  } else {
    close(out[0]);
    close(err[1]);
    close(in[1]);
    if (waitpid(pid, &res.status, 0) != pid) res.status = -1;
    else {
      res.stderr = read_child_pipe(err[0]);
      res.stdout = read_child_pipe(in[0]);
    }
  }
  return res;
}

JSValueRef function_shellexec(JSContextRef ctx, JSObjectRef function, JSObjectRef this_object,
		size_t argc, const JSValueRef args[], JSValueRef* exception) {
  if (argc == 6) {
    char* joined = cmd(ctx, (JSObjectRef) args[0]);
    if (joined) {
#ifdef DEBUG
      printf("cmd: %s\n", joined);
#endif
      char** environment = NULL;
      if (!JSValueIsNull(ctx, args[4])) {
        environment = env(ctx, (JSObjectRef) args[4]);
      }
      char* dir = NULL;
      if (!JSValueIsNull(ctx, args[5])) {
        dir = value_to_c_string(ctx, args[5]);
#ifdef DEBUG
        printf("dir: %s", dir);
#endif
      }
      struct SystemResult result = system_call(joined, environment, dir);
#ifdef DEBUG
      printf("stdout: %s", result.stdout);
      printf("stderr: %s", result.stderr);
#endif

      JSValueRef arguments[3];
      arguments[0] = JSValueMakeNumber(ctx, result.status);
      arguments[1] = c_string_to_value(ctx, result.stdout);
      arguments[2] = c_string_to_value(ctx, result.stderr);

      free(result.stdout);
      free(result.stderr);
      free(dir);
      free(environment);
      free(joined);

      return JSObjectMakeArray(ctx, 3, arguments, NULL);
    }
  }
  return JSValueMakeNull(ctx);
}

