(ns lethal-injection.inverted
  (:refer-clojure :exclude [])
  (:require [clojure.pprint :as pp])
  (:require [clojure.string :as str])
  (:require [clojure.core.reducers :as r])
  (:require [clojure.tools.logging :as log])
  (:require [lethal-injection.util  :as util])
  (:require [lethal-injection.bitop :as bitop])
  (:use     [lethal-injection.util :only [returning returning-bind indexed]])
  (:use     [lethal-injection.corpus])
  (:use     [print.foo]))
 


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search Terms
;;
;; Search terms are the individual elements to be managed by an inverted index.
;; In a normal sense, these are typically words to be searched for in some domain
;; of textual data -- web pages for example.  We use the same concept, but in our
;; hangman game we are interested in individual letters to be found within
;; words, rather than words within a page. So, in our case, we define search
;; terms to be the set of letters that may possibly occur within a word.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def +search-terms+ (.getBytes "ABCDEFGHIJKLMNOPQRSTUVWXYZ*_ ?\u0000"))

(def +term-ids+     (into {}
                      (mapv (comp vec reverse)
                        (indexed +search-terms+))))


(defn all-terms [] 
  (mapv (comp char bitop/ub8) (vec (seq +search-terms+))))


(defn term [id]
  (get (all-terms) id (last (all-terms))))


(defn canonical-char [ch]
  (cond
    (char? ch) (canonical-char (byte ch))
    (<= (byte \a) ch (byte \z)) (- ch 32)
    :default ch))


(defn term-id [ch]  
  (or (+term-ids+ (canonical-char ch)) \u0000))


(defn word-terms [w]
  (into (sorted-set) (seq w)))


(defn word-terms-bits [& [terms]]
  (if (empty? terms)
    0
    (apply bit-or
      (conj (for [n (map term-id (vec terms))]
              (bit-shift-left 1 n))
        0))))


(defn word-terms-vector [w]
  (bit-or
    (bit-shift-left (or (word-id *corpus* w) -1) 32)
    (word-terms-bits (word-terms w))))


(defn terms-vector-word [n]
  (word *corpus* (bit-shift-right n 32)))


(defn pp-terms-vector [x]
  (returning x
    (clojure.pprint/cl-format *out* "~&[~35A | ~32,,,'0@A~%"
      (terms-vector-word x)
      (Integer/toBinaryString (bitop/sb32 x)))))


(defn pp-terms-vectors [vs]
  (returning vs
    (clojure.pprint/cl-format *out* "~%[~35A | ~31@A~%"
      "        WORD" (apply str (reverse (all-terms))))
    (println "" (apply str (repeat 70 "-")))
    (loop [v vs]
      (if (empty? v)
        nil
        (do
          (clojure.pprint/cl-format *out* "~&[~35A | ~32,,,'0@A~%"
            (terms-vector-word (first v))
            (Integer/toBinaryString (bitop/sb32 (first v))))
          (recur (rest v)))))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Exclusionary Index
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-exclusionary-index [words]
  (loop [w   (seq words)
         i   0
         idx (long-array (count words) 0)]
      (if (empty? w)
        idx
        (do
          (aset idx i (word-terms-vector (first w)))
          (recur (rest w) (inc i) idx)))))


(def exclusionary-index (memoize build-exclusionary-index))


(defn exclude-terms [words & terms]
  (let [tv (word-terms-vector (apply str terms))
        eix (build-exclusionary-index words)]
    (filter identity
      (for [i (range (count eix)) :let [wtv (aget eix i)] ]
        (when (zero? (bit-and (bitop/mask 32 0) tv wtv))
          wtv)))))


(defn words-excluding-terms [words & terms]
  (let [vws (apply exclude-terms words terms)]
    (seq (map terms-vector-word vws))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Term Frequency Distribution
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn term-frequency-distribution [words]
  (with-local-vars [newspace (transient {})]
    (doseq [w words]
      (doseq [t (word-terms w)]
        (var-set newspace 
          (conj! (var-get newspace)
            [t (inc (get (var-get newspace) t 0))]))))
    (persistent! (var-get newspace))))


(defn pp-term-distribution
  ([]      (pp-term-distribution (:all-words *corpus*)))
  ([words] (let [cols ["Term" "Words Occurred" "%"]
                 tot  (count words)]
             (pp/print-table cols
               (map (partial zipmap cols)
                 (sort-by second >
                   (map #(conj % (pp/cl-format nil "~6,2f" (/ (second %) tot 0.01)))
                     (seq (term-frequency-distribution words)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inverted Index
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  
(defn make-inverted-set []
  (long-array (count (all-terms)) 0))

(defn indexed-word [w]
  (returning-bind [s (make-inverted-set)]
    (doseq [i (range (count w))]
      (let [tid (term-id (nth w i))
            b   (bit-shift-left 0x1 i)]
        (aset s tid (bit-or
                      (bit-shift-left (word-id *corpus* w) 32)
                      (aget s tid) b))))))

(defn make-posn-mask [& positions]
  (if (empty? positions)
    0
    (apply bit-or
      (conj (for [x positions]
              (bit-shift-left 1 x)) 0))))

(defn term-positions [word term]
  (for [i (range (count word)) :when (= term (nth (seq word) i))]
    i))



(defn make-inverted-index
  ([]      (make-inverted-index (:all-words *corpus*)))
  ([words] (mapv indexed-word words)))

(defn select-for-term-positions [iind term posn]
  (for [i (range (count iind))
        :let [p (aget (nth iind i) (term-id term))] 
        :when (= posn (bit-and posn (bitop/mask 32 0) p))]
    (bit-shift-right p 32)))

(defn words-including-term-positions [words term posn]
  (map (partial word *corpus*) 
    (select-for-term-positions
      (make-inverted-index words) term posn)))

    

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Examples
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (word-terms-bits "")    => 0
;; (word-terms-bits "A")   => 1
;; (word-terms-bits "B")   => 2
;; (word-terms-bits "C")   => 4
;; (word-terms-bits "ABC") => 7
;; (word-terms-bits "D")   => 8
;; (word-terms-bits "AD")  => 9

;; (repeatedly 3 #(with-corpus []
;;                  (let [w (random-word *corpus*)]
;;                   [w (word-terms-vector w)])))
;;
;; => (["MULTICAR"  413128610945285]
;;     ["IDEALIZER" 311037270296857]
;;     ["KOUMISSES" 353557414171920])

;; (with-corpus []
;;   (seq (map terms-vector-word
;;          [413128610945285 311037270296857 353557414171920])))
;;
;;  => ("MULTICAR" "IDEALIZER" "KOUMISSES")


;; (let [c (make-corpus)]
;;   (pp-terms-vectors c (exclude-terms c (random-words c 5) \a)))
;;
;; [        WORD                        | ^@? _*ZYXWVUTSRQPONMLKJIHGFEDCBA
;;  ----------------------------------------------------------------------
;; [STRUGGLES                           | 00000000000111100000100001010000
;; [BYWORDS                             | 00000001010001100100000000001010
;; [WHICKERS                            | 00000000010001100000010110010100


;; (let [c (make-corpus)]
;;   (pp-terms-vectors c (exclude-terms c (random-words c 50) \a \e)))
;;
;; [        WORD                        | ^@? _*ZYXWVUTSRQPONMLKJIHGFEDCBA
;;  ----------------------------------------------------------------------
;; [SURMOUNT                            | 00000000000111100111000000000000
;; [VOLVULUS                            | 00000000001101000100100000000000
;; [FOUR                                | 00000000000100100100000000100000
;; [SPOOKING                            | 00000000000001001110010101000000
;; [BULGY                               | 00000001000100000000100001000010
;; [IMPUDICITY                          | 00000001000110001001000100001100


;; (with-corpus []
;;   (words-excluding-terms (random-words *corpus* 5) \a))
;;
;;  => ("REPROOF")
;;  => ("VERISMO" "QUELLED" "PREEMPTIVELY")
;;  => ("FORETOKENED")
;;  => ("FEMES" "CONTINGENCY")


;; (with-corpus []
;;   (term-frequency-distribution (random-words *corpus* 5)))
;;
;;   => {\A 1, \B 1, \C 4, \D 1, \E 4, \I 2, \L 3, \M 1, \N 3,
;;       \O 2, \R 1, \S 5, \T 1, \U 2, \V 1, \W 1, \Y 2}


;; (with-corpus []
;;   (pp-term-distribution))
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

;; (with-corpus []
;;   (pp-term-distribution (random-words *corpus* 1000)))
;;
;; | Term | Words Occurred |      % |
;; |------+----------------+--------|
;; |    E |            711 |  71.10 |
;; |    S |            597 |  59.70 |
;; |    I |            583 |  58.30 |
;; |    A |            532 |  53.20 |
;; |    R |            510 |  51.00 |
;; |    N |            473 |  47.30 |
;; |    O |            440 |  44.00 |
;; |    T |            440 |  44.00 |
;; |    L |            381 |  38.10 |
;; |    C |            287 |  28.70 |
;; |    D |            283 |  28.30 |
;; |    U |            257 |  25.70 |
;; |    P |            249 |  24.90 |
;; |    M |            238 |  23.80 |
;; |    G |            212 |  21.20 |
;; |    H |            188 |  18.80 |
;; |    B |            152 |  15.20 |
;; |    Y |            137 |  13.70 |
;; |    F |             93 |   9.30 |
;; |    V |             93 |   9.30 |
;; |    K |             81 |   8.10 |
;; |    W |             73 |   7.30 |
;; |    Z |             40 |   4.00 |
;; |    X |             24 |   2.40 |
;; |    Q |             15 |   1.50 |
;; |    J |             11 |   1.10 |

;; (with-corpus []
;;   (pp-term-distribution (random-words *corpus* 50)))
;;
;; | Term | Words Occurred |      % |
;; |------+----------------+--------|
;; |    E |             39 |  78.00 |
;; |    S |             33 |  66.00 |
;; |    I |             29 |  58.00 |
;; |    R |             27 |  54.00 |
;; |    T |             27 |  54.00 |
;; |    N |             24 |  48.00 |
;; |    A |             22 |  44.00 |
;; |    L |             21 |  42.00 |
;; |    C |             19 |  38.00 |
;; |    O |             18 |  36.00 |
;; |    U |             18 |  36.00 |
;; |    M |             15 |  30.00 |
;; |    G |             12 |  24.00 |
;; |    D |             11 |  22.00 |
;; |    H |             11 |  22.00 |
;; |    P |             10 |  20.00 |
;; |    B |              9 |  18.00 |
;; |    Y |              9 |  18.00 |
;; |    F |              6 |  12.00 |
;; |    K |              6 |  12.00 |
;; |    Z |              3 |   6.00 |
;; |    V |              2 |   4.00 |
;; |    W |              2 |   4.00 |
;; |    J |              1 |   2.00 |
;; |    Q |              1 |   2.00 |
;; |    X |              1 |   2.00 |


;; (seq (indexed-word "A"))
;;   => (1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0)

;; (seq (indexed-word "AA"))
;;   => (3 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0)

;; (seq (indexed-word "AAA"))
;;   => (7 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0)

;; (seq (indexed-word "BBB"))
;;   => (0 7 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0)

;; (seq (indexed-word "AAABBB"))
;;   => (7 56 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0)

;; (seq (indexed-word "ABABAB"))
;;   => (21 42 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0)   

;; (seq (indexed-word "IMPUDICITY"))
;;   => (0 0 64 16 0 0 0 0 161 0 0 0 2 0 0 4 0 0 0 256 8 0 0 0 512 0 0 0 0 0 0)


;; (with-corpus []
;;   (assert (= (word-count *corpus*) (count (make-inverted-index)))))


;; (with-corpus []
;;   (let [ws (random-words *corpus* 10)]
;;     (zipmap ws (map #(map (partial bit-and (bitop/mask 32 0)) %)
;;                  (map seq (make-inverted-index ws))))))

;; {"INTERACTANTS" (288 0 64 0 8 0 0 0 1 0 0 0 0 514 0 0 0 16 2048 1156 0 0 0 0 0 0 0 0 0 0 0),
;;  "PUTTIED"      (0 0 0 64 32 0 0 0 16 0 0 0 0 0 0 1 0 0 0 12 2 0 0 0 0 0 0 0 0 0 0),
;;  "VACCINIAL"    (130 0 12 0 0 0 0 0 80 0 0 256 0 32 0 0 0 0 0 0 0 1 0 0 0 0 0 0 0 0 0),
;;  "TREBUCHETS"   (0 8 32 0 132 0 0 64 0 0 0 0 0 0 0 0 0 2 512 257 16 0 0 0 0 0 0 0 0 0 0),
;;  "PRINTED"      (0 0 0 64 32 0 0 0 4 0 0 0 0 8 0 1 0 2 0 16 0 0 0 0 0 0 0 0 0 0 0),
;;  "YACHTSMAN"    (130 0 4 0 0 0 0 8 0 0 0 0 64 256 0 0 0 0 32 16 0 0 0 0 1 0 0 0 0 0 0),
;;  "PALMED"       (2 0 0 32 16 0 0 0 0 0 0 4 8 0 0 1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0),
;;  "ANTISTORY"    (1 0 0 0 0 0 0 0 8 0 0 0 0 2 64 0 0 128 16 36 0 0 0 0 256 0 0 0 0 0 0),
;;  "GASTROSCOPES" (2 0 128 0 1024 0 1 0 0 0 0 0 0 0 288 512 0 16 2116 8 0 0 0 0 0 0 0 0 0 0 0),
;;  "WELTERING"    (0 0 0 0 18 0 256 0 64 0 0 4 0 128 0 0 0 32 0 8 0 0 1 0 0 0 0 0 0 0 0)}


;; (with-corpus []
;;   (let [ws (:all-words *corpus*)]
;;     (words-including-term-positions ws \a 3)))
;;
;;  => ("AASVOGELS" "AASVOGEL" "AAS" "AARRGHH" "AARRGH" "AARGH" "AARDWOLVES"
;;      "AARDWOLF" "AARDVARKS" "AARDVARK" "AALS" "AALIIS" "AALII" "AAL" "AAHS"
;;      "AAHING" "AAHED" "AAH" "AA")




