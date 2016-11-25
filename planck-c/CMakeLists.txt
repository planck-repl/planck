cmake_minimum_required(VERSION 2.8.7)
project(planck C)

set(CMAKE_BUILD_TYPE Release)

set(SOURCE_FILES
    archive.c
    archive.h
    bundle.c
    bundle.h
    bundle_inflate.h
    clj.c
    clj.h
    cljs.c
    cljs.h
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
    repl.c
    repl.h
    shell.c
    shell.h
    str.c
    str.h
    theme.c
    theme.h
    timers.c
    timers.h)

add_executable(planck ${SOURCE_FILES})

find_package(PkgConfig REQUIRED)

find_library(CURL curl)
target_link_libraries(planck ${CURL})

option(USE_BUNDLED_LIBZIP "use an in-tree version of libzip" OFF)
if(USE_BUNDLED_LIBZIP)
    if(NOT IS_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/libzip-1.1.3")
        message(FATAL_ERROR "The libzip-1.1.3 dependency is missing."
            "See https://github.com/mfikes/planck/pull/361 for instructions.")
    endif()
    include_directories(${CMAKE_CURRENT_SOURCE_DIR})
    add_subdirectory(libzip-1.1.3/)
    target_link_libraries(planck zip)
else(USE_BUNDLED_LIBZIP)
    pkg_check_modules(LIBZIP REQUIRED libzip)
    include_directories(${LIBZIP_INCLUDE_DIRS})
    target_link_libraries(planck ${LIBZIP_LDFLAGS})
endif(USE_BUNDLED_LIBZIP)

if(APPLE)
    find_library(JAVASCRIPTCORE JavaScriptCore)
    mark_as_advanced(JAVASCRIPTCORE)
    target_link_libraries(planck ${JAVASCRIPTCORE})
elseif(UNIX)
    pkg_check_modules(JAVASCRIPTCORE javascriptcoregtk-4.0)
    if(NOT JAVASCRIPTCORE_FOUND)
        pkg_check_modules(JAVASCRIPTCORE REQUIRED javascriptcoregtk-3.0)
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
   target_link_libraries(planck ${ICU_UC_LDFLAGS} ${ICU_IO_LDFLAGS})
endif(APPLE)

if(APPLE)
elseif(UNIX)
   find_package(Threads)
   target_link_libraries(planck ${CMAKE_THREAD_LIBS_INIT})
endif(APPLE)
