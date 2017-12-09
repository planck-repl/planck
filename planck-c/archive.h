#include <zip.h>

void* open_archive(const char *path, char **error_msg);
void close_archive(void* archive);
char *get_contents_zip(void* archive, const char *name, time_t *last_modified, char **error_msg);
