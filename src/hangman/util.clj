(ns hangman.util
  (:use     [clojure.core])
  (:require [clojure.repl :as repl])
  (:require [clojure.pprint :as pp])
  (:require [clojure.reflect :as reflect]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; doseq-indexed
;;
;; https://gist.github.com/4134522
;; https://twitter.com/Baranosky/status/271894356418498560
;;
;; Example:
;;
;; (doseq-indexed idx [name names]
;;   (println (str idx ". " name)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro doseq-indexed
  [index-sym [item-sym coll] & body]
  `(let [idx-atom# (atom 0)]
     (doseq [~item-sym ~coll]
       (let [~index-sym (deref idx-atom#)]
         ~@body
         (swap! idx-atom# inc)))))


(defn indexed
  "Returns a lazy sequence of [index, item] pairs, where items come
  from 's' and indexes count up from zero.
  (indexed '(a b c d))  =>  ([0 a] [1 b] [2 c] [3 d])"
  [s]
  (map vector (range) s))

(defn positions
  "Returns a lazy sequence containing the positions at which pred
   is true for items in coll."
  [pred coll]
  (for [[idx elt] (indexed coll) :when (pred elt)] idx))

(defn split-vec
  "Split the given vector at the provided offsets using subvec. Supports
  negative offsets."
  [v & ns]
  (let [ns (map #(if (neg? %) (+ % (count v)) %) ns)]
    (lazy-seq
     (if-let [n (first ns)]
       (cons (subvec v 0 n)
             (apply split-vec
                    (subvec v n)
                    (map #(- % n) (rest ns))))
       (list v)))))

(defn knit
  "Takes a list of functions (f1 f2 ... fn) and returns a new function F. F takes
   a collection of size n (x1 x2 ... xn) and returns a vector
      [(f1 x1) (f2 x2) ... (fn xn)].
   Similar to Haskell's ***, and a nice complement to juxt (which is Haskell's &&&)."
  [& fs]
  (fn [arg-coll] split-vec    (vec (map #(% %2) fs arg-coll))))


(defn rmerge
  "Recursive merge of the provided maps."
  [& maps]
  (if (every? map? maps)
    (apply merge-with rmerge maps)
    (last maps)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Namespace Docstring Introspection
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ns-docs
  "Prints docs for all public symbols in given namespace
   http://blog.twonegatives.com/post/42435179639/ns-docs
   https://gist.github.com/timvisher/4728530"
  [ns-symbol]
  (dorun 
   (map (comp #'repl/print-doc meta)
        (->> ns-symbol 
             ns-publics 
             sort 
             vals))))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Timing and Performance Metric
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro with-timing
  "Same as clojure.core/time but returns a vector of a the result of
   the code and the milliseconds rather than printing a string. Runs
   the code in an implicit do."
  [& body]
  `(let [start# (System/nanoTime)  ret# ~(cons 'do body)]
     [ret# (/ (double (- (System/nanoTime) start#)) 1000000.0)]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pretty Printing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;  (def fmt "~:<~:@{[~25s ~30s]~:^~:@_~}~:>")
;; #'user/fmt

;; user=> (cl-format true fmt attrs)
;; ([:db/cardinality           :db.cardinality/one           ]
;;  [:db/doc                   "Documentation string for an entity."]
;;  [:db/fulltext              true                          ]
;;  [:db/ident                 :db/doc                       ]
;;  [:db/valueType             :db.type/string               ])
;; nil

;; ;;;

;; Since you're interested in pprint/cl-format, I'll deconstruct the
;; format string for you:

;; "~:<~:@{[~25s ~30s]~:^~:@_~}~:>"  ; the entire format string
;;  ~:<                        ~:>   ; creates a logical block
;;     ~:@{                  ~}      ; iterates through the vectors in the list
;;         [         ]               ; creates a pair of literal brackets
;;          ~25s ~30s                ; creates a pair of fixed-width columns
;;                    ~:^            ; breaks the iteration on the last pair
;;                       ~:@_        ; creates a mandatory newline



;; user=> (let [keys ["Attribute" "Value"]]
;;         (print-table keys (map (partial zipmap keys) attrs)))

;; =====================================================
;; Attribute       | Value                              
;; =====================================================
;; :db/cardinality | :db.cardinality/one                
;; :db/doc         | Documentation string for an entity.
;; :db/fulltext    | true                               
;; :db/ident       | :db/doc                            
;; :db/valueType   | :db.type/string                    
;; =====================================================

(defn fprint
  "Same as print but explicitly flushes *out*."
  [& more]
  (apply print more)
  (flush))

(defn fprintln
  "Same as println but explicitly flushes *out*."
  [& more]
  (apply println more)
  (flush))

  

(def ^:dynamic *print-progress* true)

(defn make-default-progress-reporter
  "A basic progress reporter function which can be used with
  `with-progress-reporting`."
  [{:keys [iters-per-row num-columns row-handler row-fmt no-summary]}]
  (let [iters-per-row (or iters-per-row 1000)
        num-columns (or num-columns 60)
        iters-per-dot (int (/ iters-per-row num-columns))
        row-handler (fn [i]
                      (if row-handler
                        (str " " (row-handler i))
                        ""))
        row-fmt (or row-fmt "%,8d rows%s")]
    (fn [i final?]
      (cond
       final?
       (when-not no-summary
         (fprintln (format row-fmt i (row-handler i))))

       (zero? (mod i iters-per-row))
       (fprintln (format row-fmt i (row-handler i)))

       (zero? (mod i iters-per-dot))
       (fprint ".")))))

(defmacro with-progress-reporting
  "Bind a `reportfn` function, and evaluate `body` wherein
  calling (report!) will invoke the report function with the current
  state of the iteration."
  [opts & body]
  `(let [iter# (atom 0)
         opts# (or ~opts {})
         reporter# (or (:reporter opts#)
                       (util/make-default-progress-reporter opts#))]
     (letfn [(report# [& [fin?#]]
               (when util/*print-progress*
                 (when-not fin?# (swap! iter# inc))
                 (reporter# @iter# (boolean fin?#))))]
       (let [~'report! report#
             val# (do ~@body)]
         (report# true)
         val#))))



(defmacro wrap-fn [name args & body]
  `(let [old-fn# (var-get (var ~name))
         new-fn# (fn [& p#] 
                   (let [~args p#] 
                     (do ~@body)))
         wrapper# (fn [& params#]
                    (if (= ~(count args) (count params#))
                      (apply new-fn# params#)
                      (apply old-fn# params#)))] 
     (alter-var-root (var ~name) (constantly wrapper#))))





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defmacro ppmx [form]
  `(do
     (pp/cl-format *out*  ";;; Macroexpansion:~%~% ~S~%~%;;; First Step~%~%"
       '~form)
     (pp/pprint (macroexpand-1 '~form))
     (pp/cl-format *out*  "~%;;; Full expansion:~%~%")
     (pp/pprint (macroexpand '~form))
     (println "")))

  
(defmacro returning
  "Compute a return value, then execute other forms for side effects.
  Like prog1 in common lisp, or a (do) that returns the first form."
  [value & forms]
  `(let [value# ~value]
     ~@forms
     value#))

(defmacro returning-bind [[sym retn-form] & body]
  `(let [~sym ~retn-form]
     (returning ~sym
       ~@body)))

(defmacro defdynamic [& [meta? name & value? :as args]]
  (let [expr# `(def ~@args)]
    `(returning-bind [v# ~expr#]
       (alter-meta! v# #(assoc % :dynamic true))
       (.setDynamic v# true))))

(defmacro aprog1 [retn-form & body]
  `(let [~'it ~retn-form]
     (returning ~'it
       ~@body)))


(defmacro aif
  ([test-form then-form]
     `(let [~'it ~test-form]
        (if ~'it ~then-form)))
  ([test-form then-form else-form]
     `(let [~'it ~test-form]
        (if ~'it ~then-form ~else-form))))

(defmacro anil?
  ([test-form then-form]
     `(let [~'it ~test-form]
        (if-not (nil? ~'it) ~then-form)))
  ([test-form then-form else-form]
     `(let [~'it ~test-form]
        (if-not (nil? ~'it) ~then-form ~else-form))))

(defmacro awhen [test-form & body]
  `(aif ~test-form (do ~@body)))

(defmacro awhile [test-expr & body]
  `(while (let [~'it ~test-expr]
            (do ~@body)
            ~'it)))

(defmacro aand [& tests]
  (if (empty? tests)
    true
    (if (empty? (rest tests))
      (first tests)
      (let [first-test (first tests)]
        `(aif ~first-test
              (aand ~@(rest tests)))))))

(defmacro it-> [& [first-expr & rest-expr]]
  (if (empty? rest-expr)
    first-expr
    `(if-let [~'it ~first-expr]
       (it-> ~@rest-expr))))

(defmacro run-and-measure-timing [expr]
  `(let [start-time# (System/currentTimeMillis)
         response# ~expr
         end-time# (System/currentTimeMillis)]
     {:time-taken (- end-time# start-time#)
      :response response#
      :start-time start-time#
      :end-time end-time#}))



;; (defmacro with-temp-file [f-sym & body]
;;   `(let [prefix# (.toString (UUID/randomUUID))
;;          postfix# (.toString (UUID/randomUUID))
;;          ~f-sym (java.io.File/createTempFile prefix# postfix#)]
;;      (try
;;        (do ~@body)
;;        (finally
;;          (.delete ~f-sym)))))


(defn lines-of-file [file-name]
 (line-seq
  (java.io.BufferedReader.
   (java.io.InputStreamReader.
    (java.io.FileInputStream. file-name)))))


(defmacro exception [& [param & more :as params]] 
  (if (class? param) 
    `(throw (new ~param (str ~@(interpose " " more)))) 
    `(throw (Exception. (str ~@(interpose " " params))))))


(defmacro ignore-exceptions [& body]
  `(try
     ~@body
     (catch Exception e# nil)))
