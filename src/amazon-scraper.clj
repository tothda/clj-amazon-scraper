(ns amazon-scraper
  (:require [net.cgrand.enlive-html :as e]
            [clojure.contrib.duck-streams :as duck]
            [clojure.contrib.str-utils :as str]))

(def *input-file* "../amazon_books.txt")
(def *output-file* "../books.html")
(def *template-file* "../template/books.html")

(defn render [xs] (apply str xs))
(defn write-file [filename str] (duck/spit (java.io.File. filename) str))
(defn resource [url] (e/html-resource (java.net.URL. url)))
(defn read-file [filename] (duck/read-lines filename))
(defn join [coll] (str/str-join "" coll))

(def *title-selector* [:#btAsinTitle e/text-node])
(def *authors-selector*  [:.buying :> :span :> :a e/text-node])
(def *pages-selector* [:#divsinglecolumnminwidth :> [:table ]
                       :.bucket :.content :> :ul :>
                       [:li (e/nth-child 1)] :> e/text-node])
(def *publisher-selector* [:#divsinglecolumnminwidth :> [:table ] :.bucket
                           :.content :> :ul :> [:li (e/nth-child 2)]
                           :> e/text-node])
(def *image-url-selector* [:#prodImage])

(defn extr [{url :url :as data}]
  (let [r (resource url)
        title (join (e/select r *title-selector*))
        authors (e/select r *authors-selector*)
        pages (join (e/select r *pages-selector*))
        pub (join (e/select r *publisher-selector*))
        image-url (-> (e/select r *image-url-selector*)
                      first
                      :attrs
                      :src)
        [_ publisher year] (re-find #"(.*) \(.*(\d{4})\)" pub)
        [_ pages] (re-find #"(\d+)" pages)]
    (merge data {:title title
                 :authors authors
                 :pages pages
                 :pub pub
                 :image-url image-url
                 :publisher publisher
                 :year year})))

(def book-template-file (java.io.File. *template-file*))

(def *row-selector* [:table#books :tbody :> e/first-child])

(e/defsnippet row-template book-template-file *row-selector*
  [{:keys [title authors publisher year pages url image-url]}]
  [:.title :a] (e/do->
                (e/html-content title)
                (e/set-attr :href url))
  [:.author] (e/html-content (str/str-join ", " authors))
  [:.publisher] (e/html-content publisher)
  [:.year] (e/html-content year)
  [:.pages] (e/html-content pages)
  [:.image :img] (e/set-attr :src image-url))

(e/deftemplate books book-template-file [ctx]
  [:tbody] (e/content (map row-template ctx)))

(defn write [book-list] (write-file *output-file* (render (books book-list))))

(defn transform [url-coll]
  (write (pmap #(extr {:url %}) url-coll)))

(defn scrape [ & n]
  (let [url-coll (read-file *input-file*)]
    (transform url-coll)))
