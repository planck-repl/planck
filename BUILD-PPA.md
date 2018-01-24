# Build PPA

## Create Sources Archive

On Ubuntu, clone and then make a sources archive that includes all necessary JARs.

0. `ssh` to Ubuntu build box.
0. `curl -O https://download.clojure.org/install/linux-install-1.9.0.315.sh`
0. `chmod +x linux-install-1.9.0.315.sh`
0. `sudo ./linux-install-1.9.0.315.sh`
0. `git clone https://github.com/mfikes/planck`
0. `cd planck`
0. `cp /usr/local/bin/clojure planck-cljs/script`
0. `cp /usr/local/lib/clojure/deps.edn planck-cljs/script`
0. `cp -r /usr/local/lib/clojure/libexec planck-cljs/script/libexec`
0. Edit `planck-cljs/script/clojure` and revise `install_dir` to be `script`
0. `FAST_BUILD=1 script/build-sandbox`
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
