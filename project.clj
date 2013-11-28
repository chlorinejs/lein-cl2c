(defproject lein-cl2c "0.0.1-SNAPSHOT"
  :description "Leiningen plugin to compile ChlorineJS to javascript"
  :url "https://github.com/chlorinejs/lein-cl2c"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[filevents "0.1.0"]
                 [myguidingstar/clansi "1.3.0"]
                 [chlorine "1.6.4-SNAPSHOT"]
                 [core-cl2 "0.9.0-SNAPSHOT"]
                 [pathetic "0.5.1"]
                 [fs "1.3.2"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]
                                  [lein-midje "3.0.1"]]}}
  :cl2c {:named-build
         { ;; where to check for changes?
          :watch ["src-cl2", "test-cl2", "node_modules"]
          ;; sometimes we don't want to compile all found files
          ;; but only some of them. Patterns can be combined together
          ;; with `and` and `or` to make complex filter.
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
  :eval-in-leiningen true)
