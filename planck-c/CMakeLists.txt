cmake_minimum_required(VERSION 2.8.8)
project(planck C)

set(CMAKE_BUILD_TYPE Release)

# Uncomment to enable Clang Address Sanitizer
#set (CMAKE_C_FLAGS "-fsanitize=address -O1 -fno-omit-frame-pointer")

set(SOURCE_FILES
    archive.c
    archive.h
    bundle.c
    bundle.h
    bundle_inflate.h
    clock.c
    clock.h
    edn.c
    edn.h
    engine.c
    engine.h
    file.c
    file.h
    functions.c
    functions.h
    globals.h
    http.c
    http.h
    io.c
    io.h
    jsc_utils.c
    jsc_utils.h
    keymap.c
    keymap.h
    legal.c
    legal.h
    linenoise.c
    linenoise.h
    main.c
    prepl.c
    prepl.h
    repl.c
    repl.h
    shell.c
    shell.h
    sockets.c
    sockets.h
    str.c
    str.h
    tasks.c
    tasks.h
    theme.c
    theme.h
    timers.c
    timers.h)

add_executable(planck ${SOURCE_FILES})

find_package(PkgConfig REQUIRED)

FIND_PACKAGE(CURL)
if(CURL_FOUND)
    include_directories(${CURL_INCLUDE_DIR})
    target_link_libraries(planck ${CURL_LIBRARIES})
else(CURL_FOUND)
    find_program(CURL_CONFIG curl-config)
    if(CURL_CONFIG)
        execute_process(COMMAND ${CURL_CONFIG} --cflags
                OUTPUT_VARIABLE CURL_CFLAGS
                OUTPUT_STRIP_TRAILING_WHITESPACE)
        execute_process(COMMAND ${CURL_CONFIG} --libs
                OUTPUT_VARIABLE CURL_LIBS
                OUTPUT_STRIP_TRAILING_WHITESPACE)
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${CURL_CFLAGS}")
        target_link_libraries(planck ${CURL_LIBS})
    else(CURL_CONFIG)
        find_library(CURL curl)
        target_link_libraries(planck ${CURL})
    endif(CURL_CONFIG)
endif(CURL_FOUND)

pkg_check_modules(ZLIB REQUIRED zlib)
include_directories(${ZLIB_INCLUDE_DIRS})
target_link_libraries(planck ${ZLIB_LDFLAGS})

pkg_check_modules(LIBZIP REQUIRED libzip)
include_directories(${LIBZIP_INCLUDE_DIRS})
target_link_libraries(planck ${LIBZIP_LDFLAGS})

if(APPLE)
    find_library(JAVASCRIPTCORE JavaScriptCore)
    mark_as_advanced(JAVASCRIPTCORE)
    target_link_libraries(planck ${JAVASCRIPTCORE})
elseif(UNIX)
    pkg_check_modules(JAVASCRIPTCORE javascriptcoregtk-4.0)
    if(NOT JAVASCRIPTCORE_FOUND)
        pkg_check_modules(JAVASCRIPTCORE REQUIRED javascriptcoregtk-3.0)
        add_definitions(-DJAVASCRIPT_CORE_3)
    endif(NOT JAVASCRIPTCORE_FOUND)
    include_directories(${JAVASCRIPTCORE_INCLUDE_DIRS})
    target_link_libraries(planck ${JAVASCRIPTCORE_LDFLAGS})
endif(APPLE)

if(APPLE)
   add_definitions(-DU_DISABLE_RENAMING)
   include_directories(/usr/local/opt/icu4c/include)
   find_library(ICU4C icucore)
   target_link_libraries(planck ${ICU4C})
elseif(UNIX)
   pkg_check_modules(ICU_UC REQUIRED icu-uc)
   pkg_check_modules(ICU_IO REQUIRED icu-io)
   include_directories(${ICU_UC_INCLUDE_DIRS} ${ICU_IO_INCLUDE_DIRS})
   target_link_libraries(planck ${ICU_UC_LDFLAGS} ${ICU_IO_LDFLAGS})
endif(APPLE)

if(APPLE)
elseif(UNIX)
   find_package(Threads)
   target_link_libraries(planck ${CMAKE_THREAD_LIBS_INIT})
endif(APPLE)
