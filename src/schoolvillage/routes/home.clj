(ns schoolvillage.routes.home
  (:require [schoolvillage.db.core :as db :refer :all]
            [schoolvillage.layout :as layout]
            [compojure.core :refer :all]
            [ring.util.http-response :refer [ok]]
            [compojure.core :refer :all]
            [clojure.java.io :as io]))

(defn about-page []
  (layout/render "about.html"))

(defroutes home-routes
  (GET "/about" [] (about-page)))
