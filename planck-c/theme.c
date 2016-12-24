#include <memory.h>
#include <stdio.h>
#include <stdbool.h>
#include <stdlib.h>

static char *font_colors[] =
        {"no-font", "",
         "black-font", "\x1b[30m",
         "red-font", "\x1b[31m",
         "green-font", "\x1b[32m",
         "yellow-font", "\x1b[33m",
         "blue-font", "\x1b[34m",
         "magenta-font", "\x1b[35m",
         "cyan-font", "\x1b[36m",
         "white-font", "\x1b[37m",
         "black-bold-font", "\x1b[40m",
         "red-bold-font", "\x1b[41m",
         "green-bold-font", "\x1b[42m",
         "yellow-bold-font", "\x1b[43m",
         "blue-bold-font", "\x1b[44m",
         "magenta-bold-font", "\x1b[45m",
         "cyan-bold-font", "\x1b[46m",
         "white-bold-font", "\x1b[47m"};

static char *prompt_fonts[] =
        {"plain", "no-font",
         "light", "cyan-font",
         "dark", "blue-font"};

const char *color_for_font(const char *font) {

    int i;
    for (i = 0; i < sizeof(font_colors) / sizeof(font_colors[0]); i += 2) {
        if (strcmp(font, font_colors[i]) == 0) {
            return font_colors[i + 1];
        }
    }

    return NULL;
}

const char *prompt_font_for_theme(const char *theme) {

    int i;
    for (i = 0; i < sizeof(prompt_fonts) / sizeof(prompt_fonts[0]); i += 2) {
        if (strcmp(theme, prompt_fonts[i]) == 0) {
            return prompt_fonts[i + 1];
        }
    }

    return NULL;
}

const char *default_theme_for_terminal() {

    // Check COLORFGBG env var

    char *color_fg_bg = getenv("COLORFGBG");
    if (color_fg_bg) {
        strtok(color_fg_bg, ";");
        char *bg = strtok(NULL, ";");
        if (bg && strcmp(bg, "0") == 0) {
            return "dark";
        }
    }

    return "light";
}

const char *prompt_ansi_code_for_theme(const char *theme) {

    const char *font = prompt_font_for_theme(theme);

    return font ? color_for_font(font) : NULL;
}

bool check_theme(const char *theme) {

    if (strcmp(theme, "dumb") == 0 || prompt_font_for_theme(theme)) {
        return true;
    }

    int err = fprintf(stderr, "Unsupported theme: %s\n", theme);
    if (err == -1) return false;

    err = fprintf(stderr, "Supported themes:\n");
    if (err == -1) return false;

    int i;
    for (i = 0; i < sizeof(prompt_fonts) / sizeof(prompt_fonts[0]); i += 2) {
        err = fprintf(stderr, "  %s\n", prompt_fonts[i]);
        if (err == -1) return false;
    }

    return false;
}

