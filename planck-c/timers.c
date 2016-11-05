#include <stddef.h>
#include <time.h>
#include <pthread.h>
#include <stdlib.h>
#include "timers.h"

int timers_outstanding = 0;
pthread_mutex_t timers_complete_lock = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t timers_complete_cond = PTHREAD_COND_INITIALIZER;

void block_until_timers_complete() {
    pthread_mutex_lock(&timers_complete_lock);
    while (timers_outstanding) {
        pthread_cond_wait(&timers_complete_cond, &timers_complete_lock);
    }
    pthread_mutex_unlock(&timers_complete_lock);
}

void signal_timer_started() {
    pthread_mutex_lock(&timers_complete_lock);
    timers_outstanding++;
    pthread_cond_signal(&timers_complete_cond);
    pthread_mutex_unlock(&timers_complete_lock);
}

void signal_timer_complete() {
    pthread_mutex_lock(&timers_complete_lock);
    timers_outstanding--;
    pthread_cond_signal(&timers_complete_cond);
    pthread_mutex_unlock(&timers_complete_lock);
}

struct timer_data_t {
    long millis;
    timer_callback_t timer_callback;
    void *data;
};

void *timer_thread(void *data) {

    struct timer_data_t *timer_data = data;

    struct timespec t;
    t.tv_sec = timer_data->millis / 1000;
    t.tv_nsec = 1000 * 1000 * (timer_data->millis % 1000);
    if (t.tv_sec == 0 && t.tv_nsec == 0) {
        t.tv_nsec = 1; /* Evidently needed on Ubuntu 14.04 */
    }
    nanosleep(&t, NULL);
    timer_data->timer_callback(timer_data->data);

    free(data);

    signal_timer_complete();

    return NULL;
}

void start_timer(long millis, timer_callback_t timer_callback, void *data) {

    signal_timer_started();

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
