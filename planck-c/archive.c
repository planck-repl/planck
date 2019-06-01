#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "archive.h"
#include "engine.h"

#ifndef ZIP_RDONLY
typedef struct zip zip_t;
typedef struct zip_stat zip_stat_t;
typedef struct zip_file zip_file_t;
#define ZIP_RDONLY 16
#endif

void format_zip_error(const char *prefix, zip_t *zip, char **error_msg);

void* open_archive(const char *path, char **error_msg) {
    zip_t *archive = zip_open(path, ZIP_RDONLY, NULL);

    if (archive == NULL && error_msg) {
        *error_msg = malloc(1024);
        if (*error_msg) {
            snprintf(*error_msg, 1024, "Could not open %s", path);
        }
        return NULL;
    }

    return archive;
}

void close_archive(void* archive) {
    zip_close(archive);
}

contents_zip_t get_contents_zip(void* archive_p, const char *name, time_t *last_modified, char **error_msg) {

    contents_zip_t rv;
    rv.payload = NULL;
    rv.length = 0;

    zip_t *archive = archive_p;
    
    zip_stat_t stat;
    if (zip_stat(archive, name, 0, &stat) < 0) {
        return rv;
    }

    zip_file_t *f = zip_fopen(archive, name, 0);
    if (f == NULL) {
        if (error_msg) {
            format_zip_error("zip_fopen", archive, error_msg);
        }
        return rv;
    }

    if (last_modified != NULL) {
        *last_modified = stat.mtime;
    }

    uint8_t *buf = malloc(stat.size + 1);
    if (!buf) {
        if (error_msg) {
            *error_msg = strdup("zip malloc");
        }
        goto close_f;
    }

    if (zip_fread(f, buf, stat.size) < 0) {
        if (error_msg) {
            format_zip_error("zip_fread", archive, error_msg);
        }
        goto free_buf;
    }
    // NULL-terminate in case client wants to treat contents as a string
    buf[stat.size] = '\0';

    zip_fclose(f);

    rv.payload = buf;
    rv.length = stat.size;

    return rv;

    free_buf:
    free(buf);

    close_f:
    zip_fclose(f);

    return rv;
}

void format_zip_error(const char *prefix, zip_t *zip, char **error_msg) {
    *error_msg = malloc(1024);
    if (*error_msg) {
        snprintf(*error_msg, 1024, "%s: %s", prefix, zip_strerror(zip));
    }
}

#ifdef ZIP_TEST
int main(int argc, char **argv) {
    if (argc != 3) {
        printf("%s <zip-file> <path>\n", argv[0]);
        return 1;
    }

    char *contents = get_contents_zip(argv[1], argv[2], NULL);
    if (contents == NULL) {
        return 1;
    }

    printf("%s", contents);
    free(contents);

    return 0;
}
#endif
