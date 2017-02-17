# Build PPA

## Create Sources Archive

On Ubuntu, clone and then make a sources archive that includes all necessary JARs.

0. `ssh` to Ubuntu build box.
0. `git clone https://github.com/mfikes/planck`
0. `cd planck`
0. `script/build-sandbox`
0. `cp ~/.lein/self-installs/leiningen-2.7.1-standalone.jar planck-cljs/sandbox-m2`
0. `script/clean-all`
0. `cd ..`
0. `tar cvzf planck_2.0.0.orig.tar.gz planck`

## Update PPA Build Files

0. Update `debian/control` and `debian/changelog`

Update the `Build Depends` and `Depends` lines.

If building for trusty:

```
Build-Depends: git, cmake, default-jdk, clang, pkg-config, vim-common, libjavascriptcoregtk-3.0-dev, libglib2.0-dev, libzip-dev, libcurl4-gnutls-dev, libicu-dev
Depends: libjavascriptcoregtk-3.0-bin, libzip2, libicu52
```

If building for xenial:

```
Build-Depends: git, cmake, default-jdk, clang, pkg-config, vim-common, libjavascriptcoregtk-4.0-dev, libglib2.0-dev, libzip-dev, libcurl4-gnutls-dev, libicu-dev
Depends: libjavascriptcoregtk-4.0-bin, libzip4, libicu55
```

If building for yakkety:

```
Build-Depends: git, cmake, default-jdk, clang, pkg-config, vim-common, libjavascriptcoregtk-4.0-dev, libglib2.0-dev, libzip-dev, libcurl4-gnutls-dev, libicu-dev
Depends: libjavascriptcoregtk-4.0-bin, libzip4, libicu57
```

If building for zesty:

```
Build-Depends: git, cmake, default-jdk, clang, pkg-config, vim-common, libjavascriptcoregtk-4.0-dev, libglib2.0-dev, libzip-dev, libcurl4-gnutls-dev, libicu-dev
Depends: libjavascriptcoregtk-4.0-bin, libzip4, libicu57
```

Set the version numbers in `debian/changelog` appropriately (reflecting the distro as well) and add a changelog line entry.


## Create Build

0. Go into the `planck` directory
0. `debuild -S -sa`

## Upload Build

0. `cd` to the top
0. `dput ppa:mfikes/planck planck_2.0.0-1ppa1~trusty1_source.changes`
