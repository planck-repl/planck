#include <stdio.h>
#include <stdlib.h>

#include "archive.h"
#include "engine.h"

#ifndef ZIP_RDONLY
typedef struct zip zip_t;
typedef struct zip_stat zip_stat_t;
typedef struct zip_file zip_file_t;
#define ZIP_RDONLY 16
#endif

void print_zip_err(const char *prefix, zip_t *zip);

char *get_contents_zip(const char *path, const char *name, time_t *last_modified) {

    zip_t *archive = zip_open(path, ZIP_RDONLY, NULL);

    if (archive == NULL) {
        char buffer[1024];
        snprintf(buffer, 1024, "Could not open %s\n", path);
        engine_print_message(buffer);
        return NULL;
    }

    zip_stat_t stat;
    if (zip_stat(archive, name, 0, &stat) < 0) {
        goto close_archive;
    }

    zip_file_t *f = zip_fopen(archive, name, 0);
    if (f == NULL) {
        print_zip_err("zip_fopen", archive);
        goto close_archive;
    }

    if (last_modified != NULL) {
        *last_modified = stat.mtime;
    }

    char *buf = malloc(stat.size + 1);
    if (!buf) {
        engine_print_message("zip malloc");
        goto close_f;
    }

    if (zip_fread(f, buf, stat.size) < 0) {
        print_zip_err("zip_fread", archive);
        goto free_buf;
    }
    buf[stat.size] = '\0';

    zip_fclose(f);
    zip_close(archive);

    return buf;

    free_buf:
    free(buf);

    close_f:
    zip_fclose(f);

    close_archive:
    zip_close(archive);

    return NULL;
}

void print_zip_err(const char *prefix, zip_t *zip) {
    char buffer[1024];
    snprintf(buffer, 1024, "%s: %s\n", prefix, zip_strerror(zip));
    engine_print_message(buffer);
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
