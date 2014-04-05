(ns lethal-injection.strategy
  (:refer-clojure :exclude [])
  (:require [clojure.pprint :as pp])
  (:require [clojure.string :as str])
  (:require [clojure.core.reducers :as r])
  (:require [clojure.tools.logging :as log])
  (:require [lethal-injection.util  :as util])
  (:require [lethal-injection.bitop :as bitop])
  (:use     [lethal-injection.util :only [returning returning-bind indexed]])
  (:use     [lethal-injection.corpus])
  (:use     [lethal-injection.inverted])
  (:use     [lethal-injection.game])
  (:use     [print.foo])
  (:import  [lethal_injection.game LetterGuess WordGuess])
  )


(definterface Strategem
  (nextGuess      [game])   
  (updateStrategy [game guess]))


(defrecord LongshotStrategy [posswords]
  Strategem
  (nextGuess [_ game]
    (->WordGuess
      (nth posswords (rand-int (count posswords)))))
  (updateStrategy [_ game guess]
    (->LongshotStrategy
      (remove #(= % (:w guess)) posswords))))
  


(defmulti make-strategy (comp first vector))
                          

(defmethod make-strategy :longshot [name game]
  (->LongshotStrategy
    (words-of-length *corpus* (.getSecretWordLength game))))

(defn random-element [coll]
  (nth (vec coll) (rand-int (count coll))))

(defrecord RandomStrategy [posswords]
  Strategem
  (nextGuess [_ game]
    (if (= 1  (.numWrongGuessesRemaining game))
      (->WordGuess
        (nth posswords (rand-int (count posswords))))
      (->LetterGuess (random-element
                       (clojure.set/difference
                         (set (all-terms)) 
                         (.getAllGuessedLetters game))))))
  (updateStrategy [_ game guess]
    (condp = (type guess)
      LetterGuess (if (get (.getIncorrectlyGuessedLetters game) (:ch guess))
                    (->RandomStrategy
                      (words-excluding-terms
                        posswords (:ch guess)))
                    (->RandomStrategy
                      (words-including-term-positions
                        posswords
                        (:ch guess)
                        (apply make-posn-mask
                          (term-positions
                            (.getGuessedSoFar game)
                            (:ch guess))))))
      WordGuess    (->RandomStrategy
                     (remove #(= % (:w guess)) posswords)))))



(defmethod make-strategy :random [name game]
  (->RandomStrategy
    (words-of-length *corpus* (.getSecretWordLength game))))



(defrecord FrequencyStrategy [posswords]
  Strategem
  (nextGuess [_ game]
    (let [tfd (term-frequency-distribution posswords)
          besttf (first (seq (for [tf (sort-by second > (seq tfd))
                              :when (get (clojure.set/difference
                                           (set (all-terms)) 
                                           (.getAllGuessedLetters game))
                                      (first tf))]
                               tf)))]
      (if (or
            (= 1 (count posswords))
            (and
              (= 1 (.numWrongGuessesRemaining game))
              (>=
                (/ 1 (count posswords))
                (/ (second besttf) (count posswords)))))
        (->WordGuess
          (nth posswords (rand-int (count posswords))))
        (->LetterGuess  (first  besttf)))))
  (updateStrategy [_ game guess]
    (condp = (type guess)
      LetterGuess (if (get (.getIncorrectlyGuessedLetters game) (:ch guess))
                    (->FrequencyStrategy
                      (words-excluding-terms posswords (:ch guess)))
                    (->FrequencyStrategy
                      (words-including-term-positions posswords (:ch guess)
                        (apply make-posn-mask
                          (term-positions
                            (.getGuessedSoFar game) (:ch guess))))))
      WordGuess   (->FrequencyStrategy
                    (remove #(= % (:w guess)) posswords)))))


(defmethod make-strategy :frequency [name game]
  (->FrequencyStrategy
    (words-of-length *corpus* (.getSecretWordLength game))))



