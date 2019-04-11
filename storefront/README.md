# Redis Streams Storefront

## Usage

In addition to the instructions below, check out the Makefile for some
other development conveniences.

### Run

``` bash
make run
```

Then visit http://localhost:8080.

Production logs are written to `stdout` by default, and are
configurable via `config/logback.xml`.

See `config/config.edn` for the environment variables used to
configure the service.

### Development

Development works best when running either the UI workflow alone, or
both the UI and backend workflows together.

#### UI

To run a [Shadow CLJS](http://shadow-cljs.org/) server, complete with
a watcher that auto compiles changed files and automatically reloads
them in the browser:

``` bash
make cljs-dev
```

You can view the UI (without the backend service running) at
http://localhost:8700.

For more information, see `shadow-cljs.edn`.

#### Backend

This project uses the [Clojure CLI
Tools](https://clojure.org/guides/deps_and_cli). To launch a REPL for
the backend service component:

``` bash
make clj-dev
```

You can use the environment variable `CLJ_REPL_ALIAS` to add
additional Clojure CLI aliases to this dev workflow, for example:

``` bash
CLJ_REPL_ALIAS=:cider-nrepl make clj-dev
```

Once you have a Clojure REPL running in your environment of choice:

``` clojure
;;; In the REPL
(dev)
(go)
```

Then, navigate to http://localhost:8080 to view the running site. The
UI-reloading capability described above continues to function when
viewing the UI on this service port.

After you change something, you can:

``` clojure
(reset)
```

To refresh the code and restart the system, as per [Stuart Sierra's
component library](https://github.com/stuartsierra/component).

Development logs are written out to files in `logs/`, and are
configurable at `dev/logback-dev.xml`.

For more information, see `deps.edn`.

## License

Copyright 2019 Bobby Calderwood

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
