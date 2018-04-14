# Build PPA

## Create Sources Archive

On Ubuntu, clone and then make a sources archive that includes all necessary JARs.

> Note: If upgrading to a newer clj tool version, then `debian/source-include-binaries` needs to be updated.

0. `ssh` to Ubuntu build box.
0. `curl -O https://download.clojure.org/install/linux-install-1.9.0.358.sh`
0. `chmod +x linux-install-1.9.0.358.sh`
0. `sudo ./linux-install-1.9.0.358.sh`
0. `git clone https://github.com/planck-repl/planck`
0. `cd planck`
0. `cp /usr/local/bin/clojure planck-cljs/script`
0. `cp /usr/local/lib/clojure/deps.edn planck-cljs/script`
0. `cp /usr/local/lib/clojure/example-deps.edn planck-cljs/script`
0. `cp -r /usr/local/lib/clojure/libexec planck-cljs/script/libexec` (make sure files copied are listed in `debian/source-include-binaries`)
0. Edit `planck-cljs/script/clojure` and revise `install_dir` to be `script`
0. `FAST_BUILD=1 script/build-sandbox`
0. `BUILD_PPA=1 script/clean`
0. `cd ..`
0. `tar cvzf planck_2.<x>.<y>.orig.tar.gz planck`

> To check that there are no problems building in a sandbox, unarchive the above into a new directory in `/tmp`, `export HOME=/sbuild-nonexistent`, and try `script/build-sandbox`.

## Configure PPA Build Files

0. For a given Ubuntu release, copy `debian/control.<ubuntu-release>` and `debian/changelog.<ubuntu-release>` to unsuffixed versions.
0. Set the `2.<x>.<y>` version number in `debian/changelog` appropriately.

## Create Build

0. Go into the `planck` directory
0. `debuild -S -sa`

## Upload Build

0. `cd` to the top
0. `dput ppa:mfikes/planck planck_2.<x>.<y>-1ppa1~<ubuntu-release>1_source.changes`
