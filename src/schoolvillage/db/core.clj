(ns schoolvillage.db.core
  (:require [schoolvillage.utilities :refer :all]
            [cheshire.core :refer [generate-string parse-string]]
            [clojure.java.jdbc :as jdbc]
            [conman.core :as conman]
            [environ.core :refer [env]]
            [clojure.walk :refer :all])
  (:import org.postgresql.util.PGobject
           org.postgresql.jdbc4.Jdbc4Array
           clojure.lang.IPersistentMap
           clojure.lang.IPersistentVector
           [java.sql
            BatchUpdateException
            Date
            Timestamp
            PreparedStatement]))

(defonce ^:dynamic *conn* (atom nil))

(conman/bind-connection *conn* "sql/queries.sql")

(defn get-user [id]
  (first (select-user {:id (Integer. id)})))

(defn add-user-subject [id subject]
  (if (not-empty (select-subject-id-by-name {:subject subject}))
    (insert-user-subject<! {:id (Integer. id) :subject subject})))

(defn add-subjects [id args]
    (doseq [x (mapv str args)]
      (add-user-subject id x)))

(defn update-user [params]
  (let [id (Integer. (:id(update-user<! (assoc params :id (Integer. (:id params))))))]
    (add-subjects id (keys(stringify-keys params)))))

(defn add-user [params]
  (let [id (Integer. (:id (insert-user<! (assoc params :id 0))))]
    (add-subjects id (keys(stringify-keys params)))))

(defn get-all-subjects [zipcode] (select-subjects))

(defn get-all-users [] (select-all-users))

(defn get-all-subjects [] (select-subjects))

(defn get-flagged-users [] (select-flagged-users))

(defn get-pending-users [] (select-pending-users))

(defn get-recent-users [] (select-recent-users))

(defn get-user-by-url [url]
  (first (select-user-by-url {:url (str url)})))

(defn get-sages-by-subject [subject & [zip]]
  (filter #(.contains (get-nearby-zipcodes (or zip "") 5) (:zip %) zip) (select-users-by-subject {:subject subject})))

(defn set-new-status [id status]
  (update-status<! {:id id :status (str status)}))

(defn generate-url [params]
 (let [url (str (subs (get params :first_name) 0 1) (get params :last_name))
       old_url (last (get-similar-urls {:url url}))]
    (if (some? old_url)
      (str url (+ (Integer. (re-find #"\d+" (subs old_url (count url) ))) 1) )
      url
      )))

(def pool-spec
  {:adapter :postgresql
   :init-size 1
   :min-idle 1
   :max-idle 4
   :max-active 32})

(defn connect! []
  (conman/connect!
    *conn*
    (assoc
      pool-spec
      :jdbc-url (env :database-url))))

(defn disconnect! []
  (conman/disconnect! *conn*))

(defn to-date [sql-date]
  (-> sql-date (.getTime) (java.util.Date.)))

(extend-protocol jdbc/IResultSetReadColumn
  Date
  (result-set-read-column [v _ _] (to-date v))

  Timestamp
  (result-set-read-column [v _ _] (to-date v))

  Jdbc4Array
  (result-set-read-column [v _ _] (vec (.getArray v)))

  PGobject
  (result-set-read-column [pgobj _metadata _index]
    (let [type (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (parse-string value true)
        "jsonb" (parse-string value true)
        "citext" (str value)
        value))))

(extend-type java.util.Date
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt idx]
    (.setTimestamp stmt idx (Timestamp. (.getTime v)))))

(defn to-pg-json [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (generate-string value))))

(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value)))

(defn vec->arr [array-vector]
  (.createArrayOf *conn* "varchar" (into-array String array-vector)))

(extend-protocol jdbc/ISQLValue
    clojure.lang.IPersistentVector
    (sql-value [v]
    (vec->arr v)))
