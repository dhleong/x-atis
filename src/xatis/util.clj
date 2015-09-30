(ns ^{:author "Daniel Leong"
      :doc "Utils"}
  xatis.util)

(defn typed-dispatch-fn
  [arg]
  (cond
    (vector? arg) :vector
    (map? arg) :map
    (string? arg) :string
    :else :default))
