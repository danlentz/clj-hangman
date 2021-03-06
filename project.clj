(defproject clj-hangman "0.1.0-SNAPSHOT"
  :min-lein-version  "2.0.0"
  :source-paths ["src/"]
  ;;  :java-source-paths ["third-party/com/factual/hangman"]
  :description "Hangman for the 21st Century"
  :url "http://github.com/danlentz/clj-hangman"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure                "1.6.0"]
                 [org.codehaus.jsr166-mirror/jsr166y "1.7.0"] 
                 [immutable-bitset                   "0.1.6"]
                 [primitive-math                     "0.1.3"]
                 [clj-tuple                          "0.1.5"]
                 [iota                               "1.1.2"]
                 [danlentz/clj-uuid                  "0.0.6-SNAPSHOT"]
                 [print-foo                          "0.5.1"]
                 [org.clojure/tools.logging          "0.2.6"]])

