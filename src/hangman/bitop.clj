(ns lethal-injection.bitop
  (:use [clojure.core])
  (:require [primitive-math :as primitive])
  (:require [clojure.core.reducers :as red])
  (:require [lethal-injection.util :as util]))
  


;; Primitive Type  |  Size   |  Minimum  |     Maximum    |  Wrapper Type
;;-----------------------------------------------------------------------
;; boolean         |1?8 bits |   false   |     true       |  Boolean
;; char            | 16 bits | Unicode 0 | Unicode 2^16-1 |  Character
;; byte            |  8 bits |  -128     |     +127       |  Byte  
;; short           | 16 bits |  -2^15    |     +2^15-1    |  Short
;; int             | 32 bits |  -2^31    |     +2^31-1    |  Integer
;; long            | 64 bits |  -2^63    |     +2^63-1    |  Long
;; float           | 32 bits |  IEEE754  |     IEEE754    |  Float
;; double          | 64 bits |  IEEE754  |     IEEE754    |  Double
;; void            |    ?    |     ?     |        ?       |  Void



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def +hex-chars+ [\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \A \B \C \D \E \F])

(def +ub63-mask+ 0x7fffffffffffffff)
(def +ub60-mask+ 0x0fffffffffffffff)
(def +ub56-mask+ 0x00ffffffffffffff)
(def +ub48-mask+ 0x0000ffffffffffff)
(def +ub40-mask+ 0x000000ffffffffff)
(def +ub32-mask+ 0x00000000ffffffff)
(def +ub24-mask+ 0x0000000000ffffff)
(def +ub16-mask+ 0x000000000000ffff)
(def +ub12-mask+ 0x0000000000000fff)
(def +ub8-mask+  0x00000000000000ff)
(def +ub4-mask+  0x000000000000000f)
(def +ub1-mask+  0x0000000000000001)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Simple Arithmetic Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn expt [num pow]
  (assert (>= pow 0))
  (loop [acc 1 p pow]
    (if (= 0 p) acc
      (recur (* acc num) (- p 1)))))

(defn expt2
  "Restricted exponentiation, returns 2^pow in O(1).
  Requires 0 <= pow < 64"
  [pow]
  (assert (not (neg? pow)))
  (assert (< pow 64))
  (bit-shift-left 0x1 pow))

(defn pphex [x]
  (util/returning x
    (clojure.pprint/cl-format *out* "~&[~A] [~64,,,'0@A]~%"
      (format "%1$016X" x)
      (Long/toBinaryString x))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bit-masking
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mask [width offset]
  (assert (not (neg? width)))
  (assert (not (neg? offset)))
  (assert (<= width 64))
  (assert (< offset 64))
  (if (<= (+ width offset) 63)
    (-> 1
      (bit-shift-left width)
      (dec)
      (bit-shift-left offset))
    (-> -1
      (bit-and-not (dec (expt2 offset))))))

(declare mask-offset mask-width)

(defn mask-offset [m]
  (cond 
    (zero? m) 0
    (neg?  m) (- 64 (mask-width m))
    :else     (loop [c 0]
                (if (pos? (bit-and 1 (bit-shift-right m c)))
                  c
                  (recur (inc c))))))

(defn mask-width [m]
  (if (neg? m)
    (- 64 (mask-width (- (inc m))))
    (loop [m (bit-shift-right m (mask-offset m)) c 0]
      (if (zero? (bit-and 1 (bit-shift-right m c)))
        c
        (recur m (inc c))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fundamental Bitwise Operations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ldb [bitmask num]
  (bit-and
    (primitive/>>> bitmask (mask-offset bitmask))
    (bit-shift-right num   (mask-offset bitmask))))

(defn dpb [bitmask num value]
  (-> (bit-and-not num bitmask)
    (bit-or
      (bit-and bitmask
        (bit-shift-left value (mask-offset bitmask))))))

(defn bit-count [x]
  (let [n (ldb (mask 63 0) x) s (if (neg? x) 1 0)]
    (loop [c s i 0]
      (if (zero? (bit-shift-right n i))
        c
        (recur (+ c (bit-and 1 (bit-shift-right n i))) (inc i))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Byte Casting 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ub4 [num]
  (byte (bit-and num +ub4-mask+)))

(defn ub8 [num]
  (short (bit-and num +ub8-mask+)))

(defn ub16 [num]
  (int (bit-and num +ub16-mask+)))

(defn ub24 [num]
  (int (bit-and num +ub24-mask+)))

(defn ub32 [num]
  (long (bit-and num +ub32-mask+)))

(defn ub48 [num]
  (long (bit-and num +ub48-mask+)))

(defn ub56 [num]
  (long (bit-and num +ub56-mask+)))

(defn sb8 [num]
  (unchecked-byte (ub8 num)))

(defn sb16 [num]
  (unchecked-short (ub16 num)))

(defn sb32 [num]
  (unchecked-int (ub32 num)))

(defn sb64 [num]
  (unchecked-long num))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; "Byte Vectors" implement a collection of primitive signed and unsigned byte
;; values cast appropriately from the JVM native (signed) two's complement
;; representation.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn long-to-octets
  "convert a long into a sequence of minimum PAD-COUNT unsigned values.
  The zeroes are padded to the msb"
  ([^long lng]
    (long-to-octets lng 8))
  ([^long lng pad-count]
    (let [pad (repeat pad-count (byte 0))
           raw-bytes (for [i (range 8)] (ldb (mask 8 (* i 8)) lng))
           value-bytes (drop-while clojure.core/zero? (reverse raw-bytes))]
      (vec (concat
             (into [] (drop (count value-bytes) pad))
             value-bytes)))))



(defn assemble-bytes [v]
  (reduce (fn
            ([] 0)
            ([tot pair] (dpb (mask 8 (* (first pair) 8))  tot (second pair))))
    (util/indexed (reverse v))))








;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Subject to Rewrite
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment 
(defn sbvec [thing]
  (cond
   (= (type thing) Long) (into (vector-of :byte)
                               (map unchecked-byte (long-to-octets thing)))
   (coll? thing)         (into (vector-of :byte)
                               (map unchecked-byte thing))))

(defn sbvector [& args]
  (sbvec args))

(defn make-sbvector [length initial-element]
  (sbvec (loop [len length v []]
         (if (<= len 0)
           v
           (recur (- len 1) (cons (unchecked-byte initial-element) v))))))
    
(defn ubvec [thing]
  (cond
   (= (type thing) Long) (into (vector-of :short)
                               (map unchecked-short (long-to-octets thing)))
   (coll? thing)         (into (vector-of :short)
                               (map unchecked-short thing))))

(defn ubvector [& args]
  (ubvec args))

(defn make-ubvector [length initial-element]
  (ubvec (loop [len length v []]
           (if (<= len 0)
             v
             (recur (- len 1) (cons (short initial-element) v))))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Hexadecimal String Representation 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn octet-hex [num]
  (str
    (+hex-chars+ (bit-shift-right num 4))  
    (+hex-chars+ (bit-and 0x0F num))))
 
(defn hex [thing]
  (cond
    (and (number? thing) (<  thing 0))     (hex (ubvec thing))
    (and (number? thing) (>=  thing 0 ))  (hex (ubvec thing))
    (coll? thing)   (apply str (into [] (map octet-hex thing)))))

(defn hex-str [s]
  (hex (.getBytes s)))

(defn unhex [s]
  (unchecked-long (read-string (str "0x" s))))

(defn unhex-str [s]
  (apply str (map char (unhex s))))
)
