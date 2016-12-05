#include "clock.h"
#include "cljs.h"
#include <time.h>

#if __DARWIN_UNIX03

#include <mach/mach.h>
#include <mach/mach_time.h>
#include <stdio.h>

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

static uint64_t launch_time = 0;
static uint64_t last_display = 0;

void init_launch_timing() {
    launch_time = system_time();
    last_display = launch_time;
}

void display_launch_timing(const char *label) {
    if (launch_time) {
        uint64_t now = system_time();
        uint64_t total_elapsed = now - launch_time;
        uint64_t elapsed = now - last_display;
        last_display = now;
        char buffer[1024];
        snprintf(buffer, 1024, "%40s: %10.6f %10.6f", label, 1e-6 * elapsed, 1e-6 * total_elapsed);
        cljs_print_message(buffer);
    }
}
