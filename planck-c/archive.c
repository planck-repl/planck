// Utilities for getting entries from ZIP files.
// 
// Uses libzip, alternatives are minizip (from zlib) and zziplib.

#include <stdio.h>
#include <stdlib.h>

#include "archive.h"

#ifndef ZIP_RDONLY
typedef struct zip zip_t;
typedef struct zip_stat zip_stat_t;
typedef struct zip_file zip_file_t;
#define ZIP_RDONLY 16
#endif

void print_zip_err(char *prefix, zip_t *zip);

char *get_contents_zip(char *path, char *name, time_t *last_modified) {
    zip_t *archive = zip_open(path, ZIP_RDONLY, NULL);
    if (archive == NULL) {
        print_zip_err("zip_open", archive);
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
    zip_fclose(f);
    close_archive:
    zip_close(archive);

    return NULL;
}

void print_zip_err(char *prefix, zip_t *zip) {
    printf("%s: %s\n", prefix, zip_strerror(zip));
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
