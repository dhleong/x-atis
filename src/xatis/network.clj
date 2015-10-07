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
    `voice-atis` is the rendered voice ATIS text.
    If `config` is nil, disconnect the voice
    atis and revert frequency to 199.998")
  (connected?
    [this]
    "Returns true if we have an active connection 
    to the network.")
  (connect!
    [this on-fail]
    "Prompt to connect to the network. on-fail should
    be called on a failed connection.")
  (disconnect!
    [this]
    "Sever any active connection with the network.")
  (send-to!
    [this cid message]
    "Send a private message to the provided pilot"))
