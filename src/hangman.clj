(ns hangman
  (:refer-clojure :exclude [])
  (:require [clojure.pprint :as pp])
  (:require [hangman.util  :as util])
  (:use     [hangman.util :only [returning returning-bind indexed]])
  (:use     [hangman.words])
  (:use     [hangman.game])
  (:use     [hangman.strategy])
  (:use     [print.foo]))

  

(defn play
  ([strategy-name] (play strategy-name 1))
  ([strategy-name iter]
     (ensure-word-db +default-corpus-file+)
     (apply +
       (repeatedly iter
         #(.currentScore 
            (loop [game     (new-game (random-word @word-db) 5)
                   strategy (make-strategy strategy-name game)]
                (if-not (= :keep-guessing (.gameStatus game))
                  game
                  (let [x (.nextGuess strategy game)]
                    (prn game)
                    (prn x)
                    (.makeGuess x game)
                    (prn game)
                    (prn)
                    (recur game (.updateStrategy strategy game x))))))))))




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
