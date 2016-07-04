#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <sys/stat.h>

#define CHUNK_SIZE 1024

char *read_all(FILE *f) {
	int len = CHUNK_SIZE + 1;
	char *buf = malloc(len * sizeof(char));

	size_t offset = 0;
	for (;;) {
		if (len - offset < CHUNK_SIZE) {
			len = 2 * len + CHUNK_SIZE;
			buf = realloc(buf, len * sizeof(char));
		}
		size_t n = fread(buf + offset, 1, CHUNK_SIZE, f);
		offset += n;
		if (feof(f)) {
			break;
		}
		if (ferror(f)) {
			return NULL;
		}
	}
	memset(buf + offset, 0, len - offset);
	return buf;
}

char *get_contents(char *path, time_t *last_modified) {
	FILE *f = fopen(path, "r");
	if (f == NULL) {
		goto err;
	}

	struct stat f_stat;
	if (fstat(fileno(f), &f_stat) < 0) {
		goto err;
	}

	if (last_modified != NULL) {
		*last_modified = f_stat.st_mtime;
	}

	char *buf = malloc(f_stat.st_size + 1);
	memset(buf, 0, f_stat.st_size);
	fread(buf, f_stat.st_size, 1, f);
	buf[f_stat.st_size] = '\0';
	if (ferror(f)) {
		free(buf);
		goto err;
	}

	if (fclose(f) < 0) {
		goto err;
	}

	return buf;

err:
	return NULL;
}

void write_contents(char *path, char *contents) {
	FILE *f = fopen(path, "w");
	if (f == NULL) {
		return;
	}

	size_t len = strlen(contents);
	int offset = 0;
	do {
		int res = fwrite(contents+offset, 1, len-offset, f);
		if (res < 0) {
			return;
		}
		offset += res;
	} while (offset < len);

	if (fclose(f) < 0) {
		return;
	}

	return;
}

int mkdir_p(char *path) {
	int res = mkdir(path, 0755);
	if (res < 0 && errno == EEXIST) {
		return 0;
	}
	return res;
}