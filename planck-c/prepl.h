#include "sockets.h"

typedef struct prepl {
    JSValueRef in;
    JSValueRef out;
    JSValueRef tap;
} prepl_t;

prepl_t *make_prepl();

conn_data_cb_ret_t* prepl_data_arrived(char *data, int sock, void *state);

accepted_conn_cb_ret_t* prepl_accepted_connection(int sock, void *info);

void prepl_listen_successful_cb();
