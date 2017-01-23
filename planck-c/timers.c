#include <stddef.h>
#include <time.h>
#include <pthread.h>
#include <stdlib.h>
#include "timers.h"
#include "engine.h"
#include "tasks.h"

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

    int err = nanosleep(&t, NULL);
    if (err) {
        free(data);
        engine_perror("timer nanosleep");
        return NULL;
    }

    timer_data->timer_callback(timer_data->data);

    free(data);

    err = signal_task_complete();
    if (err) {
        engine_print_err_message("timer signal_task_complete", err);
    }

    return NULL;
}

int start_timer(long millis, timer_callback_t timer_callback, void *data) {

    int err = signal_task_started();
    if (err) {
        return err;
    }

    struct timer_data_t *timer_data = malloc(sizeof(struct timer_data_t));
    if (!timer_data) return -1;

    timer_data->millis = millis;
    timer_data->timer_callback = timer_callback;
    timer_data->data = data;

    pthread_attr_t attr;
    err = pthread_attr_init(&attr);
    if (err) {
        free(timer_data);
        return err;
    }

    err = pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    if (err) {
        free(timer_data);
        return err;
    }

    pthread_t thread;
    err = pthread_create(&thread, &attr, timer_thread, timer_data);
    if (err) {
        free(timer_data);
    }
    return err;
}
