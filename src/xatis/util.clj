(ns ^{:author "Daniel Leong"
      :doc "Utils"}
  xatis.util)

(def atis-text-max-width 64)

;;
;; Internal
;;

(defn find-line-end
  ([text start max-length]
   (find-line-end text start max-length start))
  ([text start max-length last-end]
   (let [next-end (.indexOf text " " last-end)
         next-length (- next-end start)
         last-was-start? (= start last-end)
         text-count (count text)]
     (cond 
       (>= start text-count) -1
       (> next-length max-length) (dec last-end)
       (= next-length max-length) next-end
       (and (= -1 next-end)
            last-was-start?) text-count
       (and (not= -1 next-end)
            (< next-length max-length)) (recur
                                          text start max-length 
                                          (inc next-end))
       :else text-count))))

;;
;; Public utils
;;

(defn re-replace
  "Functional regex processing. `pred` will be called
  for each successive match in `text`; if it returns
  a non-nil, the match provided to it will be replaced
  with the result."
  [regex pred text]
  {:pre [(instance? java.util.regex.Pattern regex)
         (fn? pred)
         (string? text)]}
  (let [m (re-matcher regex text)]
    (if-let [found (re-find m)]
      (if-let [result (pred found)]
        ;; non-nil result should be replaced
        (recur 
          regex
          pred
          (.replaceFirst
            m 
            result))
        ;; nil result can be ignroed
        (recur regex pred text))
      ;; nothing more to do here
      text)))

(defn split-atis
  ([text]
   (split-atis text atis-text-max-width))
  ([text max-width]
   (loop [parts []
          start 0]
     (let [end (find-line-end text start max-width)]
       (if (= -1 end)
         parts
         (recur
           (conj parts (subs text start end))
           (inc end)))))))

(defn typed-dispatch-fn
  [arg]
  (cond
    (seq? arg) :seq
    (map? arg) :map
    (string? arg) :string
    :else :default))
