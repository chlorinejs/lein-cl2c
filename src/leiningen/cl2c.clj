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

;; A simple console logger
(defmulti info (fn [& xs] (first xs)))

(defmethod info :compile
  [_ input]
  (println (format "Compiling %s..." (style input :underline))))

(defmethod info :error
  [_ input]
  (println (format (str (style "Error: " :red) " compiling %s")
                   input)))

(defmethod info :timestamp
  [_ timestamp]
  (print "\n" (style (timestamp) :magenta) " "))

(defn handle-known-exception
  "Handles known exception, returns :FAILED for final user report."
  [e input]
  (info :error input)
  (println (style (:msg e) :blue))
  (doseq [i (range (count (:causes e)))
          :let [cause (nth (:causes e) i)]]
    (print (apply str (repeat (inc i) "  ")))
    (println "caused by " (style cause :yellow)))
  (when-let [trace (:trace e)]
    (print-cause-trace
     trace
     (if *verbose* 10 3)))
  :FAILED)

(defn handle-unknown-exception
  "Handles unknown exception, returns :FAILED for final user report."
  [e input]
  (println
   (format (str (style "Unknown error: " :red) " compiling %s")
           input))
  (print-cause-trace
   e (if *verbose* 10 3))
  :FAILED)

(defn handle-timeout-exception
  "Handles timeout exception, returns :FAILED for final user report."
  [e input]
  (println
   (format (str (style "Error: " :red) " Timeout compiling %s")
           input))
  :FAILED)

(defn cl2->js
  "Compiles .cl2 source file to javascript code (string)"
  [input]
  (str
   (when *timestamp*
     (eval `(js (console.log "Script compiled at: "
                             ~(timestamp)))))
   (if *strategy*
     (compile-with-states input *strategy*)
     (bare-compile input))))

(defn hic->html
  "Compiles .hic source file to HTML code (string)"
  [input]
  (let [content (-> input
                    slurp
                    read-string)]
    (str (when (or (and (list? content)
                        (vector? (first content))
                        (= :html (ffirst content)))
                   (and (vector?  content)
                        (= :html (first content))))
           "<!DOCTYPE html>")
         (html content))))

(defn ensure-parent-dir
  "Creates parent directories if necessary."
  [f]
  (when-not (.isDirectory (.getParentFile f))
    (.mkdirs (.getParentFile f))))
