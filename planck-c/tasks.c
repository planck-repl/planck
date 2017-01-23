#include <pthread.h>
#include "tasks.h"

static int tasks_outstanding = 0;
pthread_mutex_t tasks_complete_lock = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t tasks_complete_cond = PTHREAD_COND_INITIALIZER;

int block_until_tasks_complete() {
    int err = pthread_mutex_lock(&tasks_complete_lock);
    if (err) return err;

    while (tasks_outstanding) {
        err = pthread_cond_wait(&tasks_complete_cond, &tasks_complete_lock);
        if (err) {
            pthread_mutex_unlock(&tasks_complete_lock);
            return err;
        }
    }

    return pthread_mutex_unlock(&tasks_complete_lock);
}

int signal_task_started() {
    int err = pthread_mutex_lock(&tasks_complete_lock);
    if (err) return err;

    tasks_outstanding++;

    err = pthread_cond_signal(&tasks_complete_cond);
    if (err) {
        pthread_mutex_unlock(&tasks_complete_lock);
        return err;
    }

    return pthread_mutex_unlock(&tasks_complete_lock);
}

int signal_task_complete() {
    int err = pthread_mutex_lock(&tasks_complete_lock);
    if (err) return err;

    tasks_outstanding--;

    err = pthread_cond_signal(&tasks_complete_cond);
    if (err) {
        pthread_mutex_unlock(&tasks_complete_lock);
        return err;
    }

    return pthread_mutex_unlock(&tasks_complete_lock);
}
