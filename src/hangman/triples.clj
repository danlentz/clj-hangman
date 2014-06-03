(ns hangman
  (:refer-clojure :exclude [])
  (:require [clojure.pprint :as pp])
  (:require [clojure.string :as str])
  (:require [clojure.core.reducers :as r])
  (:require [clojure.tools.logging :as log])
  (:require [hangman.util  :as util])
  (:require [hangman.corpus :as corpus])
  (:use     [clj-tuple])
  (:use     [print.foo]))


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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tuple Constituent Accessors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn s [triple]
  (assert (= (count triple) 3))
  (nth triple 0))

(defn p [triple]
  (assert (= (count triple) 3))
  (nth triple 1))

(defn o [triple]
  (assert (= (count triple) 3))
  (nth triple 2))

(defn sp [triple]
  (assert (= (count triple) 3))
  (butlast triple))

(defn po [triple]
  (assert (= (count triple) 3))
  (rest triple))

(defn so [triple]
  (assert (= (count triple) 3))
  (list (s triple) (o triple)))

(defn spo [triple]
  (assert (= (count triple) 3))
  triple)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Graph Constructors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Graph [indices triples])

(defmethod print-method Graph [g ^java.io.Writer w]
  (.write w "#<Graph (")
  (.write w (str (count (:triples g))))
  (.write w " triples)>"))


;; (defn make-index [triples key1 key2 key3]
;;   (reduce (fn [i0 triple]
;;             (let [i1 (or (i0 (key1 triple)) {})
;;                   i2 (or (i1 (key2 triple)) #{})]
;;               (assoc i0
;;                 (key1 triple)
;;                 (assoc i1
;;                   (key2 triple)
;;                   (conj i2 (key3 triple))))))
;;     {} triples))

(defn make-index [triples key1 key2 key3]
  (reduce (fn [i0 triple]
            (let [i1 (or (i0 (key1 triple)) {})
                  i2 (or (i1 (key2 triple)) {})]
              (assoc i0
                (key1 triple)
                (assoc i1
                  (key2 triple)
                  (assoc i2
                    (key3 triple)
                    triple)))))
    {} triples))


(defn add-index [graph key1 key2 key3]
  (->Graph
    (assoc (:indices graph)
      [key1 key2 key3] (make-index (:triples graph) key1 key2 key3))
    (:triples graph)))


(defn make-graph [triples]
  (-> (->Graph {} triples)
    (add-index s p o)
    (add-index p o s)
    (add-index o s p)))


  

;;;
;;  These are the "graph constructors" which build a data-structure
;; that represents a (multiply) indexed graph from the set of triples
;; obtained, usually, using the top-level parser api 'file-triples'.
;;
;;  The indices are hierarchical and dynamically generated based on a
;; provided sequence of "keys" that designate the triple constituent
;; represented at a specific depth within a given index.  The standard
;; set of indices built by default using 'indexed-graph-from-file' may
;; be easily extended or reduced if there is some compelling need to
;; do so.  As currently implemented, the graph indexing is provided
;; for all single constituent permutations. Multiple constituent
;; indexing can be incorporated with only minor changes to current
;; index and query facilities.
;;
;;  Each index is structured in a similar way to the following model,
;; which is shown based on the "normal" SPO triple constituent key
;; order [s p o]:
;;
;;  {subj1 {pred1 obj1,
;;          pred2 obj2,
;;          pred3 obj3 ...},
;;   subj2 {pred1 obj1,
;;          pred2 obj2,
;;          pred3 obj3 ...}
;;   ...}
;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Database Management
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-graph! [graph]
  (swap! db (constantly graph)))

(defn clear-db! []
  (set-graph! nil))

(defn build-db!
  ([]
     (build-db! corpus/+default-corpus-file+))
  ([filename]
     (set-graph! (make-graph (file-triples filename)))
     @db))

;;;
;;  These are the actual "top-level" database API functions which are pleasantly
;; trivial to implement using the facilities we have created for parsing and
;; graph construction.  Primarily, these are responsible for atomic interaction
;; with global state, which is limited to only the single var 'db'.
;;
;;  Its worth mentioning that there is nothing limiting the global db to only
;; the two graphs created from the code-puzzle test data.  Arbitrarily many
;; filenames may be passed to 'build-db!' and, provided the same file-naming
;; conventions are followed as the existing data files, the db will be built
;; and indexed appropriately for as many graphs as desired.  
;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Indexed Database Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn supplied-constituents [graph subj pred obj] 
  (vec (conj (for [c [subj pred obj]]
               (not (nil? c)))
         (class graph))))

(defmulti query supplied-constituents)

(defmethod query [Graph false false false] [graph subj pred obj]
  (:triples graph))


(defmethod query [Graph true false false]  [graph subj pred obj]
  (let [i0 ((:indices graph) [s p o])
        i1 (get i0 subj)]
    (set (for [pred (keys i1)
               obj  (keys (get i1 pred))]
           (get-in i1 [pred obj])))))

(defmethod query [Graph false true false]  [graph subj pred obj]
  (let [i0 ((:indices graph) [p o s])
        i1 (get i0 pred)]
    (set (for [obj  (keys i1)
               subj (keys (get i1 obj))]
           (get-in i1 [obj subj])))))
 
(defmethod query [Graph false false true]  [graph subj pred obj]
  (let [i0 ((:indices graph) [o s p])
        i1 (get i0 obj)]
    (set (for [subj (keys i1)
               pred (keys (get i1 subj))]
           (get-in i1 [subj pred])))))

(defmethod query [Graph true true false]  [graph subj pred obj]
  (let [idx ((:indices graph) [s p o])]
    (set (for [obj (keys (get-in idx [subj pred]))]
           (tuple subj pred obj)))))
 
(defmethod query [Graph true false true]  [graph subj pred obj]
  (let [idx ((:indices graph) [o s p])]
    (set (for [pred (keys (get-in idx [obj subj]))]
           (tuple subj pred obj)))))
  
(defmethod query [Graph false true true]  [graph subj pred obj]
  (let [idx ((:indices graph) [p o s])]
    (set (for [subj (keys (get-in idx [pred obj]))]
           (tuple subj pred obj)))))
 
(defmethod query [Graph true true true]   [graph subj pred obj]
  (set (filter identity
         (vector ((:triples graph) [subj pred obj])))))


;; (util/run-and-measure-timing
;;   (query @db "EVERYTHING" nil nil))

;; (util/run-and-measure-timing
;;   (take 100 (query @db nil \J nil)))

;; (util/run-and-measure-timing
;;   (take 10 (query @db nil nil 4)))

;; (util/run-and-measure-timing
;;   (query @db nil \A 0))

;; (util/run-and-measure-timing
;;   (query @db "EVERYTHING" \E nil))

;; (util/run-and-measure-timing
;;   (query @db "EVERYTHING" nil 0))

;; (query @db "EVERYTHING" \E 0)
;;  => #{["EVERYTHING" \E 0]}

;; (query @db "EVERYTHING" \E 1)
;;  => #{}

;; (query @db nil \Q 8)
;;  => #{["VENTRILOQUISTIC" \Q 8]
;;       ["VENTRILOQUIES" \Q 8]
;;       ["PICTURESQUENESSES" \Q 8]
;;       ["GRANDILOQUENCES" \Q 8]
;;       ["VENTRILOQUIZING" \Q 8]
;;       ["DISCOTHEQUES" \Q 8]
;;       ["MULTIFREQUENCY" \Q 8]
;;       ["PLATERESQUE" \Q 8]
;;       ["VENTRILOQUISM" \Q 8]
;;       ["VENTRILOQUY" \Q 8]
;;       ["GIGANTESQUE" \Q 8]
;;       ["DEMISEMIQUAVERS" \Q 8]
;;       ["DEMISEMIQUAVER" \Q 8]
;;       ["VENTRILOQUIALLY" \Q 8]
;;       ["VENTRILOQUISTS" \Q 8]
;;       ["VENTRILOQUIZE" \Q 8]
;;       ["PICTURESQUENESS" \Q 8]
;;       ["GRANDILOQUENCE" \Q 8]
;;       ["GRANDILOQUENT" \Q 8]
;;       ["VENTRILOQUIZED" \Q 8]
;;       ["NONDELINQUENT" \Q 8]
;;       ["PICTURESQUE" \Q 8]
;;       ["PICTURESQUELY" \Q 8]
;;       ["VENTRILOQUISMS" \Q 8]
;;       ["DISCOTHEQUE" \Q 8]
;;       ["VENTRILOQUIZES" \Q 8]
;;       ["GRANDILOQUENTLY" \Q 8]
;;       ["VENTRILOQUIAL" \Q 8]
;;       ["VENTRILOQUIST" \Q 8]}


;;;
;;  Ok, so this a is way to implemnent the "lower-level" query engine.  There
;; are 8 possible combinations of supplied arguments, so 8 methods are
;; needed to implement optimally spread queries over available indexes. Note
;; we are always requiring the first query constituent, which specifies a
;; graph "name", but there is nothing to prevent future extension to a
;; full-fledged quad-store, which would require addition of 8 additional
;; query methods and modest updates turning "triple" related code into the
;; equivalent "quad" routines.
;;
;;  If this effort were to continue, these "lower-level" query methods are the 
;; essential pieces one would use to build a "higher-level" query language.  It is
;; not very much more effort/code required to build the rudiments of a graph-pattern
;; based query system along the lines of SparQL once one has completed writing these 
;; fundamental "lower-level" query operations.



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Graph Operations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn graph-union [graph & more]
  (make-graph
    (reduce clojure.set/union
      (map :triples (conj more graph)))))


(defn graph-intersection [graph & more]
  (make-graph
    (reduce clojure.set/intersection
      (map :triples (conj more graph)))))


(defn graph-difference [graph & more]
  (make-graph
    (reduce clojure.set/difference
      (map :triples (conj more graph)))))




;; (:triples
;; (graph-union
;;   (make-graph (word-triples "EVERYTHING"))
;;   (make-graph (word-triples "SOMETHING"))))

;; (:triples
;; (graph-intersection
;;   (make-graph (word-triples "EVERYTHING"))
;;   (make-graph (word-triples "EVERYTHING"))))

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



