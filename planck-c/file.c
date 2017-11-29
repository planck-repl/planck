#include <stdlib.h>
#include <search.h>
#include <JavaScriptCore/JavaScript.h>
#include "unicode/ustdio.h"
#include "file.h"

descriptor_t ufile_to_descriptor(UFILE *ufile) {
    return (descriptor_t) ufile;
}

UFILE *descriptor_to_ufile(descriptor_t descriptor) {
    return (UFILE *) descriptor;
}

descriptor_t ufile_open(const char *path, const char *encoding, const char *mode) {
    return ufile_to_descriptor(u_fopen(path, mode, NULL, encoding));
}

descriptor_t ufile_open_read(const char *path, const char *encoding) {
    return ufile_open(path, encoding, "r");
}

descriptor_t ufile_open_write(const char *path, bool append, const char *encoding) {
    return ufile_open(path, encoding, (append ? "a" : "w"));
}

JSStringRef ufile_read(descriptor_t descriptor) {
    UFILE *ufile = descriptor_to_ufile(descriptor);
    JSStringRef rv = NULL;
    void *buffer = malloc(sizeof(uint16_t) * 1024);
    int32_t read = u_file_read(buffer, 1024, ufile);
    /* If we've read to the end of the file, clear the EOF indicator
     * so that subsequent read calls will try again. */
    if (u_feof(ufile)) {
        clearerr(u_fgetfile(ufile));
    }
    if (read > 0) {
        rv = JSStringCreateWithCharacters(buffer, (size_t) read);
    }
    free(buffer);
    return rv;
}

void ufile_write(descriptor_t descriptor, JSStringRef text) {
    UFILE *ufile = descriptor_to_ufile(descriptor);
    u_file_write(JSStringGetCharactersPtr(text), (uint32_t) JSStringGetLength(text), ufile);
}

void ufile_flush(descriptor_t descriptor) {
    UFILE *ufile = descriptor_to_ufile(descriptor);
    u_fflush(ufile);
}

void ufile_close(descriptor_t descriptor) {
    UFILE *ufile = descriptor_to_ufile(descriptor);
    u_fclose(ufile);
}

descriptor_t file_to_descriptor(FILE *file) {
    return (descriptor_t) file;
}

FILE *descriptor_to_file(descriptor_t descriptor) {
    return (FILE *) descriptor;
}

descriptor_t file_open(const char *path, const char *mode) {
    return file_to_descriptor(fopen(path, mode));
}

descriptor_t file_open_read(const char *path) {
    return file_open(path, "r");
}

descriptor_t file_open_write(const char *path, bool append) {
    return file_open(path, (append ? "a" : "w"));
}

size_t file_read(descriptor_t descriptor, size_t buf_size, uint8_t *buf) {
    FILE *file = descriptor_to_file(descriptor);
    return fread(buf, sizeof(uint8_t), buf_size, file);
}

void file_write(descriptor_t descriptor, size_t buf_size, uint8_t *buf) {
    FILE *file = descriptor_to_file(descriptor);
    fwrite(buf, sizeof(uint8_t), buf_size, file);
}

void file_flush(descriptor_t descriptor) {
    FILE *file = descriptor_to_file(descriptor);
    fflush(file);
}

void file_close(descriptor_t descriptor) {
    FILE *file = descriptor_to_file(descriptor);
    fclose(file);
}
