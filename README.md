# lein-cl2c

Leiningen plugin to compile ChlorineJS to Javascript and Hiccup to HTML.

[![Build Status](https://travis-ci.org/chlorinejs/lein-cl2c.png?branch=master)](https://travis-ci.org/chlorinejs/lein-cl2c)

## Installation

### Clojars

In your `:plugins`:

![Latest version](https://clojars.org/lein-cl2c/latest-version.svg)

## Usage

Configure `cl2c` in your `project.clj`:

```clojure
:cl2c {:named-build
         { ;; where to check for changes?
          :watch ["src-cl2", "test-cl2", "node_modules"]
          ;; sometimes we don't want to compile all found files
          ;; but only some of them. Patterns can be combined together
          ;; with `and` and `or`.
          :filter (or "src-cl2/" "test-cl2/")
          ;; - How the compiler figure out its output files from given input?
          ;; - It's surely a rule
          :path-map ["foo" => "fu_out",
                     "bar" => "bah_out"]
          ;; where to find cl2 sources?
          :paths ["node_modules/"]
          ;; what strategy to use?
          :strategy "prod"
          ;; some files may take too long to compile. We need a limit
          :timeout 2000
          ;; how are output files optimized?
          ;; available options: :advanced, :simple (default) and :pretty
          :optimizations :pretty}}
```

Compile ChlorineJS files with:

    $ lein cl2c once

or:

    $ lein cl2c once :some-names :named-build

or:
    $ lein cl2c auto

or:

    $ lein cl2c auto :some-names :named-build

## License

Copyright © 2013 Hoang Minh Thang.

Distributed under the Eclipse Public License, the same as Clojure.
