#include <stdio.h>

#include "bundle_inflate.h"

char *bundle_get_contents(char *path) {
    fprintf(stderr, "WARN: no bundled sources, need to run script/bundle-c\n");
    return NULL;
}

#ifdef BUNDLE_TEST
int main(void) {
    fprintf(stderr, "no bundled sources, need to run run script/bundle-c\n");
    return -1;
}
#endif
