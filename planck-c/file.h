#include <JavaScriptCore/JavaScript.h>

uint64_t ufile_open_read(const char *path, const char *encoding);

uint64_t ufile_open_write(const char *path, bool append, const char *encoding);

JSStringRef ufile_read(uint64_t descriptor);

void ufile_write(uint64_t descriptor, JSStringRef text);

void ufile_close(uint64_t descriptor);

uint64_t file_open_read(const char *path);

uint64_t file_open_write(const char *path, bool append);

size_t file_read(uint64_t descriptor, size_t buf_size, uint8_t *buffer);

void file_write(uint64_t descriptor, size_t buf_size, uint8_t *buffer);

void file_close(uint64_t descriptor);