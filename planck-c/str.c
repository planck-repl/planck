#include <string.h>
#include <stdlib.h>

int str_has_suffix(char *str, char *suffix) {
	int len = strlen(str);
	int suffix_len = strlen(suffix);

	if (len < suffix_len) {
		return -1;
	}

	return strcmp(str + (len-suffix_len), suffix);
}

int str_has_prefix(char *str, char *prefix) {
	int len = strlen(str);
	int prefix_len = strlen(prefix);

	if (len < prefix_len) {
		return -1;
	}

	return strncmp(str, prefix, prefix_len);
}

char *str_concat(char *s1, char *s2) {
	int l1 = strlen(s1), l2 = strlen(s2);
	int len = l1 + l2 + 1;
	char *s = malloc(len * sizeof(char));
	memset(s, 0, len);

	strncpy(s, s1, l1);
	strncpy(s+l1, s2, l2);
	return s;
}
