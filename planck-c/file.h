#include <JavaScriptCore/JavaScript.h>

uint64_t file_open_read(const char* path, const char* encoding);
uint64_t file_open_write(const char* path,  bool append, const char* encoding);
JSStringRef file_read(uint64_t descriptor);
void file_write(uint64_t descriptor, JSStringRef text);
void file_close(uint64_t descriptor);