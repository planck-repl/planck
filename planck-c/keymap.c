#include <memory.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>

#include "edn.h"
#include "linenoise.h"
#include "str.h"

int id_for_key_map_action(char *action) {

    if (strcmp(action, ":go-to-beginning-of-line") == 0) {
        return KM_GO_TO_START_OF_LINE;
    } else if (strcmp(action, ":go-back-one-space") == 0) {
        return KM_MOVE_LEFT;
    } else if (strcmp(action, ":go-forward-one-space") == 0) {
        return KM_MOVE_RIGHT;
    } else if (strcmp(action, ":delete-right") == 0) {
        return KM_DELETE_RIGHT;
    } else if (strcmp(action, ":delete-backwards") == 0) {
        return KM_DELETE;
    } else if (strcmp(action, ":delete-to-end-of-line") == 0) {
        return KM_DELETE_TO_END_OF_LINE;
    } else if (strcmp(action, ":go-to-end-of-line") == 0) {
        return KM_GO_TO_END_OF_LINE;
    } else if (strcmp(action, ":clear-screen") == 0) {
        return KM_CLEAR_SCREEN;
    } else if (strcmp(action, ":next-line") == 0) {
        return KM_HISTORY_NEXT;
    } else if (strcmp(action, ":previous-line") == 0) {
        return KM_HISTORY_PREVIOUS;
    } else if (strcmp(action, ":transpose-characters") == 0) {
        return KM_SWAP_CHARS;
    } else if (strcmp(action, ":undo-typing-on-line") == 0) {
        return KM_CLEAR_LINE;
    } else if (strcmp(action, ":delete-previous-word") == 0) {
        return KM_DELETE_PREVIOUS_WORD;
    } else if (strcmp(action, ":reverse-i-search") == 0) {
        return KM_REVERSE_I_SEARCH;
    } else if (strcmp(action, ":cancel-search") == 0) {
        return KM_CANCEL_SEARCH;
    } else if (strcmp(action, ":finish-search") == 0) {
        return KM_FINISH_SEARCH;
    } else {
        return -1;
    }
}

char key_code_for(char *key_name) {
    if (str_has_prefix(key_name, ":ctrl-") == 0 && strlen(key_name) == 7) {
        char c = key_name[6];
        if ('a' <= c && c <= 'z') {
            return (char) (c - 'a' + 1);
        } else {
            return -1;
        }
    }
    return -1;
}

static int last_id = -2;
static char buffer[1024];

static FILE *reader_file = NULL;

static wint_t reader_getwchar(const clj_Reader *r) {
    return fgetwc(reader_file);
}

static bool in_map = false;
static char *keymap_path = NULL;

extern void emit(const clj_Reader *r, const clj_Node *n) {
    if (n->type == CLJ_MAP) {
        in_map = true;
    } else if (n->type == CLJ_END) {
        in_map = false;
    } else if (n->type == CLJ_KEYWORD) {
        wcstombs(buffer, n->value, 1024);
        if (last_id == -2) {
            last_id = id_for_key_map_action(buffer);
            if (last_id == -1) {
                fprintf(stderr, "%s: Unrecognized keymap key: %s\n", keymap_path, buffer);
            }
        } else {
            char key_code = key_code_for(buffer);
            if (key_code == -1) {
                fprintf(stderr, "%s: Unrecognized keymap value: %s\n", keymap_path, buffer);
            }
            if (last_id != -1 && key_code != -1) {
                linenoiseSetKeymapEntry(last_id, key_code);
            }
            last_id = -2;
        }
    }
}

int load_keymap(char *home) {

    char keymap_name[] = ".planck_keymap";
    size_t len = strlen(home) + strlen(keymap_name) + 2;
    keymap_path = malloc(len * sizeof(char));
    snprintf(keymap_path, len, "%s/%s", home, keymap_name);

    reader_file = fopen(keymap_path, "r");
    if (reader_file) {
        clj_Result result;
        char message[200];

        clj_Reader reader;
        reader.emit = emit;
        reader.getwchar = reader_getwchar;

        while (1) {
            result = clj_read(&reader);
            switch (result) {
                case CLJ_MORE:
                    break;
                case CLJ_EOF:
                    free(keymap_path);
                    fclose(reader_file);
                    return EXIT_SUCCESS;
                default:
                    free(keymap_path);
                    fclose(reader_file);
                    clj_read_error(message, &reader, result);
                    fprintf(stderr, "%s\n", message);
                    return EXIT_FAILURE;
            }
        }

    }

    return EXIT_SUCCESS;
}