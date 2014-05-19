(ns hangman
  (:refer-clojure :exclude [])
  (:require [clojure.pprint :as pp])
  (:require [clojure.string :as str])
  (:require [clojure.core.reducers :as r])
  (:require [clojure.tools.logging :as log])
  (:require [hangman.util  :as util])
  (:require [hangman.bitop :as bitop])
  (:use     [hangman.util :only [returning returning-bind indexed]])
  (:use     [hangman.corpus])
  (:use     [hangman.inverted])
  (:use     [hangman.game])
  (:use     [hangman.strategy])
  (:use     [print.foo]))

  

(defn play
  ([strategy-name] (play strategy-name 1))
  ([strategy-name iter]
     (with-corpus []
       (apply +
         (repeatedly iter
           #(.currentScore 
              (loop [g (new-game (random-word *corpus*) 5)
                     s (make-strategy strategy-name g)]
                (if-not (= :keep-guessing (.gameStatus g))
                  g
                  (let [x (.nextGuess s g)]
                    (prn g)
                    (prn x)
                    (.makeGuess x g)
                    (prn g)
                    (prn)
                    (recur g (.updateStrategy s g x)))))))))))


;; (util/with-timing (play :frequency 1000))
;;  => [9402 2432927.496]

;; (util/with-timing (play :frequency 1000))
;;  => [8763 2760980.378]


;; (apply + (repeatedly 1000
;;            #(.currentScore (play :random))))
;;  => 22698

;; (apply + (repeatedly 1000
;;            #(.currentScore (play :longshot))))
;;  => 24952

