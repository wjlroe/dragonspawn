(ns dragonspawn.core
  (:use [domina :only [set-styles!]])
  (:require [goog.dom :as dom]
            [goog.style :as style]
            [goog.Timer]
            [goog.events :as events]
            [goog.events.KeyCodes :as kcs]
            [goog.events.EventType :as event-type]))

(def game-width 2400)
(def game-height 1800)
(def cell-size 120)
(def aspectRatio (float (/ 4 3)))
(def time-limit 10)
(def number-bones-needed 10)
(def win-message "You won and can build a dragon")
(def lose-message "You lost! The villagers will kill you now :(")
(def reset-message "[Press n to start over]")
(def title-message "Dragon Spawn")
(def start-message ["You stole all the cabages! Oh noes!"
                    "Some villagers are coming to find and kill you."
                    (str "You have " time-limit " seconds to find " number-bones-needed)
                    "dragon bones to build a dragon to defend yourself."
                    "[Press any key to begin]"
                    ""
                    "Press '/' or 'p' to pause or play the music"])

(defn abs
  [number]
  (Math/abs number))

(defn cell->coords
  [x y]
  [(* x cell-size)
   (* y cell-size)])

(defn pixels->cell
  [pixels]
  (int (/ pixels cell-size)))

(def game-x-cells (pixels->cell game-width))
(def game-y-cells (pixels->cell game-height))

(def pickup (dom/getElement "pickup"))
(def potion (dom/getElement "potion"))
(def soundtrack (dom/getElement "soundtrack"))

(def surface
  (let [surface (dom/getElement "surface")]
    (set! (.-width surface) game-width)
    (set! (.-height surface) game-height)
    surface))

(def draw-context (.getContext surface "2d"))

(def start-position
  [(pixels->cell (/ game-width 2))
   (- (pixels->cell game-height) 1)])

(defn random-location
  []
  (map (comp int rand) [(inc game-x-cells) game-y-cells]))

(defn random-item-spawns
  [item number]
  (into {}
        (map #(vector % item) (take number (repeatedly random-location)))))

(defn random-spawns
  []
  (merge (random-item-spawns :flask 5)
         (random-item-spawns :bones 30)))

(defn initial-state
  []
  {:game-state :start
   :item-locations (random-spawns)
   :inventory []
   :player start-position})

(def keycode->key
  {kcs/SPACE :space
   kcs/W     :forward
   kcs/UP    :forward
   kcs/A     :left
   kcs/LEFT  :left
   kcs/S     :back
   kcs/DOWN  :back
   kcs/D     :right
   kcs/RIGHT :right
   kcs/N     :new-game
   kcs/SLASH :toggle-music
   kcs/P     :toggle-music})

(def sprites
  {:bones (dom/getElement "bones")
   :flask (dom/getElement "flask")
   :player (dom/getElement "player")
   :grass (dom/getElement "grass")})

(def colour->rgb
  {:blue "#127496"})

(defn log-state
  [{:keys [player]}]
  (let [[player-x player-y] player]
    (.log js/console "player x" player-x "player y" player-y)))

(defn toggle-music
  []
  (if (.-paused soundtrack)
    (.play soundtrack)
    (.pause soundtrack)))

(defn draw-sprite
  [state x y size sprite]
  (let [sprite (sprites sprite)
        [x y] (cell->coords x y)]
    (.drawImage draw-context sprite x y size size)))

(defn draw-text
  ([state x y text] (draw-text state x y text "middle"))
  ([state x y text baseline] (draw-text state x y text baseline "center"))
  ([state x y text baseline align] (draw-text state x y text baseline align "4em"))
  ([state x y text baseline align font-size]
   (let [[x y] (cell->coords x y)]
     (set! (.-font draw-context) (str "bold " font-size " sans-serif"))
     (set! (.-textBaseline draw-context) baseline)
     (set! (.-textAlign draw-context) align)
     (.fillText draw-context text x y))))

(defn elapsed-time
  [start-time]
  (-> (js/Date.)
      (- start-time)
      (/ 1000)
      int))

(defn bone-count
  [inventory]
  (count (filter #(= :bones %) inventory)))

(defn enough-bones?
  [inventory]
  (>= (bone-count inventory) number-bones-needed))

(defn draw-grass
  [state]
  (doseq [x (range game-x-cells) y (range game-y-cells)]
    (draw-sprite state x y cell-size :grass)))

(defmulti render :game-state)

(defmethod render :start
  [state]
  (draw-grass state)
  (let [x-mid (int (/ game-x-cells 2))]
    (draw-text state x-mid 2 title-message "middle" "center" "5em")
    (doseq [[y message] (map vector (range (count start-message)) start-message)]
      (draw-text state x-mid (+ 3 y) message))))

(defmethod render :playing
  [state]
  (let [{:keys [player start-time item-locations inventory]} state
        countdown (- time-limit (elapsed-time start-time))
        [player-x player-y] player]
    (draw-grass state)
    (doseq [[[x y] sprite] item-locations]
      (draw-sprite state x y cell-size sprite))
    (draw-sprite state player-x player-y cell-size :player)
    (draw-text state 0 0 (str (bone-count inventory) " bones") "top" "left")
    (draw-text state game-x-cells 0 (str countdown " seconds") "top" "right")))

(defmethod render :win
  [state]
  (let [x (int (/ game-x-cells 2))]
    (draw-text state x 0 win-message "top" "center")
    (draw-text state x 1 reset-message "top" "center")))

(defmethod render :lose
  [state]
  (let [x (int (/ game-x-cells 2))]
    (draw-text state x 0 lose-message "top" "center")
    (draw-text state x 1 reset-message "top" "center")))

(defn render-world
  [state]
  (render @state)
  (.requestAnimationFrame js/window #(render-world state)))

(defn boundary
  [[x y]]
  [(min (max x 0) (dec game-x-cells))
   (min (max y 0) (dec game-y-cells))])

(defn move-player
  [state direction]
  (swap! state
         (fn [current]
           (let [{:keys [player inventory item-locations]} current
                 [player-x player-y] player
                 new-player (boundary
                              (condp = direction
                                :left [(dec player-x) player-y]
                                :right [(inc player-x) player-y]
                                :forward [player-x (dec player-y)]
                                :back [player-x (inc player-y)]
                                player))
                 item-at-player (get item-locations new-player)
                 sounds (condp = item-at-player
                          :potion (.play potion)
                          :bones (.play pickup)
                          nil)
                 new-inventory (if item-at-player
                                 (conj inventory item-at-player)
                                 inventory)
                 new-item-locations (if item-at-player
                                      (dissoc item-locations new-player)
                                      item-locations)]
             (assoc current
                    :player new-player
                    :inventory new-inventory
                    :item-locations new-item-locations)))))

(defn timeup?
  [start-time]
  (>= (elapsed-time start-time) time-limit))

(defn playing?
  [{:keys [game-state]}]
  (= game-state :playing))

(defn check-game
  [state]
  (when (playing? @state)
    (swap! state
           (fn [current]
             (let [{:keys [game-state start-time inventory]} current]
               (assoc current :game-state
                      (cond
                        (enough-bones? inventory) :win
                        (timeup? start-time) :lose
                        :else game-state)))))))

(defn start-game
  [state]
  (swap! state
         (fn [current]
           (assoc current
                  :game-state :playing
                  :start-time (js/Date.)))))

(defn reset-game
  [state keyevent]
  (when (= keyevent :new-game)
    (reset! state (initial-state))))

(defn handle-keyevent
  [state keyevent]
  (condp = keyevent
    :toggle-music (toggle-music)
    (let [{:keys [game-state]} @state]
      (condp = game-state
        :playing (move-player state keyevent)
        :start (start-game state)
        (reset-game state keyevent))))
  (log-state @state))

(defn translate-keyboard-event
  [e]
  (-> e
      .getBrowserEvent
      .-keyCode
      keycode->key))

(defn prevent-default?
  [keyevent]
  (.log js/console "prevent-default?" keyevent)
  (contains? #{:space :forward :back :left :right} keyevent))

(defn keydown
  [state e]
  (when (prevent-default? (translate-keyboard-event e))
    (.preventDefault e))
  (->> e
       translate-keyboard-event
       (handle-keyevent state)))

(defn px
  [size]
  (str (int size) "px"))

(defn resize
  [& args]
  (let [winWidth (.-innerWidth js/window)
        winHeight (.-innerHeight js/window)
        currentRatio (float (/ winWidth winHeight))
        [width height] (if (> currentRatio aspectRatio)
                         [(* winHeight aspectRatio) winHeight]
                         [winWidth (/ winWidth aspectRatio)])
        top (/ (- height) 2)
        left (/ (- width) 2)
        css {:width (px width)
             :height (px height)
             :marginTop (px top)
             :marginLeft (px left)}]
    (set-styles! surface
                 css)))

(defn setup-events
  [state]
  (let [timer (goog.Timer. 200)]
    (events/listen timer goog.Timer/TICK (partial check-game state))
    (events/listen js/window event-type/RESIZE resize)
    (events/listen js/window event-type/KEYDOWN (partial keydown state))
    (.start timer)))

(defn main
  []
  (let [state (atom (initial-state))]
    (setup-events state)
    (resize)
    (render-world state)))

(main)
