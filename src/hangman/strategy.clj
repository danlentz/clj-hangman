(ns hangman.strategy
  (:refer-clojure :exclude [])
  (:require [clojure.pprint :as pp])
  (:require [hangman.util  :as util])
  (:use     [hangman.words])
  (:use     [hangman.util :only [returning returning-bind indexed]])
  (:use     [hangman.game])
  (:use     [print.foo])
  (:import  [hangman.game LetterGuess WordGuess]))



(definterface Strategem
  (nextGuess      [game])   
  (updateStrategy [game guess]))


(defmulti make-strategy (comp first vector))
                          

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Longshot Strategy
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord LongshotStrategy [posswords]
  Strategem
  (nextGuess [_ game]
    (->WordGuess
      (random-element posswords)))
  (updateStrategy [_ game guess]
    (->LongshotStrategy
      (remove #(= % (:w guess)) posswords))))


(defmethod make-strategy :longshot [name game]
  (->LongshotStrategy
    (words-of-length @word-db (.getSecretWordLength game))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Random Letters Strategy
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord RandomStrategy [posswords]
  Strategem
  (nextGuess [_ game]
    (if (= 1  (.numWrongGuessesRemaining game))
      (->WordGuess
        (random-element posswords))
      (->LetterGuess (random-element
                       (clojure.set/difference
                         (all-letters)
                         (.getAllGuessedLetters game))))))
  (updateStrategy [_ game guess]
    (condp = (type guess)
      LetterGuess (if (get (.getIncorrectlyGuessedLetters game) (:ch guess))
                    (->RandomStrategy
                      (clojure.set/intersection
                        posswords
                        (words-excluding
                          @word-db (:ch guess))))
                    (->RandomStrategy
                      (clojure.set/intersection
                        posswords
                        (apply words-with-letter-positions @word-db (:ch guess)
                          (letter-positions (.getGuessedSoFar game)
                            (:ch guess))))))
      WordGuess    (->RandomStrategy
                     (remove #(= % (:w guess)) posswords)))))



(defmethod make-strategy :random [name game]
  (->RandomStrategy
    (words-of-length @word-db (.getSecretWordLength game))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Letter Frequency in Remaining Possible Words Strategy
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord FrequencyStrategy [posswords]
  Strategem
  (nextGuess [_ game]
    (let [tfd    (letter-frequency-distribution posswords)
          besttf (first (seq (for [tf (sort-by second > (seq tfd))
                                   :when (get (clojure.set/difference
                                                (all-letters) 
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
          (random-element posswords))
        (->LetterGuess  (first  besttf)))))
  (updateStrategy [_ game guess]
    (condp = (type guess)
      LetterGuess (if (get (.getIncorrectlyGuessedLetters game) (:ch guess))
                    (->FrequencyStrategy
                      (clojure.set/intersection
                        posswords
                        (words-excluding @word-db (:ch guess))))
                    (->FrequencyStrategy
                      (clojure.set/intersection
                        posswords
                        (apply words-with-letter-positions @word-db (:ch guess)
                          (letter-positions (.getGuessedSoFar game)
                            (:ch guess))))))
      WordGuess   (->FrequencyStrategy
                    (remove #(= % (:w guess)) posswords)))))


(defmethod make-strategy :frequency [name game]
  (->FrequencyStrategy
    (words-of-length @word-db (.getSecretWordLength game))))



