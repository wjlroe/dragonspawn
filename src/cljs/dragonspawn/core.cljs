(ns dragonspawn.core
  (:use [domina :only [set-styles!]])
  (:require [goog.dom :as dom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.events.KeyCodes :as kcs]
            [goog.events.EventType :as event-type]
            ))

(def game-width 800)
(def game-height 600)
(def cell-size 32)
(def aspectRatio (float (/ 4 3)))

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

(defn surface-element
  []
  (dom/getElement "surface"))

(def keycode->key
  {kcs/SPACE :space
   kcs/W :forward
   kcs/A :left
   kcs/S :back
   kcs/D :right})

(def sprites
  {:bones (dom/getElement "bones")
   :flask (dom/getElement "flask")
   :player (dom/getElement "player")
   :grass (dom/getElement "grass")})

(def colour->rgb
  {:blue "#127496"})

(defn log-state
  [{:keys [surface-width surface-height surface player]}]
  (let [[player-x player-y] player]
    (.log js/console "surface width:" surface-width
          "surface height:" surface-height)
    (.log js/console "surface:" surface)
    (.log js/console "player x" player-x "player y" player-y)))

(defn draw-square
  [{:keys [draw-context]} x y size colour]
  )

(defn draw-sprite
  [{:keys [draw-context]} x y size sprite]
  (let [sprite (sprites sprite)
        [x y] (cell->coords x y)]
    (.drawImage draw-context sprite x y size size)))

(defn render-world
  [state]
  (let [{:keys [player]} @state
        [player-x player-y] player]
    (doseq [x (range game-x-cells) y (range game-y-cells)]
      (draw-sprite @state x y cell-size :grass))
    (draw-sprite @state 1 2 cell-size :bones)
    (draw-sprite @state 4 4 cell-size :flask)
    (draw-sprite @state player-x player-y cell-size :player))
  (.requestAnimationFrame js/window #(render-world state)))

(defn boundary
  [[x y]]
  [(min (max x 0) (dec game-x-cells))
   (min (max y 0) (dec game-y-cells))])

(defn move-player
  [state direction]
  (swap! state
         (fn [current]
           (let [{:keys [player]} current
                 [player-x player-y] player]
             (assoc current
               :player (boundary
                         (condp = direction
                           :left [(dec player-x) player-y]
                           :right [(inc player-x) player-y]
                           :forward [player-x (dec player-y)]
                           :back [player-x (inc player-y)]
                           player)))))))

(defn handle-keyevent
  [state keyevent]
  (.log js/console "you pressed:" keyevent)
  (log-state @state)
  (move-player state keyevent))

(defn translate-keyboard-event
  [e]
  (-> e
      .getBrowserEvent
      .-keyCode
      keycode->key))

(defn keydown
  [state e]
  (when (= :space (translate-keyboard-event e))
    (.preventDefault e))
  (->> e
       translate-keyboard-event
       (handle-keyevent state)))

(defn px
  [size]
  (str (int size) ".px"))

(defn resize
  [state e]
  (let [{:keys [draw-context surface]} @state
        winWidth (.-innerWidth js/window)
        winHeight (.-innerHeight js/window)
        currentRatio (float (/ winWidth winHeight))
        [width height] (if (> currentRatio aspectRatio)
                         [(* winHeight aspectRatio) winHeight]
                         [winWidth (/ winWidth aspectRatio)])
        top (/ (- height) 2)
        left (/ (- width) 2)]
    (set-styles! surface
                 {:width (px width)
                  :height (px height)
                  :marginTop (px top)
                  :marginLeft (px left)})
    ))

(defn ^:export main
  []
  (let [surface (surface-element)
        draw-context (.getContext surface "2d")
        state (atom {:surface surface
                     :draw-context draw-context
                     :surface-width (.-width surface)
                     :surface-height (.-height surface)
                     :player [(pixels->cell (/ game-width 2))
                              (- (pixels->cell game-height) 1)]})]
    (set! (.-width surface) game-width)
    (set! (.-height surface) game-height)
    (resize state)
    (events/listen js/window event-type/RESIZE (partial resize state))
    (events/listen js/window event-type/KEYDOWN (partial keydown state))
    (log-state @state)
    (render-world state)))

(main)
