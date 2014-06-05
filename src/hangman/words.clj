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

(def db (atom nil))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Triples Generation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: this is somewhat ugly.

(defn word-triples [word]
  (set (conj
         (map #(apply tuple %)
           (apply concat
             (for [c (map char (range (int \A) (inc (int \Z))))
                   :let [posns (util/positions #(= % c) word)]]
               (if (empty? posns)
                 (list (list word c -1))
                 (map #(conj (list c %) word) posns)))))
         (tuple word :length (count word)))))

;; (word-triples "abccddd")

(defn word-collection-triples [coll]
  (r/fold clojure.set/union clojure.set/union
    (mapv word-triples coll)))

;; (corpus/with-corpus []
;;   (count (word-collection-triples (:all-words corpus/*corpus*))))
    
(defn file-triples [filename]
  (r/fold clojure.set/union clojure.set/union
    (mapv word-triples (corpus/words-from-file filename))))

;; (util/run-and-measure-timing 
;;   (count (file-triples corpus/+default-corpus-file+)))
;;
;;  => {:response         4511728,
;;      :start-time 1401197847802,
;;      :end-time   1401197881150,
;;      :time-taken         33348}

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Database Management
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-graph! [graph]
  (swap! db (constantly graph)))

(defn clear-db! []
  (set-graph! nil))

(defn build-db!
  ([]
     (build-db! +default-corpus-file+))
  ([filename]
     (set-graph! (make-graph (file-triples filename)))
     @db))

;;;
;;  These are the actual "top-level" database API functions which are pleasantly
;; trivial to implement using the facilities we have created for parsing and
;; graph construction.  Primarily, these are responsible for atomic interaction
;; with global state, which is limited to only the single var 'db'.
;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Hangman Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn words-of-length [graph length]
  (set (map s (query graph nil :length length))))


;; (util/run-and-measure-timing
;;   (words-of-length @db 2))
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

(defn words-excluding-letter [graph letter]
  (set (map s (query graph nil letter -1))))

(defn words-excluding [graph & letters]
  (apply clojure.set/intersection 
    (filter identity
      (map (partial words-excluding-letter graph) letters))))

(defn words-with-letter-position [graph letter position]
  (set (map s (query graph nil letter position))))


;; (util/run-and-measure-timing
;;   (words-excluding @db \A ))



;; (util/run-and-measure-timing
;;   (clojure.set/intersection
;;     (words-of-length @db 3)
;;     (words-excluding @db \A \E \I \O)
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
;;     (words-of-length @db 3)
;;     (words-excluding @db \A \E \I \O)
;;     (words-with-letter-position @db \F 0)
;;     ))
;;
;;  => {:response #{"FRY" "FLY" "FUG" "FUD" "FLU" "FUN" "FUB" "FUR"},
;;      :start-time 1401748612893,
;;      :end-time 1401748613531,
;;      :time-taken 638}


;; (util/run-and-measure-timing
;;   (clojure.set/intersection
;;     (words-of-length @db 3)
;;     (words-excluding @db \A \E \I \O)
;;     (words-with-letter-position @db \F 0)
;;     (words-with-letter-position @db \U 1)
;;     ))
;;
;;  => {:response #{"FUG" "FUD" "FUN" "FUB" "FUR"},
;;      :start-time 1401748827035,
;;      :end-time 1401748827689,
;;      :time-taken 654}



