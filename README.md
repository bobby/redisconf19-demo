# Example and demo code for Bobby Calderwood's talk at RedisConf 2019

## Usage

First, start a Redis server on `6379`:

``` bash
# using homebrew-installed Redis
brew services start redis

# otherwise
redis-server /usr/local/etc/redis.conf # or whatever
```

To run the three services (on Mac OS):

``` bash
# in one shell
cd storefront
make bootstrap
make run

# in another shell
cd barista
make bootstrap
make run
```

## Development Workflow

See the individual READMEs in each service: `storefront/README.md` and `barista/README.md`.

## [License](./LICENSE)

Copyright 2019 Bobby Calderwood

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
