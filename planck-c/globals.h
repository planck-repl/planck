// Global variables used throughout Planck

#define PLANCK_VERSION "2.0"

// Configuration

struct src_path {
    char *type;
    char *path;
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
    bool static_fns;
    bool elide_asserts;
    char *theme;
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
};

extern struct config config;

// Mutable variables

extern int exit_value;
extern bool return_termsize;
