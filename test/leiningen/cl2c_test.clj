(ns leiningen.cl2c-test
  (:require [leiningen.cl2c :refer :all]
            [clojure.java.io :as io]
            [chlorine.util :as u]
            [midje.sweet :refer :all]))

(defn pwd []
  (System/getProperty "user.dir"))
