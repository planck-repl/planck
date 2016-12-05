#include "clock.h"
#include <time.h>

#if __DARWIN_UNIX03

#include <mach/mach.h>
#include <mach/mach_time.h>

#endif

uint64_t system_time() {
#if __DARWIN_UNIX03
    static mach_timebase_info_data_t sTimebaseInfo;
    uint64_t now = mach_absolute_time();
    if (sTimebaseInfo.denom == 0) {
        (void) mach_timebase_info(&sTimebaseInfo);
    }
    return now * sTimebaseInfo.numer / sTimebaseInfo.denom;
#else
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return 1000000000ll * ts.tv_sec + ts.tv_nsec;
#endif
}