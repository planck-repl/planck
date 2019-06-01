#include <zip.h>

typedef struct contents_zip {
    uint8_t * payload;
    size_t length;
} contents_zip_t;

void* open_archive(const char *path, char **error_msg);
void close_archive(void* archive);
contents_zip_t get_contents_zip(void* archive, const char *name, time_t *last_modified, char **error_msg);
