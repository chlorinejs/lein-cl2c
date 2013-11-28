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

(defn gen-state
  "Compiles a pre-defined Chlorine strategy, returns that state"
  [strategy]
  (binding [*temp-sym-count* (ref 999)
            *macros*         (ref {})]
    (let [inclusion (eval `(js (load-file
                                ~(str "r:/strategies/" strategy ".cl2"))))]
      {:temp-sym-count @*temp-sym-count*
       :macros @*macros*
       :inclusion inclusion})))

(def ^{:doc "Pre-compiles Chlorine strategies once
  and saves states to this var."}
  prelude
  {"dev"  (gen-state "dev")
   "prod" (gen-state "prod")
   "prod-compat" (gen-state "prod-compat")})

(defn compile-with-states
  "Compiles a file using pre-compiled states."
  [f state-name]
  (let [state (get prelude state-name)]
    (binding [*temp-sym-count*  (ref (:temp-sym-count state))
              *macros*          (ref (:macros state))]
      (str
       (:inclusion state) "\n\n"
       (tojs' f)))))

(defn output-file-for
  "Generate .html and .js file names from .hic and .cl2 ones."
  [input-file path-map]
  (replace-map input-file path-map))

(defn bare-compile
  "Compiles a file without using pre-compiled states."
  [file]
  (binding [*temp-sym-count* (ref 999)
            *macros*         (ref {})]
    (tojs' file)))

(defn set-terminal-title
  "Sets title of current terminal window."
  [new-title]
  (printf "%s]2;%s%s" (char 27) new-title (char 7))
  (println))
