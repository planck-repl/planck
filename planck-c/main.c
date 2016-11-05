#include <errno.h>
#include <getopt.h>
#include <libgen.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <unistd.h>

#include "bundle.h"
#include "cljs.h"
#include "globals.h"
#include "io.h"
#include "legal.h"
#include "repl.h"
#include "str.h"
#include "theme.h"
#include "timers.h"

void usage(char *program_name) {
    printf("Planck %s\n", PLANCK_VERSION);
    printf("Usage:  %s [init-opt*] [main-opt] [arg*]\n", program_name);
    printf("\n");
    printf("  With no options or args, runs an interactive Read-Eval-Print Loop\n");
    printf("\n");
    printf("  init options:\n");
    printf("    -i path, --init=path     Load a file or resource\n");
    printf("    -e string, --eval=string Evaluate expressions in string; print non-nil\n");
    printf("                             values\n");
    printf("    -c cp, --classpath=cp    Use colon-delimited cp for source directories and\n");
    printf("                             JARs. PLANCK_CLASSPATH env var may be used instead.\n");
    printf("    -K, --auto-cache         Create and use .planck_cache dir for cache\n");
    printf("    -k path, --cache=path    If dir exists at path, use it for cache\n");
    printf("    -q, --quiet              Quiet mode\n");
    printf("    -v, --verbose            Emit verbose diagnostic output\n");
    printf("    -d, --dumb-terminal      Disable line editing / VT100 terminal control\n");
    printf("    -t theme, --theme=theme  Set the color theme\n");
    printf("    -n x, --socket-repl=x    Enable socket REPL where x is port or IP:port\n");
    printf("    -s, --static-fns         Generate static dispatch function calls\n");
    printf("    -a, --elide-asserts      Set *assert* to false to remove asserts\n");
    printf("\n");
    printf("  main options:\n");
    printf("    -m ns-name, --main=ns-name Call the -main function from a namespace with\n");
    printf("                               args\n");
    printf("    -r, --repl                 Run a repl\n");
    // printf("    path                       Run a script from a file or resource\n");
    // printf("    -                          Run a script from standard input\n");
    printf("    -h, -?, --help             Print this help message and exit\n");
    printf("    -l, --legal                Show legal info (licenses and copyrights)\n");
    printf("\n");
    printf("  operation:\n");
    printf("\n");
    printf("    - Enters the cljs.user namespace\n");
    printf("    - Binds planck.core/*command-line-args* to a seq of strings containing\n");
    printf("      command line args that appear after any main option\n");
    printf("    - Runs all init options in order\n");
    // printf("    - Calls a -main function or runs a repl or script if requested\n");
    printf("    - Runs a repl or script if requested\n");
    printf("\n");
    printf("  The init options may be repeated and mixed freely, but must appear before\n");
    printf("  any main option.\n");
    printf("\n");
    printf("  Paths may be absolute or relative in the filesystem.\n");
    printf("\n");
    printf("  A comprehensive User Guide for Planck can be found at http://planck-repl.org\n");
    printf("\n");
}

char *get_cljs_version() {
    char *bundle_js = bundle_get_contents("planck/bundle.js");
    if (bundle_js != NULL) {
        char *start = bundle_js + 29;
        char *version = strtok(start, " ");
        version = strdup(version);
        free(bundle_js);
        return version;
    } else {
        return "(Unknown)";
    }
}

void banner() {
    printf("Planck %s\n", PLANCK_VERSION);
    printf("ClojureScript %s\n", get_cljs_version());

    printf("    Docs: (doc function-name-here)\n");
    printf("          (find-doc \"part-of-name-here\")\n");
    printf("  Source: (source function-name-here)\n");
    printf("    Exit: Control+D or :cljs/quit or exit or quit\n");
    printf(" Results: Stored in vars *1, *2, *3, an exception in *e\n");

    printf("\n");
}

struct config config;
int exit_value = 0;
bool return_termsize = false;
JSContextRef global_ctx = NULL;

char *ensure_trailing_slash(char *s) {
    if (str_has_suffix(s, "/") == 0) {
        return strdup(s);
    } else {
        return str_concat(s, "/");
    }
}

char *fully_qualify(char* cwd, char *path) {
    if (cwd && path && str_has_prefix(path, "/") != 0) {
        return str_concat(cwd, path);
    } else {
        return strdup(path);
    }
}

char* get_current_working_dir() {
    char cwd[PATH_MAX];
    if (getcwd(cwd, sizeof(cwd)) != NULL) {
        return ensure_trailing_slash(cwd);
    }
    return NULL;
}

void init_classpath(char* classpath) {

    char* cwd = get_current_working_dir();

    char *source = strtok(classpath, ":");
    while (source != NULL) {
        char *type = "src";
        if (str_has_suffix(source, ".jar") == 0) {
            type = "jar";
        }

        config.num_src_paths += 1;
        config.src_paths = realloc(config.src_paths, config.num_src_paths * sizeof(struct src_path));
        config.src_paths[config.num_src_paths - 1].type = type;
        if (strcmp(type, "jar") == 0) {
            config.src_paths[config.num_src_paths - 1].path = fully_qualify(cwd, source);
        } else {
            char* with_trailing_slash = ensure_trailing_slash(source);
            config.src_paths[config.num_src_paths - 1].path = fully_qualify(cwd, with_trailing_slash);
            free(with_trailing_slash);
        }

        source = strtok(NULL, ":");
    }

    free(cwd);
}

void err_cache_path() {
  fprintf(stderr, "Error: At most one of -k/--cache or -K/--auto-cache may be specified.\n");
}

int main(int argc, char **argv) {
    config.verbose = false;
    config.quiet = false;
    config.repl = false;
    config.javascript = false;
    config.static_fns = false;
    config.elide_asserts = false;
    config.cache_path = NULL;
    config.theme = NULL;
    config.dumb_terminal = false;

    config.out_path = NULL;
    config.num_src_paths = 0;
    config.src_paths = NULL;
    config.num_scripts = 0;
    config.scripts = NULL;

    config.main_ns_name = NULL;

    config.socket_repl_port= 0;
    config.socket_repl_host = NULL;

    struct option long_options[] = {
            {"help",          no_argument,       NULL, 'h'},
            {"legal",         no_argument,       NULL, 'l'},
            {"verbose",       no_argument,       NULL, 'v'},
            {"quiet",         no_argument,       NULL, 'q'},
            {"repl",          no_argument,       NULL, 'r'},
            {"static-fns",    no_argument,       NULL, 's'},
            {"elide-asserts", no_argument,       NULL, 'a'},
            {"cache",         required_argument, NULL, 'k'},
            {"eval",          required_argument, NULL, 'e'},
            {"theme",         required_argument, NULL, 't'},
            {"socket-repl",   required_argument, NULL, 'n'},
            {"dumb-terminal", no_argument,       NULL, 'd'},
            {"classpath",     required_argument, NULL, 'c'},
            {"auto-cache",    no_argument,       NULL, 'K'},
            {"init",          required_argument, NULL, 'i'},
            {"main",          required_argument, NULL, 'm'},

            // development options
            {"javascript",    no_argument,       NULL, 'j'},
            {"out",           required_argument, NULL, 'o'},

            {0, 0, 0,                                  0}
    };
    int opt, option_index;
    bool did_encounter_main_opt = false;
    while (!did_encounter_main_opt && (opt = getopt_long(argc, argv, "h?lvrsak:je:t:n:dc:o:Ki:qm:", long_options, &option_index)) != -1) {
        switch (opt) {
            case 'h':
                usage(argv[0]);
                exit(0);
            case 'l':
                legal();
                return 0;
            case 'v':
                config.verbose = true;
                break;
            case 'q':
                config.quiet = true;
                break;
            case 'r':
                did_encounter_main_opt = true;
                config.repl = true;
                break;
            case 's':
                config.static_fns = true;
                break;
            case 'a':
                config.elide_asserts = true;
                break;
            case 'k':
                if (config.cache_path) {
                    err_cache_path();
                    return EXIT_FAILURE;
                }
                config.cache_path = strdup(optarg);
                break;
            case 'K':
                if (config.cache_path) {
                    err_cache_path();
                    return EXIT_FAILURE;
                }
                config.cache_path = ".planck_cache";
                {
                    char *path_copy = strdup(config.cache_path);
                    char *dir = dirname(path_copy);
                    if (mkdir_p(dir) < 0) {
                        fprintf(stderr, "Could not create %s: %s\n", config.cache_path, strerror(errno));
                    }
                    free(path_copy);
                }
                break;
            case 'j':
                config.javascript = true;
                break;
            case 'e':
                config.num_scripts += 1;
                config.scripts = realloc(config.scripts, config.num_scripts * sizeof(struct script));
                config.scripts[config.num_scripts - 1].type = "text";
                config.scripts[config.num_scripts - 1].expression = true;
                config.scripts[config.num_scripts - 1].source = strdup(optarg);
                break;
            case 'i':
                config.num_scripts += 1;
                config.scripts = realloc(config.scripts, config.num_scripts * sizeof(struct script));
                config.scripts[config.num_scripts - 1].type = "path";
                config.scripts[config.num_scripts - 1].expression = false;
                config.scripts[config.num_scripts - 1].source = strdup(optarg);
                break;
            case 'm':
                did_encounter_main_opt = true;
                config.main_ns_name = strdup(optarg);
                break;
            case 't':
                config.theme = strdup(optarg);
                break;
            case 'n':
                config.socket_repl_host = malloc(256);
                if (sscanf(optarg, "%255[^:]:%d", config.socket_repl_host, &config.socket_repl_port) != 2) {
                    strcpy(config.socket_repl_host, "localhost");
                    if (sscanf(optarg, "%d", &config.socket_repl_port) != 1) {
                        printf("Could not parse socket REPL params.\n");
                        free(config.socket_repl_host);
                        config.socket_repl_port = 0;
                    }
                }
                break;
            case 'd':
                config.dumb_terminal = true;
                break;
            case 'c': {
                char *classpath = strdup(optarg);
                init_classpath(classpath);

                break;
            }
            case 'o':
                config.out_path = ensure_trailing_slash(strdup(optarg));
                break;
            case '?':
                usage(argv[0]);
                exit(1);
            default:
                printf("unhandled argument: %c\n", opt);
        }
    }

    if (config.cache_path) {
        if (access(config.cache_path, W_OK) != 0) {
            fprintf(stderr, "Warning: Unable to write to cache directory.\n\n");
        }
    }

    if (config.num_src_paths == 0) {
        char *classpath = getenv("PLANCK_CLASSPATH");
        if (classpath) {
            init_classpath(classpath);
        }
    }

    if (config.dumb_terminal) {
        config.theme = "dumb";
    } else {
        if (!config.theme) {
            config.theme = default_theme_for_terminal();
        }
    }

    config.num_rest_args = 0;
    config.rest_args = NULL;
    if (optind < argc) {
        config.num_rest_args = (size_t) (argc - optind);
        config.rest_args = malloc((argc - optind) * sizeof(char *));
        int i = 0;
        while (optind < argc) {
            config.rest_args[i++] = argv[optind++];
        }
    }

    if (config.num_scripts == 0 && config.main_ns_name == NULL && config.num_rest_args == 0) {
        config.repl = true;
    }

    if (!check_theme(config.theme)) {
        exit(1);
    }

    if (config.main_ns_name != NULL && config.repl) {
        printf("Only one main-opt can be specified.\n");
        exit(1);
    }

    config.is_tty = isatty(STDIN_FILENO) == 1;

    JSGlobalContextRef ctx = JSGlobalContextCreate(NULL);
    global_ctx = ctx;
    cljs_engine_init(ctx);

    // Process init arguments

    for (int i = 0; i < config.num_scripts; i++) {
        struct script script = config.scripts[i];
        evaluate_source(ctx, script.type, script.source, script.expression, false, NULL, config.theme, true, 0);
        if (exit_value != EXIT_SUCCESS) {
            return exit_value;
        }
    }

    // Process main arguments

    if (config.main_ns_name != NULL) {
        run_main_in_ns(ctx, config.main_ns_name, config.num_rest_args, config.rest_args);
    } else if (!config.repl && config.num_rest_args > 0) {
        char *path = config.rest_args[0];
        config.rest_args++;
        config.num_rest_args--;

        struct script script;
        if (strcmp(path, "-") == 0) {
            char *source = read_all(stdin);
            script.type = "text";
            script.source = source;
            script.expression = false;
        } else {
            script.type = "path";
            script.source = path;
            script.expression = false;
        }

        evaluate_source(ctx, script.type, script.source, script.expression, false, NULL, config.theme, true, 0);
    } else if (config.repl) {
        if (!config.quiet) {
            banner();
        }

        run_repl(ctx);
    }

    if (exit_value == EXIT_SUCCESS) {
        block_until_timers_complete();
    }

    if (exit_value == EXIT_SUCCESS_INTERNAL) {
        exit_value = EXIT_SUCCESS;
    }

    return exit_value;
}
