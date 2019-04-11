(ns redis-streams-clj.barista.ui.views
  (:require [goog.string :as gstring]
            [goog.string.format]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [redis-streams-clj.barista.ui.routes :as routes]
            [redis-streams-clj.barista.ui.subs :as subs]
            [rbx :as ui]
            ["@fortawesome/fontawesome-svg-core" :as fontawesome]
            ["@fortawesome/free-solid-svg-icons" :as icons]
            ["@fortawesome/react-fontawesome" :refer [FontAwesomeIcon]]
            ["react-markdown" :as ReactMarkdown]))

;;;; Font Awesome Initialization

;; TODO: can probably be optimized by specifying the icons we actually
;; use
(fontawesome/library.add icons/fas)

;;;; Helpers

(defn input-value-from-event
  [^js/Event event]
  (some-> event .-target .-value))

(defn format-currency
  [cents]
  (gstring/format "$%.2f" (/ cents 100)))

;;;; Views

(defn page-view [{:keys [header content]}]
  [:> ui/Container
   [:> ui/Title header]
   [:main content]])

(defn home []
  (let [state (reagent/atom {})]
    (fn []
      [page-view
       {:header  "Welcome to work, Barista!"
        :content [:> ui/Column.Group
                  [:> ui/Column {:size 6}
                   [:> ui/Title {:size 4} "Please sign in to your work queue!"]
                   [:form {:onSubmit (fn [e]
                                       (.preventDefault e)
                                       (re-frame/dispatch [:command/sign-in! @state]))}
                    [:> ui/Field
                     [:> ui/Label "Email"]
                     [:> ui/Field
                      [:> ui/Control
                       [:> ui/Input {:type        "email"
                                     :required    true
                                     :placeholder "you@example.com"
                                     :onChange    #(swap! state assoc :email (input-value-from-event %))}]]]]
                    [:> ui/Field {:kind "group"}
                     [:> ui/Control
                      [:> ui/Button {:color "primary"
                                     :type  "Submit"}
                       "Submit"]]
                     [:> ui/Control
                      [:> ui/Button {:inverted true
                                     :onClick  #(js/history.back)}
                       "Cancel"]]]]]]}])))

(defn order-item
  [{:keys [menu_item quantity customization status]}]
  (let [{:keys [title price photo_url]} menu_item]
    [:> ui/Media
     [:> ui/Media.Item {:align "left"}
      [:> ui/Image.Container {:style {:max-width 100}}
       [:> ui/Image {:alt title
                     :src photo_url}]]]
     [:> ui/Media.Item {:align "content"}
      [:> ui/Title {:size 5} title]
      [:p
       [:strong "Quantity: "]
       quantity]
      (when customization
        [:p
         [:strong "Customization: "]
         customization])]]))

(defn work-queue []
  (let [barista @(re-frame/subscribe [::subs/barista])]
    [page-view
     {:header  (str "Work Queue: " (:email barista))
      :content [:> ui/Content
                (if-let [item (:current_item barista)]
                  [order-item item]
                  [:p "You don't have an item in your work queue. Please click the button below to claim an item."])
                [:> ui/Button.Group {:style {:marginTop "1em"}}
                 [:> ui/Button {:color   "info"
                                :onClick #(re-frame/dispatch [:command/claim-next-item!])}
                  "Ready for Next Item"]
                 (when-let [item-id (some-> barista :current_item :id)]
                   [:> ui/Button {:color   "success"
                                  :onClick #(re-frame/dispatch [:command/complete-current-item! item-id])}
                    "Complete Current Item"])]]}]))

(defn not-found []
  [page-view
   {:header  "Not Found"
    :content [:p "Whoops, we couldn't find the page you were looking for!"]}])

(defn navbar
  [page]
  (let [barista @(re-frame/subscribe [::subs/navbar])]
    [:> ui/Container
     [:> ui/Navbar {:transparent true}
      [:> ui/Navbar.Brand
       [:> ui/Navbar.Item
        {:href (routes/home)}
        [:> ui/Icon {:style {:marginRight "5px"}}
         [:> FontAwesomeIcon {:icon "coffee"}]]
        "A Streaming Cup 'o Joe"]
       [:> ui/Navbar.Burger]]
      [:> ui/Navbar.Menu
       [:> ui/Navbar.Segment {:align "end"}
        (if barista
          [:> ui/Navbar.Item
           {:href    (routes/work-queue)
            :active  (= page :work-queue)
            :managed true}
           [:> ui/Icon {:style {:marginRight "5px"}}
            [:> FontAwesomeIcon {:icon "list"}]]
           "Work Queue"
           [:> ui/Tag {:color "success"
                       :style {:marginLeft "5px"}}
            (:queue_length barista)]]
          [:> ui/Navbar.Item
           {:href    (routes/home)
            :active  (= page :home)
            :managed true}
           "Sign In"])]]]]))

(defn notifications
  []
  (let [notifications @(re-frame/subscribe [::subs/notifications])]
    [:> ui/Section
     [:> ui/Container
      [:> ui/Column.Group
       (into [:> ui/Column {:size 4 :offset 8}]
             (for [[id {:keys [color message] :as n}] notifications]
               [:> ui/Notification {:color color}
                [:> ui/Delete {:as      "button"
                               :onClick #(re-frame/dispatch [:command/dismiss-notification id])}]
                message]))]]]))

(defn app-root []
  (let [{:keys [page]} @(re-frame/subscribe [::subs/app-view])]
    [:div
     [navbar page]
     [notifications]
     [:> ui/Section
      (case page
        :home
        [home]
        :work-queue
        [work-queue]
        [not-found])]
     [:> ui/Footer {:id "footer"}
      [:> ui/Container
       [:> ui/Content {:size "small"}
        [:> ui/Icon
         [:> FontAwesomeIcon {:icon "copyright"}]]
        "2019 Bobby Calderwood"]]]]))
