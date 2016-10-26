#include <stddef.h>
#include <time.h>
#include <pthread.h>
#include <stdlib.h>
#include "timers.h"

struct timer_data_t {
    long millis;
    timer_callback_t timer_callback;
    void* data;
};

void *timer_thread(void *data) {

    struct timer_data_t *timer_data = data;

    struct timespec t;
    t.tv_sec = timer_data->millis / 1000;
    t.tv_nsec = 1000 * 1000 * (timer_data->millis % 1000);
    nanosleep(&t, NULL);
    timer_data->timer_callback(timer_data->data);

    free(data);

    return NULL;
}

void start_timer(long millis, timer_callback_t timer_callback, void *data) {

    struct timer_data_t *timer_data = malloc(sizeof(struct timer_data_t));
    timer_data->millis = millis;
    timer_data->timer_callback = timer_callback;
    timer_data->data = data;

    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_t thread;
    pthread_create(&thread, &attr, timer_thread, timer_data);
}
