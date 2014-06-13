(ns hangman
  (:refer-clojure :exclude [])
  (:require [clojure.pprint :as pp])
  (:require [hangman.util  :as util])
  (:use     [hangman.util :only [returning-bind]])
  (:use     [hangman.words])
  (:use     [hangman.game])
  (:use     [hangman.strategy])
  (:use     [print.foo]))

  

(defn play
  ([strategy-name] (play strategy-name 1))
  ([strategy-name iter]
     (println "Initializing...")
     (ensure-word-db +default-corpus-file+)
     (println "Playing.")
     (returning-bind
       [score (apply +
                (repeatedly iter
                  #(.currentScore 
                     (loop [game     (new-game (random-word @word-db) 5)
                            strategy (make-strategy strategy-name game)]
                       (prn game)
                       (if-not (= :keep-guessing (.gameStatus game))
                         game
                         (let [x (.nextGuess strategy game)]
                           (prn x)
                           (.makeGuess x game)
                           (recur game
                             (.updateStrategy strategy game x))))))))]
       (println "Final Score: " score))))




;; (util/run-and-measure-timing
;;   (play :longshot 1000))

;; (util/run-and-measure-timing
;;   (play :random 1000))


;; (util/run-and-measure-timing
;;   (play :frequency 1000))
;;
;;  => {:response   7767,
;;      :start-time 1402620872542,
;;      :end-time   1402620887812,
;;      :time-taken 15270}
