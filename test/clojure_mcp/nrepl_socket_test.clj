(ns clojure-mcp.nrepl-socket-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [clojure-mcp.config :as config]
   [clojure-mcp.core :as core]
   [clojure-mcp.nrepl :as nrepl]
   [clojure-mcp.nrepl-launcher :as launcher]
   [clojure-mcp.tool-system :as tool-system]
   [clojure-mcp.tools.eval.tool :as eval-tool]
   [clojure-mcp.tools.nrepl-ports.core :as ports-core]
   [clojure-mcp.tools.nrepl-ports.tool :as ports-tool]
   [nrepl.server :as nrepl-server]))

(defn- with-socket-server [f]
  (let [dir (io/file (System/getProperty "java.io.tmpdir")
                     (str "clojure-mcp-uds-" (System/nanoTime)))
        _ (.mkdirs dir)
        socket-file (io/file dir nrepl/nrepl-socket-filename)
        server (nrepl-server/start-server :socket (.getAbsolutePath socket-file))]
    (try
      (f dir socket-file)
      (finally
        (nrepl-server/stop-server server)
        (doseq [file (reverse (file-seq dir))]
          (.delete file))))))

(defn- service-for-dir [dir]
  (config/set-config* (nrepl/create nil) :nrepl-user-dir (.getCanonicalPath dir)))

(defn- eval-with-service [service code]
  (let [client-atom (atom service)
        tool (eval-tool/create-eval-tool client-atom)
        validated (tool-system/validate-inputs tool {:code code})
        result (tool-system/execute-tool tool validated)]
    {:validated validated
     :result result}))

(deftest read-nrepl-socket-file-test
  (testing "detects nrepl.socket in the project directory"
    (with-socket-server
      (fn [dir socket-file]
        (is (= (.getCanonicalPath socket-file)
               (nrepl/read-nrepl-socket-file (.getAbsolutePath dir)))))))

  (testing "returns nil when there is no directory"
    (is (nil? (nrepl/read-nrepl-socket-file nil)))))

(deftest resolve-connection-target-test
  (testing "an explicit service target wins over files"
    (with-socket-server
      (fn [dir socket-file]
        (spit (io/file dir ".nrepl-port") "12345")
        (is (= {:port 12345}
               (nrepl/resolve-connection-target {} (.getAbsolutePath dir))))
        (is (= {:socket (.getCanonicalPath socket-file)}
               (nrepl/resolve-connection-target
                {:socket (.getCanonicalPath socket-file)}
                (.getAbsolutePath dir)))))))

  (testing "nrepl.socket is used when .nrepl-port is absent"
    (with-socket-server
      (fn [dir socket-file]
        (is (= {:socket (.getCanonicalPath socket-file)}
               (nrepl/resolve-connection-target {} (.getAbsolutePath dir))))))))

(deftest autodetect-and-eval-test
  (testing "eval autodetects nrepl.socket and initializes the connection"
    (with-socket-server
      (fn [dir socket-file]
        (let [{:keys [validated result]}
              (eval-with-service (service-for-dir dir) "(+ 1 2)")]
          (is (= (.getCanonicalPath socket-file) (:socket validated)))
          (is (nil? (:port validated)))
          (is (false? (:error result)))
          (is (some #(= [:value "3"] %) (:outputs result)))
          (is (= :clj (get-in result [:context :env-type]))))))))

(deftest explicit-socket-eval-test
  (testing "eval accepts an explicit :socket parameter"
    (with-socket-server
      (fn [_dir socket-file]
        (let [tool (eval-tool/create-eval-tool (atom (nrepl/create nil)))
              validated (tool-system/validate-inputs
                         tool {:code "(* 6 7)"
                               :socket (.getCanonicalPath socket-file)})
              result (tool-system/execute-tool tool validated)]
          (is (= (.getCanonicalPath socket-file) (:socket validated)))
          (is (false? (:error result)))
          (is (some #(= [:value "42"] %) (:outputs result)))))))

  (testing "rejects specifying both :port and :socket"
    (is (thrown? Exception
                 (tool-system/validate-inputs
                  (eval-tool/create-eval-tool (atom (nrepl/create nil)))
                  {:code "1" :port 7888 :socket "/tmp/nrepl.socket"})))))

(deftest discover-socket-server-test
  (testing "list-nrepl-ports discovery includes Unix sockets"
    (with-socket-server
      (fn [dir socket-file]
        (with-redefs [ports-core/get-listening-jvm-ports (constantly [])]
          (let [results (ports-core/discover-nrepl-ports (.getCanonicalPath dir))
                socket-result (some #(when (= (.getCanonicalPath socket-file)
                                              (:socket %))
                                       %)
                                    results)]
            (is (= 1 (count results)))
            (is (some? socket-result))
            (is (true? (:valid socket-result)))
            (is (= :nrepl-socket-file (:source socket-result)))
            (is (= :clj (:env-type socket-result)))))))))

(deftest socket-formats-as-unix-target-test
  (testing "socket results are displayed as unix: paths"
    (let [formatted (ports-tool/format-port-info
                     {:socket "/tmp/nrepl.socket"
                      :source :nrepl-socket-file
                      :valid true
                      :env-type :clj
                      :project-dir "/tmp/project"
                      :matches-cwd true}
                     true)]
      (is (str/includes? formatted "unix:/tmp/nrepl.socket")))))

(deftest core-accepts-socket-target-test
  (testing "a socket is a valid connection target at startup"
    (is (= {:socket "/tmp/nrepl.socket"}
           (core/ensure-port {:socket "/tmp/nrepl.socket"}))))

  (testing "an explicit socket disables auto-start and fallback spawning"
    (is (not (launcher/should-start-nrepl?
              {:socket "/tmp/nrepl.socket"
               :project-dir "/tmp/project"
               :start-nrepl-cmd ["should-not-run"]})))
    (is (= {:socket "/tmp/nrepl.socket"}
           (launcher/maybe-start-fallback-nrepl
            {:socket "/tmp/nrepl.socket"
             :fallback-nrepl true
             :fallback-nrepl-cmd ["should-not-run"]})))))
