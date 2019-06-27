(ns redis-streams-clj.common.domain)

(def menu
  [{:id          "e19fa2b0-5662-11e9-a902-dac8687b984b"
    :title       "Cappuccino"
    :description "Espresso with streamed milk. Classic."
    :price       1000
    :photo_url   "/img/products/cappuccino.jpg"}
   {:id          "e7746720-5662-11e9-a902-dac8687b984b"
    :title       "Americano"
    :description "Espresso with hot water, like a 'Merican."
    :price       800
    :photo_url   "/img/products/americano.jpg"}
   {:id          "ed3666e0-5662-11e9-a902-dac8687b984b"
    :title       "Hot Cocoa"
    :description "Cocoa powder, sugar, and streamed milk, for us non-coffee drinkers."
    :price       750
    :photo_url   "/img/products/cocoa.jpg"}])

(def ingredient-usage
  {"e19fa2b0-5662-11e9-a902-dac8687b984b" {:coffee_beans 100
                                           :milk         50}
   "e7746720-5662-11e9-a902-dac8687b984b" {:coffee_beans 120}
   "ed3666e0-5662-11e9-a902-dac8687b984b" {:cocoa_powder 150
                                           :milk         100}})
