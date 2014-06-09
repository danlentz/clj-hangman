(ns hangman.words
  (:refer-clojure :exclude [])
  (:require [iota])
  (:require [clojure.pprint :as pp])
  (:require [clojure.string :as str])
  (:require [clojure.core.reducers :as r])
  (:require [clojure.tools.logging :as log])
  (:require [hangman.util  :as util])
  (:use     [hangman.triples])
  (:use     [clj-tuple])
  (:use     [print.foo]))




(def +default-corpus-file+ "resources/words.txt")


(defn words-from-file
  ([]
     (words-from-file +default-corpus-file+))
  ([filename]
     (into []
       (r/fold concat conj
         (r/map str/upper-case
           (r/filter identity
             (iota/vec filename)))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Global State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def word-db (atom nil))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Triples Generation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: this is somewhat ugly.

(defn word-triples [word]
  (set (concat
         (map #(apply tuple %)
           (apply concat
             (for [c (map char (range (int \A) (inc (int \Z))))
                   :let [posns (util/positions #(= % c) word)]]
               (if (empty? posns)
                 (list (list word c -1))
                 (map #(conj (list c %) word) posns)))))
         [(tuple word :length (count word))
          (tuple word :type   :WORD)]
         )))

;; (word-triples "abccddd")

(defn word-collection-triples [coll]
  (r/fold clojure.set/union clojure.set/union
    (mapv word-triples coll)))


(defn file-triples [filename]
  (r/fold clojure.set/union clojure.set/union
    (mapv word-triples (words-from-file filename))))

;; (util/run-and-measure-timing 
;;   (count (file-triples +default-corpus-file+)))
;;
;;  => {:response         4511728,
;;      :start-time 1401197847802,
;;      :end-time   1401197881150,
;;      :time-taken         33348}

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Database Management
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-word-db! [graph]
  (swap! word-db (constantly graph)))

(defn clear-word-db! []
  (set-graph! nil))

(defn build-word-db!
  ([]
     (build-word-db! +default-corpus-file+))
  ([filename]
     (set-word-db! (make-graph (file-triples filename)))
     @word-db))

;;;
;;  These are the actual "top-level" database API functions which are pleasantly
;; trivial to implement using the facilities we have created for parsing and
;; graph construction.  Primarily, these are responsible for atomic interaction
;; with global state, which is limited to only the single atom 'word-db'.
;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Hangman Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn words-of-length [g length]
  (set (map s (query g nil :length length))))


;; (util/run-and-measure-timing
;;   (words-of-length @word-db 2))
;;
;;   =>  {:response #{"PE" "EN" "UH" "SI" "IT" "PI" "FA" "MY" "AM" "BI" "YO"
;;     "MU" "LI" "NU" "AY" "AH" "IF" "HO" "AX" "OD" "NE" "ON" "OW" "EX" "ME"
;;     "BO" "JO" "KA" "IS" "TA" "EH" "AT" "EL" "XU" "OY" "UP" "MM" "YE" "AN"
;;     "MI" "UM" "PA" "UT" "GO" "BY" "XI" "MO" "AR" "AW" "TI" "ID" "BA" "TO"
;;     "SH" "MA" "OE" "AD" "WO" "OM" "HE" "SO" "DO" "AL" "LA" "DE" "AS" "AA"
;;     "NO" "ET" "AG" "BE" "OX" "OR" "EM" "ED" "WE" "US" "HA" "AB" "YA" "EF"
;;     "RE" "IN" "ES" "OS" "UN" "LO" "HI" "ER" "AE" "HM" "AI" "OP" "OF" "OH"
;;     "NA"},
;;        :start-time 1401743282516,
;;        :end-time   1401743282516,
;;        :time-taken 0}

(defn words-excluding-letter [g letter]
  (set (map s (query g nil letter -1))))

(defn words-excluding [g & letters]
  (apply clojure.set/intersection 
    (filter identity
      (map (partial words-excluding-letter g) letters))))

(defn words-with-letter-position [g letter position]
  (set (map s (query g nil letter position))))

(defn all-words [g]
  (mapv s (query g nil :type :WORD)))

(defn random-element [coll]
  (nth coll (rand-int (count coll))))

(defn random-word [g]
  (random-element (all-words g)))

(defn random-words [g n]
  (loop [words #{} all (all-words g)]
    (if (= (count words) n)
      words
      (recur (conj words (random-element all)) all))))




;; (util/run-and-measure-timing
;;   (random-word @word-db))

;; (util/run-and-measure-timing
;;   (random-words @word-db 10))

;; (util/run-and-measure-timing
;;   (words-excluding @word-db \A ))

;; (util/run-and-measure-timing
;;   (clojure.set/intersection
;;     (words-of-length @word-db 3)
;;     (words-excluding @word-db \A \E \I \O)
;;     ))
;;
;;   => {:response #{"GUY" "MUM" "URD" "JUG" "NTH" "HUP" "RUG" "PUR"
;; "BUG" "SUM" "STY" "DUH" "DUN" "RUB" "HMM" "HUM" "GYP" "FRY" "TRY"
;; "SHH" "GUV" "NUS" "WHY" "PUD" "HUT" "SUN" "HUN" "DUD" "LUG" "WUD"
;; "GUN" "TUG" "HUH" "GUT" "CUP" "SHY" "TUN" "URB" "BUS" "SUB" "TUX"
;; "CUB" "DRY" "CRY" "JUN" "SPY" "UNS" "THY" "UPS" "TUP" "FLY" "PUB"
;; "BUM" "NUT" "PHT" "HUB" "SKY" "CUT" "TUT" "FUG" "PYX" "NUB" "BUY"
;; "CWM" "GYM" "ULU" "UTS" "PUN" "BUD" "UGH" "CUR" "PUG" "PUL" "BUR"
;; "DUB" "DUG" "JUT" "HUG" "SUQ" "LUX" "PUP" "GUL" "FUD" "YUM" "WRY"
;; "CUM" "WYN" "CUD" "TSK" "FLU" "FUN" "YUK" "GNU" "UMM" "BUB" "SYN"
;; "DUP" "TUB" "SUP" "PUS" "FUB" "MUG" "URN" "UMP" "BRR" "VUG" "RUM"
;; "NUN" "MUN" "BYS" "BUN" "RUN" "LUV" "PRY" "GUM" "FUR" "PLY" "BUT"
;; "MUD" "PUT" "SLY" "LUM" "HYP" "MUS" "MUT" "YUP" "JUS" "RUT"},
;;      :start-time 1401745795429,
;;      :end-time 1401745796055,
;;      :time-taken 626}


;; (util/run-and-measure-timing
;;   (clojure.set/intersection
;;     (words-of-length @word-db 3)
;;     (words-excluding @word-db \A \E \I \O)
;;     (words-with-letter-position @word-db \F 0)
;;     ))
;;
;;  => {:response #{"FRY" "FLY" "FUG" "FUD" "FLU" "FUN" "FUB" "FUR"},
;;      :start-time 1401748612893,
;;      :end-time 1401748613531,
;;      :time-taken 638}


;; (util/run-and-measure-timing
;;   (clojure.set/intersection
;;     (words-of-length @word-db 3)
;;     (words-excluding @word-db \A \E \I \O)
;;     (words-with-letter-position @word-db \F 0)
;;     (words-with-letter-position @word-db \U 1)
;;     ))
;;
;;  => {:response #{"FUG" "FUD" "FUN" "FUB" "FUR"},
;;      :start-time 1401748827035,
;;      :end-time 1401748827689,
;;      :time-taken 654}



