(ns hangman.game
  (:refer-clojure :exclude [])
  (:require [clojure.pprint :as pp])
  (:require [clojure.string :as str])
  (:require [clojure.core.reducers  :as r])
  (:require [clojure.tools.logging  :as log])
  (:require [hangman.util  :as util])
  (:require [hangman.words :as words])
  (:use     [hangman.util :only [returning returning-bind indexed]])
  (:use     [print.foo]))


(def +unknown+ \_)


(definterface IGame
  (getMaxWrongGuesses            [])
  (getGuessedSoFar               [])
  (getCorrectlyGuessedLetters    [])
  (getIncorrectlyGuessedLetters  [])
  (getIncorrectlyGuessedWords    [])
  (guessLetter
;;    "Guess the specified letter and update the game state accordingly
;;    returns the string representation of the current game state (which
;;    will contain MYSTERY_LETTER in place of unknown letters)"
    [ch])
  (guessWord
;    "Guess the specified word and update the game state
;    accordingly. returns The string representation of the current game
;    state (which will contain MYSTERY_LETTER in place of unknown
;    letters)"
    [str])
  (currentScore
 ;   "return The score for the current game state"
    [])
  (assertCanKeepGuessing         [])
  (gameStatus                    [])
  (numWrongGuessesMade           [])
  (numWrongGuessesRemaining      [])
  (getAllGuessedLetters          [])
  (getSecretWordLength           []))


(deftype HangmanGame [secret
                      ^:volatile-mutable sofar
                      max-incorrect
                      ^:volatile-mutable correct
                      ^:volatile-mutable incorrect
                      ^:volatile-mutable bad-guess]
  IGame
  
  (getMaxWrongGuesses [this]
    max-incorrect)

  (currentScore [this]
    (if (= (.gameStatus this) :game-lost)
      25
      (+ (.numWrongGuessesMade this) (count correct))))

  (assertCanKeepGuessing [this]
    (if-not (= (.gameStatus this) :keep-guessing)
      (util/exception IllegalStateException
        "Cannot keep guessing in current game state:" (.gameStatus this))
      true))

  (gameStatus [this]
    (cond
      (=  secret (.getGuessedSoFar this))            :game-won
      (>= (.numWrongGuessesMade this) max-incorrect) :game-lost
      :else                                          :keep-guessing))

  (numWrongGuessesMade [this]
    (+ (count incorrect) (count bad-guess)))

  (numWrongGuessesRemaining [this]
    (- (.getMaxWrongGuesses this) (.numWrongGuessesMade this)))

  (getGuessedSoFar [this]
    (apply str sofar))

  (getCorrectlyGuessedLetters [this]
    (set correct))

  (getIncorrectlyGuessedLetters [this]
    (set incorrect))

  (getAllGuessedLetters [this]
    (clojure.set/union correct incorrect))

  (getIncorrectlyGuessedWords [this]
    (set bad-guess))

  (getSecretWordLength [this]
    (count secret))

  (guessLetter [this ch]
    (.assertCanKeepGuessing this)
    (if ((.getAllGuessedLetters this) ch)
      (.getGuessedSoFar this)
      (do
        (let [newsofar (vec (for [i (range (count secret))
                                  :let [c (nth secret i)]]
                              (if (= c ch)
                                c
                                (nth sofar i))))]
          (if (= sofar newsofar)
            (set! incorrect (conj incorrect ch))
            (do
              (set! correct (conj correct ch))
              (set! sofar newsofar))))
        (.getGuessedSoFar this))))

  (guessWord [this g]
    (.assertCanKeepGuessing this)
    (let [g (.toUpperCase g)]
      (if (= secret g)
        (set! sofar (vec secret))
        (set! bad-guess (conj bad-guess g)))
      (.getGuessedSoFar this)))

  Object
  
  (toString [this]
    (str
      (.getGuessedSoFar this)
      "; score="
      (.currentScore this)
      "; status="
      (.gameStatus this))))


(defn new-game [secret-word max-wrong-guesses]
     (HangmanGame.
       secret-word
       (vec (repeat (count secret-word) +unknown+))
       max-wrong-guesses
       #{} #{} #{}))



(definterface Guess
  (makeGuess [game]))

(defrecord LetterGuess [ch]
  Guess
  (makeGuess [_ game]
    (.guessLetter game ch))
  Object
  (toString [_]
    (str "LetterGuess[" ch "]")))


(defrecord WordGuess [w]
  Guess
  (makeGuess [_ game]
    (.guessWord game w))
  Object
  (toString [_]
    (str "WordGuess[" w "]")))
  



