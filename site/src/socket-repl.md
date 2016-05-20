## Socket REPL

<img width="85" align="right" style="margin: 0ex 1em" src="img/socket-repl.jpg">
Planck supports connecting via a TCP socket.

This mimics the Socket REPL feature introduced with Clojure 1.8.

To start Planck in this mode, add the `-n`, or `-​-​socket-repl` command line option, minimally specifying the port to listen on. If you'd like to have Planck additionally listen only on a specific IP address, specify it as in `192.0.2.1:9999`.

Here is an example of starting a REPL with a listening socket enabled and `def`ing a var:

```sh
$ planck -n 9999
Planck socket REPL listening.
cljs.user=> (def a 3)
#'cljs.user/a
cljs.user=> 
```

At this point, Planck is running. You can use the REPL directly in the terminal. But, you can additionally make TCP connections to it and access the same vars and general runtime environment:

```sh
$ telnet 0 9999
Trying 0.0.0.0...
Connected to localhost.
Escape character is '^]'.
cljs.user=> a
3
cljs.user=> 
```

You can make as many connections as you'd like.

You can exit a socket REPL connection by typing `:repl/quit`, `exit`, `quit`, or `:cljs/quit`.

Socket REPLs can be used by IDEs, for example. It provides a side channel that an IDE can use in order to introspect the runtime environment without interfering with your primary REPL session.

Additionally, socket REPLs could be used in other creative fashions—perhaps facilitating collaborative development without relying on other sharing technologies like `tmux`.

Since socket REPLs are established from environments with unknown terminal capabilities, all of the rich terminal control and coloring (VT-100 and ANSI codes) are turned off for socket REPL sessions.
