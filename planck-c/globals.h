// Global variables used throughout Planck

#define PLANCK_VERSION "2.22.0"

// Configuration

struct src_path {
    char *type;
    char *path;
    void *archive;
    bool blacklisted;
};

struct script {
    char *type;
    bool expression;
    char *source;
};

struct config {
    bool verbose;
    bool quiet;
    bool is_tty;
    bool repl;
    bool javascript;
    char* checked_arrays;
    bool static_fns;
    bool fn_invoke_direct;
    bool elide_asserts;
    char* optimizations;
    const char *theme;
    bool dumb_terminal;

    char *main_ns_name;
    size_t num_rest_args;
    char **rest_args;

    char *out_path;
    char *cache_path;

    size_t num_src_paths;
    struct src_path *src_paths;
    size_t num_scripts;
    struct script *scripts;

    char *socket_repl_host;
    int socket_repl_port;

    char *clojurescript_version;

    size_t num_compile_opts;
    char **compile_opts;
};

extern struct config config;

// Mutable variables

extern int exit_value;
extern bool return_termsize;
