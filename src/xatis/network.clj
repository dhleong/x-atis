(ns ^{:author "Daniel Leong"
      :doc "Protocol for network actions"}
  xatis.network)

(defprotocol XAtisNetwork
  "Protocol for network actions"
  (config-voice!
    [this config voice-atis]
    "Configure a voice connection. 
    `config` is a map:
    {:id 'KLGA' :atis-frequency '127.050'
     :voice-server 'rw.liveatc.net'}
    `voice-atis` is the rendered voice ATIS text.")
  (connected?
    [this]
    "Returns true if we have an active connection 
    to the network.")
  (connect!
    [this params]
    "Request a connection to the network using
    the given parameters.")
  (disconnect!
    [this]
    "Sever any active connection with the network.")
  (send-to!
    [this cid message]
    "Send a private message to the provided pilot"))
