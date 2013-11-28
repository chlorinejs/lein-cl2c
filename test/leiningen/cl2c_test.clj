(ns leiningen.cl2c-test
  (:require [leiningen.cl2c :refer :all]
            [clojure.java.io :as io]
            [chlorine.util :as u]
            [midje.sweet :refer :all]))

(defn pwd []
  (System/getProperty "user.dir"))

(facts "transform-filter test"
  (transform-filter `(and (or ".cl2$" ".hic$")
                          "match-me"))
  => `(fn [~'s]
        (and (or (re-find (re-pattern ".cl2$") ~'s)
                 (re-find (re-pattern ".hic$") ~'s))
             (re-find (re-pattern "match-me") ~'s))))

(facts "filter files with .cl2 or .hic extensions and has
  \"match-me\" in its name."
  (filter (make-filter '(and (or ".cl2$" ".hic$")
                             "match-me"))
          ["dir/match-me.cl2"
           "dir/match-me.hic"
           "dir/match-me.js"
           "dir/match-me.html"
           "dir/match-no-me.clj"
           "dir/match-no-me.hic"])
  => ["dir/match-me.cl2"
      "dir/match-me.hic"])
