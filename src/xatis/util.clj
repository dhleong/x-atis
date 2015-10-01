(ns ^{:author "Daniel Leong"
      :doc "Utils"}
  xatis.util)

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

(defn typed-dispatch-fn
  [arg]
  (cond
    (vector? arg) :vector
    (map? arg) :map
    (string? arg) :string
    :else :default))
