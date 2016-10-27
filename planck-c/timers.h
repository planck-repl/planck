typedef void (*timer_callback_t)(void* data);

void start_timer(long millis, timer_callback_t timer_callback, void *data);

void block_until_timers_complete();