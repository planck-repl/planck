#ifndef SOCKETS_H
#define SOCKETS_H

#include <stdbool.h>

typedef void *(*connection_handler_t)(void *socket_desc);

typedef void (*listen_successful_cb_t)(void);

typedef struct accepted_conn_cb_ret {
    int err;
    void* info;
} accepted_conn_cb_ret_t;

typedef accepted_conn_cb_ret_t* (*accepted_conn_cb_t)(int sock, void* state);

typedef struct conn_data_cb_ret {
    int err;
    bool close;
} conn_data_cb_ret_t;

typedef conn_data_cb_ret_t* (*conn_data_cb_t)(char* data, int sock, void* state);

typedef struct socket_accept_info {
    char *host;
    int port;
    listen_successful_cb_t listen_successful_cb;
    accepted_conn_cb_t accepted_conn_cb;
    conn_data_cb_t conn_data_cb;
    int socket_desc;
    void* info;
} socket_accept_info_t;

int write_to_socket(int fd, const char *text);

int bind_and_listen(socket_accept_info_t* socket_accept_info1);

void *accept_connections(void *data);

int close_socket(int fd);

int connect_socket(const char *host, int port, conn_data_cb_t conn_data_cb,
                   void *data_arrived_info);

#endif /* SOCKETS_H */
