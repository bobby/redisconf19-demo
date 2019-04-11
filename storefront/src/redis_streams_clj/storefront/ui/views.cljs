(ns redis-streams-clj.storefront.ui.views
  (:require [goog.string :as gstring]
            [goog.string.format]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [redis-streams-clj.storefront.ui.routes :as routes]
            [redis-streams-clj.storefront.ui.subs :as subs]
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
       {:header  "Welcome to our Coffee Shop."
        :content [:> ui/Column.Group
                  [:> ui/Column {:size 6}
                   [:> ui/Title {:size 4} "Please sign in!"]
                   [:form {:onSubmit (fn [e]
                                       (.preventDefault e)
                                       (re-frame/dispatch [:command/upsert-customer! @state]))}
                    [:> ui/Field
                     [:> ui/Label "Full Name"]
                     [:> ui/Control
                      [:> ui/Input {:type        "text"
                                    :required    true
                                    :placeholder "Joe Covfefe"
                                    :onChange    #(swap! state assoc :name (input-value-from-event %))}]]]
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

(defn menu-item
  [{:keys [id title description price photo_url] :as item}]
  [(let [state (reagent/atom {:menu_item_id id
                              :quantity     1})]
     (fn []
       [:> ui/Media
        [:> ui/Media.Item {:align "left"}
         [:> ui/Image.Container
          [:> ui/Image {:style {:width      "auto"
                                :height     "auto"
                                :max-height 150
                                :max-width  200}
                        :alt   (str "image of " title)
                        :src   photo_url}]]]
        [:> ui/Media.Item {:align "content"}
         [:> ui/Content
          [:> ui/Title {:size "4"} title]
          [:> ui/Title {:subtitle true :size "5"} (format-currency price)]
          [:> ReactMarkdown {:source description}]]]
        [:> ui/Media.Item {:align "right"}
         [:> ui/Field
          [:> ui/Control
           [:> ui/Textarea {:placeholder "How would you like it: milk, sugar, cream?"
                            :onChange    #(swap! state assoc :customization (input-value-from-event %))}]]]
         [:> ui/Field {:kind "group"}
          [:> ui/Control {:expanded true}
           [:> ui/Input {:type         "number"
                         :required     true
                         :defaultValue (:quantity @state)
                         :min          1
                         :max          100
                         :onChange     #(swap! state assoc :quantity (js/parseInt (input-value-from-event %)))}]]
          [:> ui/Control
           [:> ui/Button {:color   "info"
                          :onClick #(re-frame/dispatch [:command/add-items-to-basket! [@state]])}
            [:> ui/Icon {:style {:marginRight "5px"}}
             [:> FontAwesomeIcon {:icon "plus"}]]
            "Add to Basket"]]]]]))])

(defn menu []
  (let [menu @(re-frame/subscribe [::subs/menu])]
    [page-view
     {:header  "Menu"
      :content [:> ui/Column.Group
                (into [:> ui/Column {:size 8}]
                      (map menu-item (vals menu)))]}]))

(defn basket-item-row
  [{:keys [id menu_item quantity customization] :as order-item}]
  (let [{:keys [title price photo_url]} menu_item]
    [:> ui/Table.Row
     [:> ui/Table.Cell
      [:> ui/Media
       [:> ui/Media.Item {:align "left"}
        [:> ui/Image.Container {:style {:max-width 100}}
         [:> ui/Image {:alt title
                       :src photo_url}]]]
       [:> ui/Media.Item {:align "content"}
        [:> ui/Title {:size 5} title]
        [:p customization]]]]
     [:> ui/Table.Cell (format-currency price)]
     [:> ui/Table.Cell quantity]
     [:> ui/Table.Cell (format-currency (* quantity price))]
     [:> ui/Table.Cell
      [:> ui/Button {:onClick #(re-frame/dispatch [:command/remove-items-from-basket! [id]])}
       [:> ui/Icon
        [:> FontAwesomeIcon {:icon "trash"}]]]]]))

(defn basket-table
  [basket]
  [:> ui/Table {:fullwidth true
                :bordered  true}
   [:> ui/Table.Head
    [:> ui/Table.Row
     [:> ui/Table.Heading "Item"]
     [:> ui/Table.Heading "Price"]
     [:> ui/Table.Heading "Quantity"]
     [:> ui/Table.Heading "Item Total"]
     [:> ui/Table.Heading "Remove"]]]
   [:> ui/Table.Foot
    [:> ui/Table.Row
     [:> ui/Table.Cell {:colSpan 3}]
     [:> ui/Table.Heading (-> basket :total format-currency)]]]
   (into [:> ui/Table.Body] (map basket-item-row (:items basket)))])

(defn basket
  []
  (let [basket @(re-frame/subscribe [::subs/basket])]
    [page-view
     {:header  "My Basket"
      :content (if (-> basket :items seq)
                 [:> ui/Column.Group
                  [:> ui/Column {:size 8}
                   [basket-table basket]
                   [:> ui/Field
                    [:> ui/Control
                     [:> ui/Button {:color   "primary"
                                    :onClick #(re-frame/dispatch [:command/place-order!])}
                      "Place Order"]]]]]
                 [:> ui/Content
                  [:p
                   "Your shopping basket is empty. Please visit "
                   [:a {:href (routes/menu)} "our menu"]
                   " to add items to your basket."]])}]))

(defn order-item-row
  [{:keys [menu_item quantity customization status] :as order-item}]
  (let [{:keys [title price photo_url]} menu_item]
    [:> ui/Table.Row
     [:> ui/Table.Cell
      [:> ui/Media
       [:> ui/Media.Item {:align "left"}
        [:> ui/Image.Container {:style {:max-width 100}}
         [:> ui/Image {:alt title
                       :src photo_url}]]]
       [:> ui/Media.Item {:align "content"}
        [:> ui/Title {:size 5} title]
        [:p customization]]]]
     [:> ui/Table.Cell (format-currency price)]
     [:> ui/Table.Cell quantity]
     [:> ui/Table.Cell (format-currency (* quantity price))]
     [:> ui/Table.Cell status]]))

(defn order-table
  [order]
  [:> ui/Table {:fullwidth true
                :bordered  true}
   [:> ui/Table.Head
    [:> ui/Table.Row
     [:> ui/Table.Heading "Item"]
     [:> ui/Table.Heading "Price"]
     [:> ui/Table.Heading "Quantity"]
     [:> ui/Table.Heading "Item Total"]
     [:> ui/Table.Heading "Status"]]]
   [:> ui/Table.Foot
    [:> ui/Table.Row
     [:> ui/Table.Cell {:colSpan 3}]
     [:> ui/Table.Heading (-> order :total format-currency)]
     [:> ui/Table.Heading (:status order)]]]
   (into [:> ui/Table.Body] (map order-item-row (:items order)))])

(defn orders []
  (let [orders @(re-frame/subscribe [::subs/orders])]
    [page-view
     {:header  "My Orders"
      :content [:> ui/Column.Group
                (into [:> ui/Column {:size 8}]
                      (if (seq orders)
                        (for [{:keys [id items status] :as order} orders]
                          [:> ui/Card {:style {:margin-bottom "3em"}}
                           [:> ui/Card.Header
                            [:> ui/Card.Header.Title id]
                            [:> ui/Card.Header.Icon
                             [:> ui/Icon
                              [:> FontAwesomeIcon {:icon (case status
                                                           "placed" "user-clock"
                                                           "ready"  "cash-register"
                                                           "paid"   "check-circle"
                                                           "question")}]]]]
                           [:> ui/Card.Content
                            [order-table order]]
                           (into [:> ui/Card.Footer]
                                 (case status
                                   "placed" [[:> ui/Card.Footer.Item {:onClick #(js/alert "cancel!")}
                                              "Cancel Order"]]
                                   "ready"  [[:> ui/Card.Footer.Item {:onClick #(js/alert "pay for!")}
                                              "Pay for Order"]]
                                   "paid"   [[:> ui/Card.Footer.Item {:onClick #(js/alert "return items!")}
                                              "Return Items"]]
                                   []))])
                        [[:> ui/Content
                          [:p
                           "You haven't placed any orders yet. Please visit "
                           [:a {:href (routes/basket)} "your basket"]
                           " to place an order."]]]))]}]))

(defn not-found []
  [page-view
   {:header  "Not Found"
    :content [:p "Whoops, we couldn't find the page you were looking for!"]}])

(defn navbar
  [page]
  (let [navbar @(re-frame/subscribe [::subs/navbar])]
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
       [:> ui/Navbar.Segment {:align "start"}
        [:> ui/Navbar.Item
         {:href    (routes/menu)
          :active  (= page :menu)
          :managed true}
         "Menu"]]
       [:> ui/Navbar.Segment {:align "end"}
        [:> ui/Navbar.Item
         {:href    (routes/basket)
          :active  (= page :basket)
          :managed true}
         [:> ui/Icon {:style {:marginRight "5px"}}
          [:> FontAwesomeIcon {:icon "shopping-basket"}]]
         "My Basket"
         [:> ui/Tag {:color "success"
                     :style {:marginLeft "5px"}}
          (:basket-count navbar)]]
        [:> ui/Navbar.Item
         {:href    (routes/orders)
          :active  (= page :orders)
          :managed true}
         "My Orders"]]]]]))

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
        :menu
        [menu]
        :basket
        [basket]
        :orders
        [orders]
        [not-found])]
     [:> ui/Footer {:id "footer"}
      [:> ui/Container
       [:> ui/Content {:size "small"}
        [:> ui/Icon
         [:> FontAwesomeIcon {:icon "copyright"}]]
        "2019 Bobby Calderwood"]]]]))
