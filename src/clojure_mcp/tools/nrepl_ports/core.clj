(ns clojure-mcp.tools.nrepl-ports.core
  "Core implementation for nREPL connection discovery.
   Discovers running nREPL servers by checking .nrepl-port and nrepl.socket
   files and scanning for JVM processes listening on TCP ports."
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]
            [clojure-mcp.nrepl :as mcp-nrepl]
            [nrepl.core :as nrepl]))

(defn read-nrepl-port-file
  "Read port number from .nrepl-port file in the given directory.
   Returns nil if file doesn't exist or on error."
  [dir]
  (mcp-nrepl/read-nrepl-port-file dir))

(defn read-nrepl-socket-file
  "Return the canonical nrepl.socket path in the given directory, or nil."
  [dir]
  (mcp-nrepl/read-nrepl-socket-file dir))

(defn parse-lsof-ports
  "Parse port numbers from lsof output.
   Matches patterns like: 'TCP *:7888 (LISTEN)' or 'TCP 127.0.0.1:7889 (LISTEN)'"
  [lsof-output]
  (when lsof-output
    (->> (str/split-lines lsof-output)
         (keep (fn [line]
                 (when-let [[_ port] (re-find #"TCP\s+(?:\*|[\d.]+):(\d+)\s+\(LISTEN\)" line)]
                   (parse-long port))))
         distinct
         vec)))

(defn get-listening-jvm-ports
  "Find ports where Java/Clojure/Babashka processes are listening.
   Returns vector of port numbers, empty vector on error."
  []
  (try
    (let [proc (.. (ProcessBuilder. ["sh" "-c" "lsof -nP -iTCP -sTCP:LISTEN 2>/dev/null | grep -Ei 'java|clojure|babashka|bb|nrepl'"])
                   (redirectErrorStream true)
                   start)
          _ (.waitFor proc 5 java.util.concurrent.TimeUnit/SECONDS)
          output (slurp (.getInputStream proc))]
      (or (parse-lsof-ports output) []))
    (catch Exception _
      [])))

(defn detect-nrepl-env-type
  "Detect the environment type from nREPL describe response.
   Returns :clj, :bb, :basilisp, :scittle, :shadow, or :unknown."
  [describe-resp]
  (let [versions (:versions describe-resp)
        aux-ns (get-in describe-resp [:aux :current-ns])]
    (cond
      (contains? versions :babashka) :bb
      (contains? versions :basilisp) :basilisp
      (contains? versions :sci-nrepl) :scittle
      ;; Check for shadow-cljs (runs on top of Clojure, has shadow.user ns)
      (and (contains? versions :clojure) (= aux-ns "shadow.user")) :shadow
      (contains? versions :clojure) :clj
      :else :unknown)))

(defn get-project-dir-expr
  "Get the expression to evaluate to find the project directory based on env type."
  [env-type]
  (case env-type
    :clj "(System/getProperty \"user.dir\")"
    :bb "(System/getProperty \"user.dir\")"
    :basilisp "(import os)\n(os/getcwd)"
    :scittle nil
    :shadow "(System/getProperty \"user.dir\")"
    nil))

(defn- connect-target [target]
  (if-let [socket (:socket target)]
    (nrepl/connect :socket socket)
    (nrepl/connect :host (or (:host target) "localhost") :port (:port target))))

(defn gather-port-info
  "Gather information about an nREPL connection target using a single connection.
   Target is either {:host h :port n} or {:socket path}.
   Returns a map with connection info, or an invalid map if connection fails."
  [{:keys [host port socket] :as target} source current-dir]
  (let [base {:host host
              :port port
              :socket socket
              :source source}
        invalid (merge base
                       {:valid false
                        :session-count 0
                        :env-type nil
                        :project-dir nil
                        :matches-cwd false})]
    (try
      (let [conn (connect-target target)
            client (nrepl/client conn 1000)]
        (try
          ;; Try to get sessions to validate this is a real nREPL
          (let [sessions-resp (first (nrepl/message client {:op "ls-sessions"}))
                sessions (:sessions sessions-resp)]
            (if sessions
              ;; Valid nREPL - get env type and project dir
              (let [describe-resp (first (nrepl/message client {:op "describe"}))
                    env-type (detect-nrepl-env-type describe-resp)
                    dir-expr (get-project-dir-expr env-type)
                    project-dir (when dir-expr
                                  (try
                                    (let [eval-resp (nrepl/message client {:op "eval" :code dir-expr})
                                          value-resp (first (filter :value eval-resp))]
                                      (when-let [v (:value value-resp)]
                                        ;; Strip quotes if present
                                        (str/replace v #"^\"|\"$" "")))
                                    (catch Exception _ nil)))
                    matches-cwd (and project-dir
                                     (= project-dir current-dir))]
                (merge base
                       {:valid true
                        :session-count (count sessions)
                        :env-type env-type
                        :project-dir project-dir
                        :matches-cwd matches-cwd}))
              ;; Invalid nREPL
              invalid))
          (finally
            (.close conn))))
      (catch Exception e
        (log/debug "Failed to connect to" (or socket (str host ":" port))
                   "-" (.getMessage e))
        ;; Connection failed
        invalid))))

(defn discover-nrepl-ports
  "Discover potential nREPL connections by:
   1. Checking .nrepl-port and nrepl.socket files in the current directory
   2. Finding Java/Clojure/Babashka processes listening on TCP ports (lsof)
   3. Validating discovered targets by checking if they respond to ls-sessions
   4. Detecting environment type (clj, bb, basilisp, shadow, etc.)
   5. Getting project directory and checking if it matches current working directory

   Arguments:
   - current-dir: The directory to use as current working directory for matching

   Returns vector of maps with:
   - :host - Host string for TCP targets (\"localhost\")
   - :port - Port number for TCP targets (nil for Unix sockets)
   - :socket - Unix domain socket path (nil for TCP targets)
   - :source - How discovered (:nrepl-port-file, :nrepl-socket-file, or :lsof)
   - :valid - Boolean indicating if target responds to nREPL ls-sessions op
   - :env-type - Environment type (:clj, :bb, :basilisp, :scittle, :shadow, :unknown, or nil if invalid)
   - :project-dir - Project directory path from nREPL server (or nil)
   - :matches-cwd - Boolean indicating if project-dir matches current working directory"
  [current-dir]
  (let [;; Collect TCP port candidates
        port-file-port (read-nrepl-port-file current-dir)
        lsof-ports (get-listening-jvm-ports)
        tcp-targets (->> (distinct (concat (when port-file-port [port-file-port])
                                           lsof-ports))
                         (mapv (fn [port]
                                 {:host "localhost"
                                  :port port
                                  :source (if (= port port-file-port)
                                            :nrepl-port-file
                                            :lsof)})))
        ;; A Unix socket in the project directory
        socket-target (when-let [socket (read-nrepl-socket-file current-dir)]
                        {:socket socket
                         :source :nrepl-socket-file})
        targets (cond-> tcp-targets
                  socket-target (conj socket-target))

        ;; Validate each target and gather info in parallel
        results (pmap #(gather-port-info % (:source %) current-dir) targets)]
    (vec results)))
