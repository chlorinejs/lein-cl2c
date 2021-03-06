(ns leiningen.cl2c
  (:require [clojure.walk :refer [postwalk]]
            [clojure.java.io :as io]
            [chlorine.reader :refer [sexp-reader]]
            [pathetic.core :refer [relativize]]
            [chlorine.js :refer :all]
            [chlorine.prelude :refer [compile-file-with-states]]
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

(defn output-file-for
  "Generate .html and .js file names from .hic and .cl2 ones."
  [input-file path-map]
  (replace-map input-file path-map))

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
     (compile-file-with-states *strategy* input)
     (compile-file-with-states "bare" input))))

(defn hic->html
  "Compiles .hic source file to HTML code (string)"
  [input]
  (with-out-str
    (with-open [in (sexp-reader input)]
      (loop [expr (read in false :eof)]
        (when (not= expr :eof)
          (when (or (and (list? expr)
                         (vector? (first expr))
                         (= :html (ffirst expr)))
                    (and (vector?  expr)
                         (= :html (first expr))))
            (print "<!DOCTYPE html>"))
          (when-let [s (html expr)]
            (print s))
          (recur (read in false :eof)))))))

(defn ensure-parent-dir
  "Creates parent directories if necessary."
  [f]
  (when-not (.isDirectory (.getParentFile f))
    (.mkdirs (.getParentFile f))))

(defn delete-output
  "Delete output files when their source files are deleted."
  [f]
  (.delete (io/file (output-file-for f *path-map*))))

(defn compile-one [file]
  (let [input (.getAbsolutePath (io/file file))
        output (io/file (output-file-for input *path-map*))]
    (info :timestamp timestamp)
    (info :compile input)

    (try+
     (ensure-parent-dir output)
     (spit output
           (with-timeout *timeout*
             ;; determine source type: hiccup or chlorine?
             (if (.endsWith input ".cl2")
               (cl2->js input)
               (hic->html input))))
     (println (style "Done!" :green))
     :PASSED
     (catch map? e
       (handle-known-exception e input))

     (catch java.util.concurrent.TimeoutException e
       (handle-timeout-exception e input))

     (catch Throwable e
       (handle-unknown-exception e input)))))

(defn compile-files [files]
  (set-terminal-title "Compiling files...")
  (let [status (map compile-one files)
        total (count status)
        failures (count (filter #(= :FAILED %) status))]
    (if (= 0 failures)
      (set-terminal-title (format "✔ %d files compiled" total))
      (set-terminal-title (format "%d/%d ✘" failures total)))))

(defn filter-files
  "Find files matching given filter function."
  [root dirs file-filter]
  (let [root (io/file root)
        dirs (map #(io/file root %) dirs)]
    (mapcat
     (fn [dir]
       (->> dir
            file-seq
            (map #(.getAbsolutePath %))
            (map #(relativize (.getAbsolutePath root) %))
            (filter file-filter)))
     dirs)))

(defn transform-filter
  "Transforms brief :filter descriptions in project.clj into
  runnable Clojure code."
  [expr]
  `(fn [~'s]
     ~(postwalk (fn [x] (if (string? x)
                         `(re-find (re-pattern ~x) ~'s)
                         x))
               expr)))

(defn make-filter
  "Makes filter function from :filter descriptions."
  [expr]
  (eval (transform-filter expr)))

(defn read-path-map
  "Converts path-map from (more readable) format in project.clj to
  format that works with replace-map."
  [v]
  (map #(vector (first %) (nth % 2))
       (partition 3 v)))

(defmacro with-build-options
  "Binding compiler-specific dynamic vars with value from build
   parameters."
  [m & body]
  `(binding [*print-pretty*     (= :pretty (:optimizations ~m))
             *symbol-map*       (or (:symbol-map   ~m) *symbol-map*)
             *timeout*          (or (:timeout ~m)      *timeout*)
             *paths*            (or (:paths ~m) [])
             *strategy*         (:strategy ~m)
             *path-map*         (concat [[#".cl2$" ".js"]
                                         [#".hic$" ".html"]]
                                        (read-path-map (:path-map ~m)))]
     ~@body))

(defn build-once [root build]
  (let [file-filter (make-filter
                     (concat '(and (or ".+\\.cl2$"  ".+\\.hic$"))
                             [(:filter build)]))
        files (filter-files root (:watch build) file-filter)]
    (println "Files:" files)
    (compile-files files)))

(defn select-builds
  "Filters builds defined in project.clj with build names given
  in command-line arguments. If no argument was given, returns
  all found builds."
  [all-builds build-names]
  (if (seq build-names)
    ;; if some build names are given, keywordize them
    ;; and find in project.clj
    (select-keys all-builds (map keyword build-names))
    ;; no build specified? -> build them all
    all-builds))

(defn make-watcher-fn
  "Makes a watcher function that can be passed to filevents.core/watch."
  [root build]
  (fn [kind file]
    (let [file (relativize
                root
                (.getAbsolutePath file))
          file-filter (make-filter
                       (concat '(and (or ".+\\.cl2$"  ".+\\.hic$"))
                               [(:filter build)]))]
      (when (file-filter file)
        (build-once root build)
        (when (= :deleted kind)
          (println "Deleting output file for " file)
          (delete-output file))))))

(defn print-manual-rerun-guide []
  (println "Press [Enter] to rerun builds."))

(defn once [project & build-names]
  (println "Builds: " build-names)
  (doseq [[bname build] (select-builds (:cl2c project) build-names)]
    (with-build-options build
      (println "Building" bname)
      (build-once (:root project) build))))

(defn auto [project & build-names]
  (apply once project build-names)
  (print-manual-rerun-guide)
  (doseq [[bname build] (select-builds (:cl2c project) build-names)]
    (with-build-options build
      (apply
       watch
       (make-watcher-fn (:root project) build)
       (:watch build))
      (while true
        (when (read-line)
          (apply once project build-names)
          (print-manual-rerun-guide))))))

(defn cl2c
  "Compile chlorine/hiccup sources to javascript/html files."
  [project & args]
  (cond (= (first args) "once") (apply once project (rest args))
        (= (first args) "auto") (apply auto project (rest args))))
