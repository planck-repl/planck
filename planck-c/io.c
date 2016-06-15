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

	int offset = 0;
	for (;;) {
		if (len - offset < CHUNK_SIZE) {
			len = 2 * len + CHUNK_SIZE;
			buf = realloc(buf, len * sizeof(char));
		}
		int n = fread(buf + offset, 1, CHUNK_SIZE, f);
		offset += n;
		if (feof(f)) {
			break;
		}
		if (ferror(f)) {
			// fprintf(stderr, "Error: %s: %s\n", __func__, strerror(errno));
			return NULL;
		}
	}
	memset(buf + offset, 0, len - offset);
	return buf;
}

char *get_contents(char *path, time_t *last_modified) {
/*#ifdef DEBUG
	printf("get_contents(\"%s\")\n", path);
#endif*/

	char *err_prefix;

	FILE *f = fopen(path, "r");
	if (f == NULL) {
		err_prefix = "fopen";
		goto err;
	}

	struct stat f_stat;
	if (fstat(fileno(f), &f_stat) < 0) {
		err_prefix = "fstat";
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
		err_prefix = "fread";
		free(buf);
		goto err;
	}

	if (fclose(f) < 0) {
		err_prefix = "fclose";
		goto err;
	}

	return buf;

err:
	//printf("get_contents(\"%s\"): %s: %s\n", path, err_prefix, strerror(errno));
	return NULL;
}

void write_contents(char *path, char *contents) {
	char *err_prefix;

	FILE *f = fopen(path, "w");
	if (f == NULL) {
		err_prefix = "fopen";
		goto err;
	}

	int len = strlen(contents);
	int offset = 0;
	do {
		int res = fwrite(contents+offset, 1, len-offset, f);
		if (res < 0) {
			err_prefix = "fwrite";
			goto err;
		}
		offset += res;
	} while (offset < len);

	if (fclose(f) < 0) {
		err_prefix = "fclose";
		goto err;
	}

	return;

err:
	// printf("write_contents(\"%s\", ...): %s: %s\n", path, err_prefix, strerror(errno));
	return;
}

int mkdir_p(char *path) {
	int res = mkdir(path, 0755);
	if (res < 0 && errno == EEXIST) {
		return 0;
	}
	return res;
}