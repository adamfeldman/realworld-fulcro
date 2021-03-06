(ns conduit.ui.home
  (:require
   [fulcro.client.primitives :as prim :refer [defsc]]
   [conduit.handler.mutations :as mutations]
   [conduit.ui.pagination :as pagination]
   [conduit.ui.article-preview :as preview]
   [fulcro.client.mutations :as m :refer [defmutation]]
   [fulcro.client.data-fetch :as df]
   [fulcro.client.routing :as r]
   [fulcro.client.dom :as dom]
   [conduit.ui.routes :as routes]
   [conduit.util :as util]))

(defsc NavBar [this props]
  {:query [[r/routers-table '_]]}
  (let [[current-screen _]         (r/current-route props :router/top)
        whoami                     (prim/shared this :user/whoami)
        {current-user-id :user/id} whoami
        logged-in?                 (number? current-user-id)]
    (dom/nav :.navbar.navbar-light
      (dom/div :.container
        (dom/div :.navbar-brand
          "conduit")
        (dom/ul :.nav.navbar-nav.pull-xs-right
          (dom/li :.nav-item
            (dom/a :.nav-link
              {:className (when-not (#{:screen/editor :screen/log-in :screen/sign-up} current-screen)
                            "active")
               :href      (routes/feed-url (if logged-in? :personal :global))}
              "Home"))
          (when logged-in?
            (dom/li :.nav-item
              (dom/a :.nav-link
                {:className (when (= current-screen :screen/new) "active")
                 :href      (routes/to-path {:handler :screen/new})}
                (dom/i :.ion-compose)
                "New Post")))
          (when logged-in?
            (dom/li :.nav-item
              (dom/a :.nav-link
                {:className (when (= current-screen :screen/settings) "active")
                 :href      (routes/to-path {:handler :screen/settings})}
                (dom/i :.ion-gear-a)
                "Settings")))
          (when-not logged-in?
            (dom/li :.nav-item
              (dom/a :.nav-link
                {:className (when (= current-screen :screen/log-in) "active")
                 :href      (routes/to-path {:handler :screen/log-in})}
                "Login")))

          (when-not logged-in?
            (dom/li :.nav-item
              (dom/a :.nav-link
                {:className (when (= current-screen :screen/sign-up) "active")
                 :href      (routes/to-path {:handler :screen/sign-up})}
                "Sign up")))

          (when logged-in?
            (dom/li :.nav-item
              (dom/div :.nav-link
                {:onClick #(routes/log-out this)}
                "Log out"))))))))

(def ui-nav-bar (prim/factory NavBar))

(defsc Footer [this _]
  (dom/footer
    (dom/div :.container
      (dom/div :.logo-font "conduit")
      (dom/span :.attribution
        "An interactive learning project from "
        (dom/a {:href "https://thinkster.io"} "Thinkster")
        ". Code &amp; design licensed under MIT."))))

(def ui-footer (prim/factory Footer))

(defsc NotFound [this props]
  {:ident [:screen :screen-id]
   :initial-state (fn [params] {:screen :screen/not-found :screen-id :top})
   :query [:screen :screen-id]}
  (dom/div :.container.page
      (dom/div :.row
        (dom/div :.col-md-9
          (dom/div "Not found")))))

(defsc Banner [this _]
  (dom/div :.banner
    (dom/div :.container
      (dom/h1 :.logo-font "conduit")
      (dom/p "A place to show off your tech stack."))))

(def ui-banner (prim/factory Banner))

(defsc Tag [this {:tag/keys [tag]}]
  {:query [:tag/tag :tag/count]}
  (dom/a :.tag-pill.tag-default {:href (routes/to-path {:handler :screen/tag :route-params {:tag tag}})} tag))

(def ui-tag (prim/factory Tag {:keyfn :tag/tag}))

(defsc Tags [this tags]
  (dom/div :.col-md-3
    (dom/div :.sidebar
      (dom/p "Popular Tags")
      (dom/div :.tag-list
        (map ui-tag tags)))))

(def ui-tags (prim/factory Tags))

(defsc FeedSelector [this props {:keys [current-page]}]
  {:query []}
  (let [whoami                                 (prim/shared this :user/whoami)
        {:pagination/keys [list-type list-id]} current-page
        not-logged-in                          (= :guest (:user/id whoami))]
    (dom/div :.feed-toggle
      (dom/ul :.nav.nav-pills.outline-active
        (when (or (not not-logged-in)
                (and not-logged-in (= list-id :personal)))
          (dom/li :.nav-item
            (dom/a :.nav-link
              (merge {:className (if (= list-id :personal) "active" "disabled")
                      :href      (routes/feed-url :personal)}
                (when not-logged-in
                  {:onClick #(js/alert "You must log in first")}))
              "Your Feed")))
        (dom/li :.nav-item
          (dom/a :.nav-link
            {:className (if (= list-id :global) "active" "disabled")
             :href      (routes/feed-url :global)}
            "Global Feed"))
        (when (= list-type :articles/by-tag)
          (dom/li :.nav-item
            (dom/div :.nav-link.active
              "Tagged with `" list-id "`")))))))

(def ui-feed-selector (prim/factory FeedSelector))

(defsc FeedScreen [this {:keys [feed-id current-page] tags :tags/all}]
  {:ident [:screen/feed :feed-id]
   :initial-state (fn [params] {:screen       :screen/feed
                                :feed-id      :global
                                :current-page (prim/get-initial-state pagination/Page
                                                #:pagination{:list-type :articles/by-feed
                                                             :list-id   :global})})

   :query [:screen :feed-id
           {:current-page (prim/get-query pagination/Page)}
           {[:tags/all '_] (prim/get-query Tag)}]}
  (dom/div :.home-page
    (ui-banner)
    (dom/div :.container.page
      (dom/div :.row
        (dom/div :.col-md-9
          (ui-feed-selector (prim/computed {} {:current-page current-page}))
          (pagination/ui-page (prim/computed current-page {:load-page #(prim/transact! this `[(load-feed ~%)])})))
        (ui-tags tags)))))

(defsc TagScreen [this {:keys [tag current-page] tags :tags/all}]
  {:ident         [:screen/feed :tag]
   :initial-state (fn [params] {:screen       :screen/tag
                                :tag          "fulcro"
                                :current-page (prim/get-initial-state pagination/Page
                                                #:pagination{:list-type :articles/by-tag
                                                             :list-id   "fulcro"})})

   :query [:screen :tag
           {:current-page (prim/get-query pagination/Page)}
           {[:tags/all '_] (prim/get-query Tag)}]}
  (dom/div :.home-page
    (ui-banner)
    (dom/div :.container.page
      (dom/div :.row
        (dom/div :.col-md-9
          (ui-feed-selector (prim/computed {} {:current-page current-page}))
          (pagination/ui-page (prim/computed current-page {:load-page #(prim/transact! this `[(load-tag ~%)])})))
        (ui-tags tags)))))

;; mutations
(defmutation load-feed [{:pagination/keys [list-id] :as page}]
  (action [{:keys [state] :as env}]
    (swap! state
      #(update-in % [:screen/feed list-id]
         (fn [x] (if x
                   x
                   {:screen       :screen/feed
                    :feed-id      list-id
                    :current-page {}}))))
    (df/load-action env :paginated-list/articles
      pagination/Page {:params page
                       :target [:screen/feed list-id :current-page]}))
  (remote [env]
    (df/remote-load env))
  (refresh [env]
    [:pagination/list-type :current-page]))

(defmutation load-tag [{:pagination/keys [list-id] :as page}]
  (action [{:keys [state] :as env}]
    (swap! state
      #(update-in % [:screen/tag list-id]
         (fn [x] (if x
                   x
                   {:screen       :screen/tag
                    :tag          list-id
                    :current-page {}}))))
    (df/load-action env :paginated-list/articles
      pagination/Page {:params page
                       :target [:screen/tag list-id :current-page]}))
  (remote [env]
    (df/remote-load env))
  (refresh [env]
    [:pagination/list-type :current-page]))
