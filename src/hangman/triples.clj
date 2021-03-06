(ns hangman.triples
  (:refer-clojure :exclude [])
  (:require [clojure.pprint :as pp])
  (:require [clojure.core.reducers :as r])
  (:require [hangman.util  :as util])
  (:require [clj-uuid :as uuid])
  (:use     [clj-tuple]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Global State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def db (atom {}))


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
;; Graph / GraphContainer Protocol
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Graph [id indices triples])

(defprotocol GraphContainer
  (id       [this])
  (indices  [this])
  (triples  [this]))

(extend-type Graph GraphContainer
             (id [this]
               (:id this))
             (indices [this]
               (:indices this))
             (triples [this]
               (:triples this)))


(defmethod print-method Graph [g ^java.io.Writer w]
  (.write w "#<Graph ")
  (.write w (str (id g)))
  (.write w " (")
  (.write w (str (count (triples g))))
  (.write w " triples)>"))


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


;;;;
;;; The following index implementation exploits clojure reducers
;;; concurrency.  It is included for reference and is not
;;; currently in use because in practice it provides no gains
;;; in performance
;;;;

;; (defn make-index [triples key1 key2 key3]
;;   (r/fold util/rmerge (fn
;;                         ([] {})
;;                         ([i0 triple]
;;                            (let [i0 (or i0 {})
;;                                  i1 (or (i0 (key1 triple)) {})
;;                                  i2 (or (i1 (key2 triple)) {})]
;;                              (assoc i0
;;                                (key1 triple)
;;                                (assoc i1
;;                                  (key2 triple)
;;                                  (assoc i2
;;                                    (key3 triple)
;;                                    triple))))))
;;     triples))

;;;;
;;; The following index implementation creates a set for the innermost
;;; index rather than a map.  It is included for reference and is not
;;; currently in use. 
;;;;

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


(defn add-index [g key1 key2 key3]
  (->Graph (id g)
    (assoc (indices g)
      [key1 key2 key3] (make-index (triples g) key1 key2 key3))
    (triples g)))


(defn make-graph
  ([tuples]
     (make-graph (uuid/v1) tuples))
  ([id tuples]
     (assert (uuid/uuid? id))
     (-> (->Graph (uuid/the-uuid id) {} tuples)
       (add-index s p o)
       (add-index p o s)
       (add-index o s p))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; GraphBuilder Protocol
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol GraphBuilder
  (graph [this]))

;; TODO: Perhaps specialize on more abstract classes eg., Collection, ...?

(extend-type Graph GraphBuilder
             (graph [this]
               this))

(extend-type clojure.lang.PersistentHashSet GraphBuilder
             (graph [this]
               (or (get @db this)                
                 (make-graph this))))

(extend-type clojure.lang.PersistentList GraphBuilder
             (graph [this]
               (graph (set this))))

(extend-type clojure.lang.PersistentVector GraphBuilder
             (graph [this]
               (graph (set this))))

(extend-type nil GraphBuilder
             (graph [this]
               (make-graph uuid/+null+ #{})))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Graph Database and Context
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn intern-graph [g]
  (let [x (@db (triples g))]
    (if x
      (id x)
      (util/returning (id g)
        (swap! db #(into % [[(id g) g] [(triples g) g]]))))))
  
(def ^{:dynamic true} *context* (intern-graph (graph nil)))

(extend-type java.util.UUID GraphBuilder
             (graph [this]
               (get @db this)))

(defmacro with-context [designator & body]
  `(binding [*context* (intern-graph (graph ~designator))]
     ~@body))


;; (assert (= (graph *context*) (graph nil) (graph uuid/+null+)))

;; (graph #{[1 2 3]})
;;  => #<Graph 2d1b48b0-723d-1195-8101-7831c1bbb832 (1 triples)>
;;  => #<Graph 270f47a0-723d-1195-8101-7831c1bbb832 (1 triples)>
;;  => #<Graph 257a01a0-723d-1195-8101-7831c1bbb832 (1 triples)>

;; (intern-graph (graph #{[1 2 3]}))
;;  => #uuid "6d863860-723d-1195-8101-7831c1bbb832"

;; (graph #{[1 2 3]})
;;  => #<Graph 6d863860-723d-1195-8101-7831c1bbb832 (1 triples)>
;;  => #<Graph 6d863860-723d-1195-8101-7831c1bbb832 (1 triples)>
;;  => #<Graph 6d863860-723d-1195-8101-7831c1bbb832 (1 triples)>


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Indexed Graph Query
;;
;; Implememnted as a multifunction rather than protocol to allow modular
;; separation of implmentation based on supplied constituents of query.
;; This, for example, might exploit various indexing capabilities of a
;; specific Graph implementation.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn supplied-constituents [g subj pred obj] 
  (vec (conj (for [c [subj pred obj]]
               (not (nil? c)))
         (class g))))

(defmulti query supplied-constituents)

(defmethod query [Graph false false false] [graph subj pred obj]
  (triples graph))


(defmethod query [Graph true false false]  [graph subj pred obj]
  (let [i0 ((indices graph) [s p o])
        i1 (get i0 subj)]
    (set (for [pred (keys i1)
               obj  (keys (get i1 pred))]
           (get-in i1 [pred obj])))))

(defmethod query [Graph false true false]  [graph subj pred obj]
  (let [i0 ((indices graph) [p o s])
        i1 (get i0 pred)]
    (set (for [obj  (keys i1)
               subj (keys (get i1 obj))]
           (get-in i1 [obj subj])))))
 
(defmethod query [Graph false false true]  [graph subj pred obj]
  (let [i0 ((indices graph) [o s p])
        i1 (get i0 obj)]
    (set (for [subj (keys i1)
               pred (keys (get i1 subj))]
           (get-in i1 [subj pred])))))

(defmethod query [Graph true true false]  [graph subj pred obj]
  (let [idx ((indices graph) [s p o])]
    (set (for [obj (keys (get-in idx [subj pred]))]
           (tuple subj pred obj)))))
 
(defmethod query [Graph true false true]  [graph subj pred obj]
  (let [idx ((indices graph) [o s p])]
    (set (for [pred (keys (get-in idx [obj subj]))]
           (tuple subj pred obj)))))
  
(defmethod query [Graph false true true]  [graph subj pred obj]
  (let [idx ((indices graph) [p o s])]
    (set (for [subj (keys (get-in idx [pred obj]))]
           (tuple subj pred obj)))))
 
(defmethod query [Graph true true true]   [graph subj pred obj]
  (set (filter identity
         (vector ((triples graph) [subj pred obj])))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SELECT / GraphQuery Protocol (Context-Aware Graph Query)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol GraphQuery
  (select [this tup]))

(extend-type Graph GraphQuery
             (select [this tup]
               (graph
                 (let [subj (s tup)
                       pred (p tup)
                       obj  (o tup)]
                   (clojure.set/union
                     (query this subj pred obj)
                     (query (graph *context*) subj pred obj))))))

(extend-type nil GraphQuery
             (select [this tup]
               (graph nil)))

(extend-type java.util.UUID GraphQuery
             (select [this tup]
               (select (graph this) tup)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Identity and Context: examples.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (select uuid/+null+ [1 2 3])
;;  => #<Graph 00000000-0000-0000-0000-000000000000 (0 triples)>

;; (select (graph #{[1 2 3] [4 5 6]}) [nil nil nil])
;;   => #<Graph e9a62310-7238-1195-8101-7831c1bbb832 (2 triples)>    

;; (with-context #{[1 2 3]}
;;   (query (graph *context*) 1 2 nil))
;;   => #{[1 2 3]}

;; (with-context (select (graph #{[1 2 3] [4 5 6]}) [nil nil nil])
;;   (triples (select (graph nil) [nil nil nil])))
;;   => #{[4 5 6] [1 2 3]}

;; (select (graph #{[1 2 3] [4 5 6]}) [nil nil nil])
;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>
;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>
;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>

;; (with-context (select (graph #{[1 2 3] [4 5 6]}) [nil nil nil])
;;   (select (graph nil) [nil nil nil]))
;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>
;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>
;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Graph Operations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn graph-union [g & more]
  (graph
    (reduce clojure.set/union
      (map triples (conj more g)))))


(defn graph-intersection [g & more]
  (graph
    (reduce clojure.set/intersection
      (map triples (conj more g)))))


(defn graph-difference [g & more]
  (graph
    (reduce clojure.set/difference
      (map triples (conj more g)))))


(defn graph-equal? [g & more]
  (every? #(= (triples g) %) (map triples more)))


