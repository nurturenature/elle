(ns elle.consistency-model-test
  "Elle finds anomalies in histories. This namespace helps turn those anomalies
  into claims about what kind of consistency models are supported by, or ruled
  out by, a given history."
  (:require [bifurcan-clj [graph :as bg]]
            [clojure [pprint :refer [pprint]]
                     [test :refer :all]]
            [elle.consistency-model :refer :all]))

(deftest implied-anomalies-test
  (is (every? keyword? (bg/vertices implied-anomalies))))

(deftest all-anomalies-test
  ; Note: these are in implication & severity order
  (is (= [:G-MSR :G-SI :G-SIb :G-cursor :G-monotonic :G-update :G0 :G1b :GSIa :cyclic-versions :dirty-update :duplicate-elements :future-read :incompatible-order :internal :lost-update :predicate-read-miss :G1a :G1c :write-skew :G-single :G1 :G2-item :G-nonadjacent :GSIb :G2 :GSI :G0-process :G1c-process :G-single-process :G1-process :G2-item-process :G-nonadjacent-process :G2-process :G0-realtime :G1c-realtime :G-single-realtime :G1-realtime :G2-item-realtime :G-nonadjacent-realtime :G2-realtime]
         (vec all-anomalies))))

(deftest all-implied-anomalies-test
  (is (= [:G-SI] (seq (all-implied-anomalies [:G-SI])))))

(deftest weakest-models-test
  (let [s #(set (map friendly-model-name (weakest-models %)))]
    (testing "empty"
      (is (= #{} (s []))))

    (testing "simple"
      (is (= #{:serializable}
             (s [:serializable :strict-serializable]))))

    (testing "independent"
      (is (= #{:read-your-writes
               :read-committed}
             (s [:repeatable-read
                 :read-committed
                 :sequential
                 :read-your-writes]))))

    (testing "cerone-end-to-end"
      (is (= #{:read-atomic}
             (s [:serializable :read-atomic])
             (s [:read-atomic :serializable]))))))

(deftest strongest-models-test
  (let [s #(set (map friendly-model-name (strongest-models %)))]
    (testing "empty"
      (is (= #{} (s []))))

    (testing "simple"
      (is (= #{:strong-serializable}
             (s [:serializable :strong-serializable :strict-serializable]))))

    (testing "independent"
      (is (= #{:repeatable-read
               :sequential}
             (s [:repeatable-read
                 :read-committed
                 :sequential
                 :read-your-writes]))))

    (testing "cerone-end-to-end"
      (is (= #{:serializable}
             (s [:serializable :read-atomic])
             (s [:read-atomic :serializable]))))))

(let [as #(into (sorted-set)
                (anomalies-prohibited-by %))]
  (deftest anomalies-prohibited-by-test
    (testing "read committed"
      (is (= #{:dirty-update
               :G1a
               :G1b
               :G1c
               :G1
               :G0
               :cyclic-versions
               :duplicate-elements
               :incompatible-order
               :future-read}
             (as [:read-committed]))))

  (testing "unknown anomaly"
    (is (thrown-with-msg? IllegalArgumentException #"Unknown consistency model"
                          (anomalies-prohibited-by
                            [:stricct-snappshot-isolation]))))))


(let [as #(into (sorted-set)
                (map friendly-model-name (anomalies->impossible-models %)))]

  (deftest anomaly->impossible-models-test
    (testing "G-single"
      (is (= #{:consistent-view
               :forward-consistent-view
               :strong-serializable
               :serializable
               :snapshot-isolation
               :strong-session-serializable
               :strong-session-snapshot-isolation
               :strong-snapshot-isolation
               :update-serializable}
             (as [:G-single]))))

    (testing "G-SI"
      (is (= #{:snapshot-isolation
               :serializable
               :strong-serializable
               :strong-session-serializable
               :strong-session-snapshot-isolation
               :strong-snapshot-isolation}
             (as [:G-SI]))))

    (testing "dirty update"
      (is (= #{:read-committed
               :monotonic-atomic-view
               :snapshot-isolation
               :repeatable-read
               :ROLA
               :serializable
               :strong-serializable
               :update-serializable
               :cursor-stability
               :forward-consistent-view
               :consistent-view
               :monotonic-view
               :monotonic-snapshot-read
               :causal-cerone
               :parallel-snapshot-isolation
               :prefix
               :read-atomic
               :strong-session-serializable
               :strong-session-snapshot-isolation
               :strong-snapshot-isolation
               }
             (as [:dirty-update]))))

    (testing "internal"
      (is (= #{:causal-cerone
               :parallel-snapshot-isolation
               :prefix
               :read-atomic
               :ROLA
               :serializable
               :snapshot-isolation
               :strong-serializable
               :strong-session-serializable
               :strong-session-snapshot-isolation
               :strong-snapshot-isolation
               }
             (as [:internal]))))))

(deftest friendly-boundary-test
  (testing "empty"
    (is (= {:not       #{}
            :also-not  #{}}
           (friendly-boundary []))))

  (testing "cyclic-versions"
    (is (= {:not #{:read-uncommitted}
            :also-not #{:read-committed
                        :snapshot-isolation
                        ; Not sure about this one. PSI allows long fork, and
                        ; I think long fork explicitly looks like inconsistent
                        ; version orders. Then again, it's not so much that the
                        ; version orders themselves are inconsistent, as the
                        ; transactions using those orders are. OTOH, if we've
                        ; invalidated read committed, then we've also broken
                        ; read atomic, and that covers the various SIs!
                        :parallel-snapshot-isolation
                        :repeatable-read
                        :serializable
                        :strong-serializable
                        :consistent-view
                        :cursor-stability
                        :monotonic-atomic-view
                        :monotonic-snapshot-read
                        :update-serializable
                        :forward-consistent-view
                        :monotonic-view
                        :strong-session-serializable
                        :strong-session-snapshot-isolation
                        :strong-snapshot-isolation
                        :ROLA
                        :causal-cerone
                        :prefix
                        :read-atomic
                        }}
           (friendly-boundary [:cyclic-versions]))))

  (testing "internal"
    (is (= {:not #{:read-atomic}
            :also-not #{:causal-cerone
                        :parallel-snapshot-isolation
                        :prefix
                        :ROLA
                        :serializable
                        :snapshot-isolation
                        :strong-serializable
                        :strong-session-serializable
                        :strong-session-snapshot-isolation
                        :strong-snapshot-isolation}}
           (friendly-boundary [:internal]))))

  (testing "G1a"
    (is (= {:not      #{:read-committed}
            :also-not #{:read-atomic
                        :ROLA :causal-cerone :consistent-view :cursor-stability
                        :forward-consistent-view :monotonic-atomic-view
                        :monotonic-snapshot-read :monotonic-view
                        :parallel-snapshot-isolation :prefix :repeatable-read
                        :serializable :snapshot-isolation
                        :strong-serializable
                        :strong-session-serializable
                        :strong-session-snapshot-isolation
                        :strong-snapshot-isolation
                        :update-serializable}}
           (friendly-boundary [:G1a]))))

  (testing "G-single"
    (is (= {:not #{:consistent-view}
            :also-not #{:snapshot-isolation
                        :serializable
                        :strong-serializable
                        :strong-session-snapshot-isolation
                        :strong-snapshot-isolation
                        :strong-session-serializable
                        :forward-consistent-view
                        :update-serializable}}
           (friendly-boundary [:G-single]))))

  (testing "internal and G2"
    (is (= {:not #{:read-atomic}
            :also-not #{:ROLA :causal-cerone
                        :parallel-snapshot-isolation
                        :prefix
                        :serializable
                        :snapshot-isolation
                        :strong-serializable
                        :strong-session-serializable
                        :strong-session-snapshot-isolation
                        :strong-snapshot-isolation}}
           (friendly-boundary [:G2 :internal]))))

  (testing "lost update"
    (is (= #{:ROLA :cursor-stability}
           (:not (friendly-boundary [:lost-update])))))

  (testing "G-single-realtime"
    (is (= {:not #{:strong-snapshot-isolation}
            :also-not #{:strong-serializable}}
           (friendly-boundary [:G-single-realtime])))))

; This is more for building plots than anything else; it's not actually testing
; anything.
(deftest plot-test
  (plot-models!)
  (plot-anomalies!)
  (plot-severity!))
