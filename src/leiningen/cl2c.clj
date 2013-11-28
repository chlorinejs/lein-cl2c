(ns leiningen.cl2c
  (:require [clojure.walk :refer [postwalk]]
            [clojure.java.io :as io]
            [pathetic.core :refer [relativize]]
            [chlorine.js :refer :all]
            [slingshot.slingshot :refer :all]
            [fs.core :as fs]
            [chlorine.util :refer [replace-map with-timeout timestamp
                                  *paths*]]
            [filevents.core :refer [watch]]
            [hiccup.core :refer [html]]
            [clojure.stacktrace :refer [print-cause-trace]]
            [clansi.core :refer [*use-ansi* style]]))

(def ^:dynamic *verbose* false)
(def ^:dynamic *timeout* 2000)
(def ^:dynamic *path-map* nil)
(def ^:dynamic *strategy* nil)
(def ^:dynamic *timestamp* false)
