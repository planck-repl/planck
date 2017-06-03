#include <errno.h>
#include <stdlib.h>
#include <pthread.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netdb.h>

#include "sockets.h"
#include "engine.h"

int write_to_socket(int fd, const char *text) {

    while (true) {

        fd_set fds;
        FD_ZERO(&fds);

        FD_SET(fd, &fds);

        int rv = select(fd + 1, NULL, &fds, NULL, NULL);

        if (rv == -1) {
            if (errno == EINTR) {
                // We ignore interrupts and loop back around
            } else {
                return -1;
            }
        } else {
            size_t len = strlen(text);
            ssize_t n = write(fd, text, len);
            if (n == len) {
                return 0;
            }
            if (n == -1) {
                return -1;
            }
            text += n;
        }
    }
}

typedef struct conn_handler_info {
    int sock;
    socket_accept_info_t *socket_accept_info;
} conn_handler_info_t;

void *conn_handler(void *data) {

    conn_handler_info_t *conn_handler_info = (conn_handler_info_t *) data;

    accepted_conn_cb_ret_t *accepted_conn_cb_ret = NULL;
    if (conn_handler_info->socket_accept_info->accepted_conn_cb) {
        accepted_conn_cb_ret = conn_handler_info->socket_accept_info->accepted_conn_cb(
                conn_handler_info->sock, conn_handler_info->socket_accept_info->info);
    }

    int err = 0;
    void *state = NULL;
    if (accepted_conn_cb_ret) {
        err = accepted_conn_cb_ret->err;
        state = accepted_conn_cb_ret->info;
        free(accepted_conn_cb_ret);
    }

    ssize_t read_size;
    char receive_buffer[4096];

    conn_data_cb_ret_t *conn_data_cb_ret = NULL;

    while (!err && (read_size = recv(conn_handler_info->sock, receive_buffer, 4095, 0)) > 0) {
        receive_buffer[read_size] = '\0';
        conn_data_cb_ret = conn_handler_info->socket_accept_info->conn_data_cb(
                receive_buffer, conn_handler_info->sock, state);

        err = conn_data_cb_ret->err;
        bool close_socket = conn_data_cb_ret->close;
        free(conn_data_cb_ret);

        if (close_socket) {
            close(conn_handler_info->sock);
            break;
        }
    }

    free(data);

    return NULL;
}

int bind_and_listen(socket_accept_info_t *socket_accept_info) {

    struct sockaddr_in server;

    int socket_desc = socket(AF_INET, SOCK_STREAM, 0);
    if (socket_desc == -1) {
        return socket_desc;
    }
    socket_accept_info->socket_desc = socket_desc;

    server.sin_family = AF_INET;
    server.sin_addr.s_addr = INADDR_ANY;
    server.sin_port = htons(socket_accept_info->port);

    int err = bind(socket_desc, (struct sockaddr *) &server, sizeof(server));
    if (err == -1) {
        return err;
    }

    return listen(socket_desc, 3);
}

void *accept_connections(void *data) {

    socket_accept_info_t *socket_accept_info = (socket_accept_info_t *) data;

    if (socket_accept_info->listen_successful_cb) {
        socket_accept_info->listen_successful_cb();
    }

    int c = sizeof(struct sockaddr_in);
    int new_socket;
    struct sockaddr_in client;
    while ((new_socket = accept(socket_accept_info->socket_desc, (struct sockaddr *) &client, (socklen_t *) &c))) {

        pthread_t handler_thread;

        conn_handler_info_t *connection_handler_data = malloc(sizeof(conn_handler_info_t));
        connection_handler_data->sock = new_socket;
        connection_handler_data->socket_accept_info = socket_accept_info;

        if (pthread_create(&handler_thread, NULL, conn_handler, connection_handler_data) < 0) {
            engine_perror("could not create thread");
            return NULL;
        }
    }

    if (new_socket < 0) {
        engine_perror("accept failed");
        return NULL;
    }

    return NULL;
}

int close_socket(int fd) {
    return shutdown(fd, SHUT_RDWR);
}

typedef struct read_inbound_socket_info {
    int socket_desc;
    conn_data_cb_t conn_data_cb;
    void *data_arrived_info;
} read_inbound_socket_info_t;

void *read_inbound_socket_data(void *data) {

    read_inbound_socket_info_t *read_inbound_socket_info = data;

    ssize_t read_size;
    char receive_buffer[4096];

    while ((read_size = recv(read_inbound_socket_info->socket_desc, receive_buffer, 4095, 0)) > 0) {
        receive_buffer[read_size] = '\0';
        conn_data_cb_ret_t *conn_data_cb_ret = read_inbound_socket_info->conn_data_cb(
                receive_buffer, read_inbound_socket_info->socket_desc, read_inbound_socket_info->data_arrived_info);
        free(conn_data_cb_ret);
    }

    free(read_inbound_socket_info);

    return NULL;
}

int connect_socket(const char *host, int port, conn_data_cb_t conn_data_cb,
                   void *data_arrived_info) {

    int socket_desc = socket(AF_INET, SOCK_STREAM, 0);
    if (socket_desc == -1) {
        return socket_desc;
    }

    struct hostent *server = gethostbyname(host);
    if (server == NULL) {
        // no such host
        return -1;
    }

    struct sockaddr_in serv_addr;
    bzero((char *) &serv_addr, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    bcopy((char *) server->h_addr,
          (char *) &serv_addr.sin_addr.s_addr,
          server->h_length);
    serv_addr.sin_port = htons(port);

    int err = connect(socket_desc, (struct sockaddr *) &serv_addr, sizeof(serv_addr));
    if (err == -1) {
        return err;
    } else {
        read_inbound_socket_info_t *read_inbound_socket_info = malloc(sizeof(read_inbound_socket_info_t));
        read_inbound_socket_info->socket_desc = socket_desc;
        read_inbound_socket_info->conn_data_cb = conn_data_cb;
        read_inbound_socket_info->data_arrived_info = data_arrived_info;

        pthread_t reader_thread;

        if (pthread_create(&reader_thread, NULL, read_inbound_socket_data, read_inbound_socket_info) < 0) {
            engine_perror("could not create thread");
            return -1;
        }
        return socket_desc;
    }
}
