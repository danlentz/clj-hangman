(ns hangman
  (:refer-clojure :exclude [])
  (:require [clojure.pprint :as pp])
  (:require [clojure.core.reducers :as r])
  (:require [hangman.util  :as util])
  (:use     [hangman.util :only [returning-bind]])
  (:use     [hangman.words])
  (:use     [hangman.game])
  (:use     [hangman.strategy]))


  
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Standard Play
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn play
  ([strategy-name] (play strategy-name 1))
  ([strategy-name iter]
     (println "Initializing...")
     (ensure-word-db +default-corpus-file+)
     (println "Playing.")
     (returning-bind
       [score (reduce +
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
;;   (play :frequency 1000))
;;
;;  => {:response   7688,
;;      :start-time 1402620872542,
;;      :end-time   1402620887812,
;;      :time-taken 15270}


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Concurrent Play 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def +default-parallelism+ 8)

(defn play-mp
  ([s-name] (play-mp s-name 1))
  ([s-name iter]
     (println "Initializing...")
     (ensure-word-db +default-corpus-file+)
     (println "Playing.")
     (returning-bind
       [score (let [words (random-words @word-db iter)]
                (r/fold (max +default-parallelism+
                          (int (/ (count words) +default-parallelism+)))
                  + (fn
                      ([] 0)
                      ([acc word]
                         (+ acc (.currentScore
                                  (loop [game     (new-game word 5)
                                         strategy (make-strategy s-name game)]
                                    (if-not (= :keep-guessing
                                              (.gameStatus game))
                                      game
                                      (let [x (.nextGuess strategy game)]
                                        (.makeGuess x game)
                                        (recur game
                                          (.updateStrategy strategy
                                            game x)))))))))
                  (vec words)))]
       (println "Final Score: " score))))
       

;; (util/run-and-measure-timing
;;   (play-mp :frequency 1000))
;;
;;  => {:response   7486,
;;      :start-time 1402657459985,
;;      :end-time   1402657465764,
;;      :time-taken 5779}




