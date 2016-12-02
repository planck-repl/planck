#include <string.h>
#include <stdlib.h>

int str_has_suffix(const char *str, const char *suffix) {
    size_t len = strlen(str);
    size_t suffix_len = strlen(suffix);

    if (len < suffix_len) {
        return -1;
    }

    return strcmp(str + (len - suffix_len), suffix);
}

int str_has_prefix(const char *str, const char *prefix) {
    size_t len = strlen(str);
    size_t prefix_len = strlen(prefix);

    if (len < prefix_len) {
        return -1;
    }

    return strncmp(str, prefix, prefix_len);
}

char *str_concat(const char *s1, const char *s2) {
    size_t l1 = strlen(s1), l2 = strlen(s2);
    size_t len = l1 + l2 + 1;
    char *s = malloc(len * sizeof(char));

    if (s) {
        strncpy(s, s1, l1);
        strncpy(s + l1, s2, l2 + 1);
    }
    return s;
}
