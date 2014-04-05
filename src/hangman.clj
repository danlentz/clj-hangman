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

  

(defn play [strategy-name]
  (with-corpus []
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
          (recur g (.updateStrategy s g x)))))))



;; (apply + (repeatedly 1000
;;            #(.currentScore (play :frequency))))
;;  => 9227

;; (apply + (repeatedly 1000
;;            #(.currentScore (play :random))))
;;  => 22698

;; (apply + (repeatedly 1000
;;            #(.currentScore (play :longshot))))
;;  => 24952

