#include <stdlib.h>
#include <search.h>
#include <JavaScriptCore/JavaScript.h>
#include "unicode/ustdio.h"

uint64_t ufile_to_descriptor(UFILE* ufile) {
    return (uint64_t)ufile;
}

UFILE* descriptor_to_ufile(uint64_t descriptor) {
    return (UFILE*)descriptor;
}

uint64_t file_open(const char* path, const char* encoding, const char* mode) {
    return ufile_to_descriptor(u_fopen(path, mode, NULL, encoding));
}

uint64_t file_open_read(const char* path, const char* encoding) {
    return file_open(path, encoding, "r");
}

uint64_t file_open_write(const char* path,  bool append, const char* encoding) {
    return file_open(path, encoding, (append ? "a" : "w"));
}

JSStringRef file_read(uint64_t descriptor) {
    UFILE* ufile = descriptor_to_ufile(descriptor);
    JSStringRef rv = NULL;
    void* buffer = malloc(sizeof(uint16_t) * 1024);
    int32_t read = u_file_read(buffer, 1024, ufile);
    if (read > 0) {
        rv = JSStringCreateWithCharacters(buffer, (size_t)read);
    }
    free(buffer);
    return rv;
}

void file_write(uint64_t descriptor, JSStringRef text) {
    UFILE* ufile = descriptor_to_ufile(descriptor);
    u_file_write(JSStringGetCharactersPtr(text), (uint32_t)JSStringGetLength(text), ufile);
}

void file_close(uint64_t descriptor) {
    UFILE* ufile = descriptor_to_ufile(descriptor);
    u_fclose(ufile);
}



