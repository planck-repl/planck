typedef void (*timer_callback_t)(void *data);

int start_timer(long millis, timer_callback_t timer_callback, void *data);

int block_until_timers_complete();