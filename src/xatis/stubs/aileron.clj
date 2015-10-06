(ns ^{:author "Daniel Leong"
      :doc "Aileron stub, for when you don't have the real thing"}
  xatis.stubs.aileron
  (:require [stubby.core :refer [defstub]]))

(defstub connect!
  [conn & _]
  (swap! conn assoc :connected true))
(defstub connected?
  [conn]
  (get @conn :connected))
(defstub create-connection
  [& _]
  (atom {}))
(defstub disconnect!
  [conn]
  (swap! conn assoc :connected false))
(defstub field)
(defstub listen)
(defstub send!)
(defstub update!
  [& _]
  nil)

