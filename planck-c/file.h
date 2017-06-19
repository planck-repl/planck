#include <JavaScriptCore/JavaScript.h>

typedef unsigned long descriptor_t;

descriptor_t ufile_open_read(const char *path, const char *encoding);

descriptor_t ufile_open_write(const char *path, bool append, const char *encoding);

JSStringRef ufile_read(descriptor_t descriptor);

void ufile_write(descriptor_t descriptor, JSStringRef text);

void ufile_flush(descriptor_t descriptor);

void ufile_close(descriptor_t descriptor);

descriptor_t file_open_read(const char *path);

descriptor_t file_open_write(const char *path, bool append);

size_t file_read(descriptor_t descriptor, size_t buf_size, uint8_t *buffer);

void file_write(descriptor_t descriptor, size_t buf_size, uint8_t *buffer);

void file_flush(descriptor_t descriptor);

void file_close(descriptor_t descriptor);
