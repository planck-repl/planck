#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

#include <JavaScriptCore/JavaScript.h>

#include <curl/curl.h>

#include "engine.h"
#include "jsc_utils.h"

#ifndef CURL_VERSION_UNIX_SOCKETS
#define CURL_VERSION_UNIX_SOCKETS 0
#define CURLOPT_UNIX_SOCKET_PATH 0
#endif

struct header_state {
    JSObjectRef *headers;
};

int curl_has_feature(int feature_const) {
  curl_version_info_data *data = curl_version_info(CURLVERSION_NOW);
  return data->features & feature_const;
}

size_t header_to_object_callback(char *buffer, size_t size, size_t nitems, void *userdata) {
    struct header_state *state = (struct header_state *) userdata;

    // printf("'%s'\n", buffer);

    int key_end = -1;
    size_t val_end = size * nitems;
    size_t i;
    for (i = 0; i < size * nitems; i++) {
        if (buffer[i] == ':' && key_end == -1) {
            key_end = i;
        }

        if (buffer[i] == '\r' && buffer[i + 1] == '\n') {
            val_end = i;
        }
    }
    if (key_end == -1) { // likely empty?
        return size * nitems;
    }

    char key[key_end + 1];
    strncpy(key, buffer, key_end);
    key[key_end] = '\0';

    JSStringRef key_str = JSStringCreateWithUTF8CString(key);

    int val_start = key_end + 2;
    size_t val_len = val_end - val_start;
    char val[val_len + 1];
    strncpy(val, buffer + val_start, val_len);
    val[val_len] = '\0';
    JSStringRef val_str = JSStringCreateWithUTF8CString(val);
    JSValueRef val_ref = JSValueMakeString(ctx, val_str);

    JSObjectSetProperty(ctx, *state->headers, key_str, val_ref, kJSPropertyAttributeReadOnly, NULL);

    return size * nitems;
}

struct write_state {
    int offset;
    int length;
    char *data;
};

size_t write_string_callback(char *buffer, size_t size, size_t nmemb, void
*userdata) {
    struct write_state *state = (struct write_state *) userdata;

    if (state->length - state->offset < size * nmemb) {
        int new_length = state->length * 2 + CURL_MAX_WRITE_SIZE;
        if (state->length == 0) {
            new_length += 1; // nul-byte
        }
        state->data = realloc(state->data, new_length);
        state->length = new_length;
    }

    memcpy(state->data + state->offset, buffer, size * nmemb);
    state->offset += size * nmemb;
    state->data[state->offset] = '\0';

    return size * nmemb;
}

// Turn off optimization for this function. See https://github.com/mfikes/planck/issues/503
JSValueRef function_http_request(JSContextRef ctx, JSObjectRef function, JSObjectRef this_object,
                                 size_t argc, const JSValueRef args[], JSValueRef *exception) __attribute__ ((
#if defined(__clang__)
optnone
#elif defined(__GNUC__)
optimize("-O0")
#endif
));

JSValueRef function_http_request(JSContextRef ctx, JSObjectRef function, JSObjectRef this_object,
                                 size_t argc, const JSValueRef args[], JSValueRef *exception) {
    if (argc == 1 && JSValueGetType(ctx, args[0]) == kJSTypeObject) {
        JSObjectRef opts = JSValueToObject(ctx, args[0], NULL);
        JSValueRef url_ref = JSObjectGetProperty(ctx, opts, JSStringCreateWithUTF8CString("url"), NULL);
        char *url = value_to_c_string(ctx, url_ref);
        JSValueRef timeout_ref = JSObjectGetProperty(ctx, opts, JSStringCreateWithUTF8CString("timeout"), NULL);
        time_t timeout = 0;
        if (JSValueIsNumber(ctx, timeout_ref)) {
            timeout = (time_t) JSValueToNumber(ctx, timeout_ref, NULL);
        }
        JSValueRef binary_response_ref = JSObjectGetProperty(ctx, opts, JSStringCreateWithUTF8CString("binary-response"), NULL);
        bool binary_response = false;
        if (JSValueIsBoolean(ctx, binary_response_ref)) {
            binary_response = JSValueToBoolean(ctx, binary_response_ref);
        }
        JSValueRef method_ref = JSObjectGetProperty(ctx, opts, JSStringCreateWithUTF8CString("method"), NULL);
        char *method = value_to_c_string(ctx, method_ref);
        JSValueRef body_ref = JSObjectGetProperty(ctx, opts, JSStringCreateWithUTF8CString("body"), NULL);

        JSObjectRef headers_obj = JSValueToObject(ctx, JSObjectGetProperty(ctx, opts,
                                                                           JSStringCreateWithUTF8CString("headers"),
                                                                           NULL), NULL);

        CURL *handle = curl_easy_init();
        assert(handle != NULL);

        curl_easy_setopt(handle, CURLOPT_CUSTOMREQUEST, method);
        curl_easy_setopt(handle, CURLOPT_URL, url);

        JSObjectRef result = JSObjectMake(ctx, NULL, NULL);
        JSValueProtect(ctx, result);

        JSValueRef insecure_ref = JSObjectGetProperty(ctx, opts, JSStringCreateWithUTF8CString("insecure"), NULL);
        bool insecure = false;
        if(JSValueIsBoolean(ctx, insecure_ref)) {
            insecure = JSValueToBoolean(ctx, insecure_ref);
        }

        if(insecure) {
            curl_easy_setopt(handle, CURLOPT_SSL_VERIFYPEER, 0L);
            curl_easy_setopt(handle, CURLOPT_SSL_VERIFYHOST, 0L);
        }

        char *socket = NULL;
        JSValueRef socket_ref = JSObjectGetProperty(ctx, opts, JSStringCreateWithUTF8CString("socket"), NULL);
        if (!JSValueIsUndefined(ctx, socket_ref)) {
          if (curl_has_feature(CURL_VERSION_UNIX_SOCKETS)) {
            socket = value_to_c_string(ctx, socket_ref);
            curl_easy_setopt(handle, CURLOPT_UNIX_SOCKET_PATH, socket);
          } else {
            JSStringRef error_str = JSStringCreateWithUTF8CString("This version of libcurl does not support UNIX sockets.");
            JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("error"), JSValueMakeString(ctx, error_str),
                                kJSPropertyAttributeReadOnly, NULL);
            JSValueUnprotect(ctx, result);
            return result;
          }
        }
        free(socket);

        struct curl_slist *headers = NULL;
        if (!JSValueIsNull(ctx, headers_obj)) {
            JSPropertyNameArrayRef properties = JSObjectCopyPropertyNames(ctx, headers_obj);
            size_t n = JSPropertyNameArrayGetCount(properties);
            int i;
            for (i = 0; i < n; i++) {
                JSStringRef key_str = JSPropertyNameArrayGetNameAtIndex(properties, i);
                JSValueRef val_ref = JSObjectGetProperty(ctx, headers_obj, key_str, NULL);

                size_t len = JSStringGetLength(key_str) + 1;
                char *key = malloc(len * sizeof(char));
                JSStringGetUTF8CString(key_str, key, len);
                JSStringRef val_as_str = to_string(ctx, val_ref);
                char *val = value_to_c_string(ctx, JSValueMakeString(ctx, val_as_str));
                JSStringRelease(val_as_str);

                size_t len_key = strlen(key);
                size_t len_val = strlen(val);
                char *header = malloc((len_key + len_val + 2 + 1) * sizeof(char));
                sprintf(header, "%s: %s", key, val);
                headers = curl_slist_append(headers, header);
                free(header);

                free(key);
                free(val);
            }

            curl_easy_setopt(handle, CURLOPT_HTTPHEADER, headers);
        }

        curl_easy_setopt(handle, CURLOPT_TIMEOUT, timeout);

        char *body = NULL;
        if (!JSValueIsUndefined(ctx, body_ref)) {
            body = value_to_c_string(ctx, body_ref);
            curl_easy_setopt(handle, CURLOPT_POSTFIELDS, body);
        }

        JSObjectRef response_headers = JSObjectMake(ctx, NULL, NULL);
        struct header_state header_state;
        header_state.headers = &response_headers;
        curl_easy_setopt(handle, CURLOPT_HEADERDATA, &header_state);
        curl_easy_setopt(handle, CURLOPT_HEADERFUNCTION, header_to_object_callback);

        struct write_state body_state;
        body_state.offset = 0;
        body_state.length = 0;
        body_state.data = NULL;
        curl_easy_setopt(handle, CURLOPT_WRITEDATA, &body_state);
        curl_easy_setopt(handle, CURLOPT_WRITEFUNCTION, write_string_callback);

        int res = curl_easy_perform(handle);
        if (res != 0) {
            JSStringRef error_str = JSStringCreateWithUTF8CString(curl_easy_strerror(res));
            JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("error"), JSValueMakeString(ctx, error_str),
                                kJSPropertyAttributeReadOnly, NULL);
        }

        int status = 0;
        curl_easy_getinfo(handle, CURLINFO_RESPONSE_CODE, &status);

        free(body);

        // printf("%d bytes, %x\n", body_state.offset, body_state.data);
        if (body_state.data != NULL) {
            if (binary_response) {
                JSValueRef* bytes = malloc(sizeof(JSValueRef)*body_state.length);
                int i;
                for (i = 0; i < body_state.length; i++) {
                    bytes[i] = JSValueMakeNumber(ctx, (uint8_t )body_state.data[i]);
                }
                JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("body"),
                                    JSObjectMakeArray(ctx, body_state.length, bytes, NULL),
                                    kJSPropertyAttributeReadOnly, NULL);
                free(bytes);
            } else {
                JSStringRef body_str = JSStringCreateWithUTF8CString(body_state.data);
                JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("body"),
                                    JSValueMakeString(ctx, body_str),
                                    kJSPropertyAttributeReadOnly, NULL);
            }
            free(body_state.data);
        }

        JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("status"), JSValueMakeNumber(ctx, status),
                            kJSPropertyAttributeReadOnly, NULL);
        JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("headers"), response_headers,
                            kJSPropertyAttributeReadOnly, NULL);

        curl_slist_free_all(headers);
        curl_easy_cleanup(handle);

        JSValueUnprotect(ctx, result);
        return result;
    }

    return JSValueMakeNull(ctx);
}

#ifdef HTTP_TEST
int main(int argc, char **argv) {
    CURL *curl = curl_easy_init();
    if(curl) {
        curl_easy_setopt(curl, CURLOPT_URL, "http://planck-repl.org");
        curl_easy_setopt(curl, CURLOPT_HEADER, 1L);
        curl_easy_perform(curl);
    }
}
#endif
