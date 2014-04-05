(defproject clj-hangman "0.1.0-SNAPSHOT"
  :min-lein-version  "2.0.0"
  :source-paths ["src/"]
  ;;  :java-source-paths ["third-party/com/factual/hangman"]
  :description "Hangman for the 21st Century"
  :url "http://github.com/danlentz/clj-hangman"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure                "1.5.1"]
                 [org.codehaus.jsr166-mirror/jsr166y "1.7.0"] 
              ;; [immutable-bitset                   "0.1.5"]
                 [iota                               "1.1.1"]
                 [print-foo                          "0.4.2"]
                 [org.clojure/tools.logging          "0.2.6"]])

