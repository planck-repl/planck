.PHONY: build bundle-and-build release bundle-test http-test zip-test clean

VERSION = $(shell git describe --tags)

CC = clang

CFLAGS = -Wall -DDEBUG -pthread
UNAME_S = $(shell uname -s)
ifeq ($(UNAME_S),Linux)
	DEPS = javascriptcoregtk-3.0 libzip libcurl
	CFLAGS += $(shell pkg-config --cflags $(DEPS))
	LIBFLAGS = $(shell pkg-config --libs $(DEPS))
endif
ifeq ($(UNAME_S),Darwin)
	DEPS = libzip libcurl
	CFLAGS += $(shell pkg-config --cflags $(DEPS))
	LIBFLAGS += -framework JavaScriptCore -lz $(shell pkg-config --libs $(DEPS))
endif
CFLAGS += $(EXTRA_CFLAGS)

SOURCES = $(sort $(wildcard *.c) linenoise.c)
OBJECTS = $(SOURCES:.c=.o)

# Build only the binary
build: planck

# Bundle cljs dependencies and build with that
bundle-and-build:
	cd ..; ./script/build-c

# Build release tarball
release:
	git archive --prefix=planck-$(VERSION)/ --output=planck-$(VERSION).tar HEAD
	# add bundle.c (with deps) and linenoise
	tar -uf planck-$(VERSION).tar --transform='s/^/planck-$(VERSION)\//' bundle.c linenoise.c linenoise.h
	gzip --force planck-$(VERSION).tar


planck: $(OBJECTS)
	$(CC) $(CFLAGS) $(LIBFLAGS) $(OBJECTS) -o $@

linenoise.c: linenoise.h
	cp ../planck/linenoise/linenoise.c .

linenoise.h:
	cp ../planck/linenoise/linenoise.h .

bundle-test:
	$(CC) -lz -DBUNDLE_TEST bundle.c -o $@

http-test: jsc_utils.o
	$(CC) $(shell pkg-config --cflags --libs javascriptcoregtk-3.0 libcurl) -DHTTP_TEST jsc_utils.o http.c -o $@

zip-test:
	$(CC) $(shell pkg-config --cflags --libs libzip) -DZIP_TEST zip.c -o $@

clean:
	rm -f planck bundle-test zip-test $(OBJECTS)

jsc-funcs:
	grep -Rh JS_EXPORT /usr/include/webkitgtk-4.0/JavaScriptCore | sed 's/^JS_EXPORT //' | grep -v '^#' > $@
