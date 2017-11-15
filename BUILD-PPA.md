# Build PPA

## Create Sources Archive

On Ubuntu, clone and then make a sources archive that includes all necessary JARs.

0. `ssh` to Ubuntu build box.
0. `git clone https://github.com/mfikes/planck`
0. `cd planck`
0. `FAST_BUILD=1 script/build-sandbox`
0. `cp ~/.lein/self-installs/leiningen-2.8.1-standalone.jar planck-cljs/sandbox-m2`
0. `BUILD_PPA=1 script/clean`
0. `cd ..`
0. `tar cvzf planck_2.<x>.<y>.orig.tar.gz planck`

## Configure PPA Build Files

0. For a given Ubuntu release, copy `debian/control.<ubuntu-release>` and `debian/changelog.<ubuntu-release>` to unsuffixed versions.
0. Set the `2.<x>.<y>` version number in `debian/changelog` appropriately.

## Create Build

0. Go into the `planck` directory
0. `debuild -S -sa`

## Upload Build

0. `cd` to the top
0. `dput ppa:mfikes/planck planck_2.<x>.<y>-1ppa1~<ubuntu-release>1_source.changes`
