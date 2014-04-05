(ns lethal-injection.corpus
  (:refer-clojure :exclude [])
  (:require [iota])
  (:require [clojure.pprint :as pp])
  (:require [clojure.string :as str])
  (:require [clojure.core.reducers :as r])
  (:require [clojure.tools.logging :as log])
  (:require [lethal-injection.util  :as util])
  (:require [lethal-injection.bitop :as bitop])
  (:use     [lethal-injection.util :only [defdynamic]])
  (:import  [clojure.lang Indexed]))



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


(defrecord Corpus [filename all-words word-ids by-length]
  Object
  (toString [this]
    (str "#<Corpus: " filename
      " [" (count all-words)
      " words]>"))
  Indexed
  (nth [this i]
    (get all-words i)))

(defdynamic *corpus* nil)

(defn make-corpus
  ([]
     (make-corpus +default-corpus-file+))
  ([filename]
     (let [wff (words-from-file filename)
           wid (into {}
                 (for [i (range (count wff))
                       :let [w (get wff i)]]
                   (vector w i)))
           byl (into (sorted-map) (group-by count wff))]
         (->Corpus filename wff wid byl))))

(defmulti ensure-corpus type)

(defmethod ensure-corpus nil [x]
  (make-corpus))

(defmethod ensure-corpus String [fname]
  (make-corpus fname))

(defmethod ensure-corpus Corpus [c]
  c)


(defmacro with-corpus [[designator] & body]
  `(binding [*corpus* (ensure-corpus ~designator)]
       ~@body))


(defprotocol Corpora
  (word-id         [this w])
  (word            [this id])
  (word-count      [this])
  (words-of-length [this n])
  (random-word     [this])
  (random-words    [this n]))


(extend-type Corpus
  Corpora
  (word-id [this w]
    (get (:word-ids this) w))
  (word    [this id]
    (get (:all-words this) id))
  (word-count [this]
    (count (:all-words this)))
  (words-of-length [this n]
    (get (:by-length this) n))
  (random-word [this]
    (word this (rand-int (word-count this))))
  (random-words [this n]
    (repeatedly n (partial random-word this))))



(defn pp-length-distribution [corpus]
  (let [cols ["Word Length" "Occurances"]
        gbl (:by-length corpus)]
    (pp/print-table cols
      (map (partial zipmap cols)
        (into (sorted-map)
          (map #(vector (first %) (count (second %)))
            gbl))))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Examples
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (with-corpus []
;;   (word-count *corpus*))
;;
;;  => 173528

;; (assert (=
;;           (word-count (ensure-corpus nil))
;;           (word-count (ensure-corpus (ensure-corpus nil)))
;;           (word-count (ensure-corpus +default-corpus-file+))))

;; (fipp
;;   (util/run-and-measure-timing
;;     (let [all (words-from-file)]
;;       (assert (= (count all) 173528))
;;       (assert (every? string? all))
;;       (assert (not (some empty? all))))))

;; (fipp
;;   (util/run-and-measure-timing
;;     (let [c (make-corpus)]
;;       (assert (= (:filename c) "resources/words.txt"))
;;       (assert (= (count (:all-words c)) (count (:word-ids c))))
;;       (assert (= (:all-words c) (map (comp
;;                                        (:all-words c)
;;                                        (:word-ids c)
;;                                        (:all-words c))
;;                                   (range (count (:all-words c)))))))))

;; (util/run-and-measure-timing
;;   (let [c (make-corpus)]
;;     (assert (nil?
;;               (let [ws (random-words c 16)]
;;                 (clojure.set/difference (prn (set ws))
;;                   (prn (set (map (partial word c)
;;                               (map (partial word-id c) ws))))))))))
  

;; (util/run-and-measure-timing
;;   (let [c (make-corpus)]
;;     (pp-length-distribution c)
;;     (assert (= (words-of-length c 28) ["ETHYLENEDIAMINETETRAACETATES"]))
;;     (assert (= (count (words-of-length c 2)) 96))))


;; | Word Length | Occurances |
;; |-------------+------------|
;; |           2 |         96 |
;; |           3 |        978 |
;; |           4 |       3919 |
;; |           5 |       8672 |
;; |           6 |      15290 |
;; |           7 |      23208 |
;; |           8 |      28558 |
;; |           9 |      25011 |
;; |          10 |      20404 |
;; |          11 |      15581 |
;; |          12 |      11382 |
;; |          13 |       7835 |
;; |          14 |       5134 |
;; |          15 |       3198 |
;; |          16 |       1938 |
;; |          17 |       1125 |
;; |          18 |        594 |
;; |          19 |        328 |
;; |          20 |        159 |
;; |          21 |         62 |
;; |          22 |         29 |
;; |          23 |         13 |
;; |          24 |          9 |
;; |          25 |          2 |
;; |          27 |          2 |
;; |          28 |          1 |

