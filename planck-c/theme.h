#include <stdbool.h>

const char *default_theme_for_terminal();

const char *prompt_ansi_code_for_theme(const char *theme);

bool check_theme(const char *theme);