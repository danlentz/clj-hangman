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
;; Words/Letters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all-letters []
  (into (sorted-set)
    (map char (range (int \A) (inc (int \Z))))))

(defn make-position-mask [& positions]
  (if (empty? positions)
    0
    (apply bit-or
      (conj (for [x positions]
              (bit-shift-left 1 x)) 0))))
  
(defn word-letters [word]
  (into (sorted-set) (seq word)))

(defn letter-positions [word letter]
  (for [i (range (count word))
        :when (= letter (nth (seq word) i))]
    i))

(defn word-letter-position-mask [word letter]
  (apply make-position-mask (letter-positions word letter)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Triples Generation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn word-triples [word]
  (set (concat
         (for [c (all-letters)]
           (tuple word c (word-letter-position-mask word c)))
         [(tuple word :length (count word))
          (tuple word :type   :WORD)])))

;; (word-triples "AABCD")
;; (word-triples "SUPERCALIFRAGILISTICEXPIALIDOCIOUS")


(defn word-collection-triples [coll]
  (r/fold clojure.set/union clojure.set/union
    (mapv word-triples coll)))


(defn word-file-triples [filename]
  (r/fold clojure.set/union clojure.set/union
    (mapv word-triples (words-from-file filename))))


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
     (set-word-db! (make-graph (word-file-triples filename)))
     @word-db))

;;;
;;  These are the actual "top-level" database API functions which are pleasantly
;; trivial to implement using the facilities we have created for parsing and
;; graph construction.  Primarily, these are responsible for atomic interaction
;; with global state, which is limited to only the single atom 'word-db'.
;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Hangman Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn words-of-length [g length]
  (set (map s (query g nil :length length))))


(defn words-excluding-letter [g letter]
  (set (map s (query g nil letter 0))))

(defn words-excluding [g & letters]
  (apply clojure.set/intersection 
    (filter identity
      (map (partial words-excluding-letter g) letters))))

(defn words-with-letter-positions [g letter position & more]
  (set (map s (query g nil letter
                (apply make-position-mask (conj more position))))))
  
(defn all-words [g]
  (mapv s (query g nil :type :WORD)))

(defn random-element [coll]
  (nth (vec coll) (rand-int (count coll))))

(defn random-word [g]
  (random-element (all-words g)))

(defn random-words [g n]
  (loop [words #{} all (all-words g)]
    (if (= (count words) n)
      words
      (recur (conj words (random-element all)) all))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Letter Frequency Distribution
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn letter-frequency-distribution [words]
  (with-local-vars [newspace (transient {})]
    (doseq [w words]
      (doseq [t (word-letters w)]
        (var-set newspace 
          (conj! (var-get newspace)
            [t (inc (get (var-get newspace) t 0))]))))
    (persistent! (var-get newspace))))


(defn pp-letter-frequency-distribution
  ([]      (pp-letter-frequency-distribution (all-words @word-db)))
  ([words] (let [cols ["Term" "Words Occurred" "%"]
                 tot  (count words)]
             (pp/print-table cols
               (map (partial zipmap cols)
                 (sort-by second >
                   (map #(conj % (pp/cl-format nil "~6,2f" (/ (second %)
                                                             tot 0.01)))
                     (seq (letter-frequency-distribution words)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Examples
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (word-triples "abccddd")

;; (util/run-and-measure-timing 
;;   (count (word-file-triples +default-corpus-file+)))

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

;; (word-letter-position-mask "A" \A)
;; (word-letter-position-mask "B" \A)
;; (word-letter-position-mask "AABBBCCCC" \A)
;; (word-letter-position-mask "SOMETIMES" \E)

;; (util/run-and-measure-timing
;;   (clojure.set/intersection
;;     (words-of-length @word-db 9)
;;     (words-excluding @word-db \A \O \U)
;;     (words-with-letter-positions @word-db \E 3 7)
;;     (words-with-letter-position @word-db \U 1)
;;     ))



;; (util/run-and-measure-timing
;;   (pp-letter-frequency-distribution))
;;
;; | Term | Words Occurred |      % |
;; |------+----------------+--------|
;; |    E |         121433 |  69.98 |
;; |    S |         104351 |  60.13 |
;; |    I |         102392 |  59.01 |
;; |    A |          94264 |  54.32 |
;; |    R |          91066 |  52.48 |
;; |    N |          84485 |  48.69 |
;; |    T |          83631 |  48.19 |
;; |    O |          79663 |  45.91 |
;; |    L |          68795 |  39.64 |
;; |    C |          55344 |  31.89 |
;; |    D |          47219 |  27.21 |
;; |    U |          46733 |  26.93 |
;; |    P |          40740 |  23.48 |
;; |    M |          39869 |  22.98 |
;; |    G |          38262 |  22.05 |
;; |    H |          33656 |  19.40 |
;; |    B |          26736 |  15.41 |
;; |    Y |          24540 |  14.14 |
;; |    F |          17358 |  10.00 |
;; |    V |          14844 |   8.55 |
;; |    K |          12757 |   7.35 |
;; |    W |          11310 |   6.52 |
;; |    Z |           7079 |   4.08 |
;; |    X |           4607 |   2.65 |
;; |    Q |           2541 |   1.46 |
;; |    J |           2467 |   1.42 |
