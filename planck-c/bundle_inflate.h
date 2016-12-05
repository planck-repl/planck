#include <assert.h>
#include <stdio.h>
#include <stdbool.h>

#include <zlib.h>

int bundle_inflate(char *dest, unsigned char *src, unsigned int src_len, unsigned int len) {
    if (src_len == 0) {
        return 0;
    }

    bool done = false;
    int status;

    z_stream strm;
    strm.next_in = src;
    strm.avail_in = src_len;
    strm.total_out = 0;
    strm.zalloc = Z_NULL;
    strm.zfree = Z_NULL;

    if (inflateInit2(&strm, (15 + 32)) != Z_OK) {
        return -1;
    }

    while (!done) {
        strm.next_out = (unsigned char *) dest + strm.total_out;
        strm.avail_out = len - (int)strm.total_out;

        status = inflate(&strm, Z_SYNC_FLUSH);
        if (status == Z_STREAM_END) {
            done = true;
        } else if (status != Z_OK) {
            break;
        }
    }

    if (inflateEnd(&strm) != Z_OK) {
        return -1;
    }

    return done ? 0 : -1;
}
