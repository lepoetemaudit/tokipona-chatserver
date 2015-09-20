(ns tokichat.core
  (:use org.httpkit.server)
  (:require
    [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]]
    [clojure.core.match :refer [match]])
  (:gen-class))

(defonce conn-chan (chan))

(go
  (loop [conns #{}]
    (let [[message chan data] (<! conn-chan)]
      (match message
             :add       (recur (conj conns chan))
             :remove    (recur (disj conns chan))
             :broadcast (do (doseq [conn conns]
                              (send! conn data))
                            (recur conns))
             :else      (recur conns)))))

(defn handler [req]
  (with-channel req channel

    (on-close channel (fn [status]
      (>!! conn-chan [:remove channel])))

    (if-not (websocket? channel)
      ((send! channel "Websocket required") (close channel))

      (letfn [(start-fn [data]
          (>!! conn-chan [:broadcast channel data])
          start-fn)]

        (>!! conn-chan [:add channel])

        (let [state-func (atom start-fn)]
        (on-receive channel (fn [msg]
          (reset! state-func (@state-func msg)))))))))

(defn -main []
  (println "Running on port 8080")
  (run-server handler {:port 8080})
)