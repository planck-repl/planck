#ifndef CLJ_H
#define CLJ_H

#include <wchar.h>
#include <setjmp.h>  //TODO: Only needed privately?

#ifdef __cplusplus
extern "C" {
#endif

typedef enum clj_result {
  CLJ_EOF  = 1,
  CLJ_MORE = 2,
  CLJ_UNEXPECTED_EOF      = -1,
  CLJ_UNMATCHED_DELIMITER = -2,
  CLJ_NOT_IMPLEMENTED     = -3,
  CLJ_UNREADABLE          = -4,
} clj_Result;

int clj_is_error(clj_Result result);

//typedef struct clj_named {
//  const wchar_t *ns;
//  const wchar_t *name;
//} clj_Named;

typedef enum clj_type {
  // Flags
  CLJ_ATOMIC    = 0x0100,
  CLJ_COMPOSITE = 0x0200,
  CLJ_END       = 0x1000,
  // Atomic types
  CLJ_NUMBER    = 0x0101,
  CLJ_CHARACTER = 0x0102,
  CLJ_STRING    = 0x0103,
  CLJ_KEYWORD   = 0x0104,
  CLJ_SYMBOL    = 0x0105,
  CLJ_REGEX     = 0x0106,
  // Composite types
  CLJ_MAP       = 0x0201,
  CLJ_LIST      = 0x0202,
  CLJ_SET       = 0x0203,
  CLJ_VECTOR    = 0x0204,
} clj_Type;

int clj_is_atomic(clj_Type type);
int clj_is_composite(clj_Type type);
int clj_is_begin(clj_Type type);
int clj_is_end(clj_Type type);

typedef struct clj_node {
  clj_Type type;
  const wchar_t *value;
} clj_Node;

typedef struct clj_reader {
  // Read/write
  wint_t (*getwchar)(const struct clj_reader*);
  void (*emit)(const struct clj_reader*, const clj_Node*);
  void *data;
  // Read-only
  int line;
  int column;
  int depth;
  // Private
  int _discard;
  wint_t _readback;
  wint_t _readback_column;
  jmp_buf _fail;
} clj_Reader;

clj_Result clj_read(clj_Reader*);

int clj_read_error(char*, const clj_Reader*, clj_Result);

typedef struct clj_printer {
  wint_t (*putwchar)(wchar_t c);
  //TODO: line/column/depth
} clj_Printer;

void clj_print(clj_Printer*, const clj_Node*);

#ifdef __cplusplus
}
#endif

#endif /* CLJ_H */
