#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include <JavaScriptCore/JavaScript.h>

#include <curl/curl.h>

#include "jsc_utils.h"

struct header_state {
    JSContextRef ctx;
    JSObjectRef headers;
};

size_t header_to_object_callback(char *buffer, size_t size, size_t nitems, void *userdata) {
    struct header_state *state = (struct header_state *) userdata;

    // printf("'%s'\n", buffer);

    int key_end = -1;
    size_t val_end = size * nitems;
    for (size_t i = 0; i < size * nitems; i++) {
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
    char val[val_len];
    strncpy(val, buffer + val_start, val_end - val_start);
    val[val_len] = '\0';
    JSStringRef val_str = JSStringCreateWithUTF8CString(val);
    JSValueRef val_ref = JSValueMakeString(state->ctx, val_str);

    JSObjectSetProperty(state->ctx, state->headers, key_str, val_ref, kJSPropertyAttributeReadOnly, NULL);

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

        struct curl_slist *headers = NULL;
        if (!JSValueIsNull(ctx, headers_obj)) {
            JSPropertyNameArrayRef properties = JSObjectCopyPropertyNames(ctx, headers_obj);
            size_t n = JSPropertyNameArrayGetCount(properties);
            for (int i = 0; i < n; i++) {
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
        header_state.ctx = ctx;
        header_state.headers = response_headers;
        curl_easy_setopt(handle, CURLOPT_HEADERDATA, &header_state);
        curl_easy_setopt(handle, CURLOPT_HEADERFUNCTION, header_to_object_callback);

        struct write_state body_state;
        body_state.offset = 0;
        body_state.length = 0;
        body_state.data = NULL;
        curl_easy_setopt(handle, CURLOPT_WRITEDATA, &body_state);
        curl_easy_setopt(handle, CURLOPT_WRITEFUNCTION, write_string_callback);

        JSObjectRef result = JSObjectMake(ctx, NULL, NULL);

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
            JSStringRef body_str = JSStringCreateWithUTF8CString(body_state.data);
            JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("body"), JSValueMakeString(ctx, body_str),
                                kJSPropertyAttributeReadOnly, NULL);
            free(body_state.data);
        }

        JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("status"), JSValueMakeNumber(ctx, status),
                            kJSPropertyAttributeReadOnly, NULL);
        JSObjectSetProperty(ctx, result, JSStringCreateWithUTF8CString("headers"), response_headers,
                            kJSPropertyAttributeReadOnly, NULL);

        curl_slist_free_all(headers);
        curl_easy_cleanup(handle);

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