(ns progress-bar-v17
  (:require
   [reagent.core :as r]
   [roam.datascript.reactive :as dr]
   [roam.block :as block]
   [blueprintjs.core :as bp-core]
   [clojure.string :as str]))

;; Circle configuration
(def base-size 11)
(def radius (* 0.75 base-size))
(def viewbox-size (* 2 base-size))
(def center-point base-size)

(defn truthy? [value]
  (or (= value true) (= value "true")))

(defn calculate-coordinates [percentage]
  (let [angle (* (/ percentage 100) (* 2 js/Math.PI))
        x (+ center-point (* radius (js/Math.sin angle)))
        y (- center-point (* radius (js/Math.cos angle)))]
    [x y]))

(defn get-arc-path [percentage]
  (let [[x y] (calculate-coordinates percentage)
        large-arc (if (>= percentage 50) 1 0)
        start-y (- center-point radius)]
    (if (>= percentage 100)
      (str "M " center-point "," start-y " A " radius "," radius " 0 1,1 "
           center-point "," (+ start-y (* 2 radius)) " A " radius "," radius " 0 1,1 "
           center-point "," start-y)
      (str "M " center-point "," start-y " A " radius "," radius " 0 " large-arc ",1 "
           x "," y " L " center-point "," center-point " Z"))))

(defn is-block-ref?
  "Returns true if block-string is a pure block reference ((uid))."
  [block-string]
  (when block-string
    (boolean (re-matches #"^\s*\(\([a-zA-Z0-9_-]+\)\)\s*$" block-string))))

(defn count-status-from-ref [ref]
  (let [title (:node/title ref)]
    (cond
      (= title "TODO") [1 0]
      (= title "DONE") [0 1]
      :else (reduce (fn [[todo done] nested-ref]
                      (let [nested-title (:node/title nested-ref)]
                        (cond
                          (= nested-title "TODO") [(inc todo) done]
                          (= nested-title "DONE") [todo (inc done)]
                          :else [todo done])))
                    [0 0]
                    (:block/refs ref)))))

(defn count-status-from-block [block]
  (reduce (fn [[todo done] ref]
            (let [[todo+ done+] (count-status-from-ref ref)]
              [(+ todo todo+) (+ done done+)]))
          [0 0]
          (:block/refs block)))

(defn progress-counts [block-uid exclude-blockrefs?]
  (let [root @(dr/pull '[:block/string
                         {:block/refs [:node/title {:block/refs [:node/title]}]}
                         {:block/children ...}]
                       [:block/uid block-uid])]
    (loop [stack (if root [root] [])
           todo 0
           done 0]
      (if (empty? stack)
        {:todo todo :done done}
        (let [node (peek stack)
              next-stack (into (pop stack) (:block/children node))
              include-block? (or (not exclude-blockrefs?)
                                 (not (is-block-ref? (:block/string node))))
              [todo+ done+] (if include-block?
                              (count-status-from-block node)
                              [0 0])]
          (recur next-stack (+ todo todo+) (+ done done+)))))))

(def bp-button (r/adapt-react-class bp-core/Button))
(def bp-popover (r/adapt-react-class bp-core/Popover))
(def bp-select (r/adapt-react-class bp-core/HTMLSelect))
(def bp-input (r/adapt-react-class bp-core/InputGroup))
(def bp-switch (r/adapt-react-class bp-core/Switch))

(defn get-component-code-uid [block-string]
  (when block-string
    (let [pattern #"\{\{(?:\[\[)?roam/render(?:\]\])?: *\(\(([^)]+)\)\).*\}\}"
          matches (re-find pattern block-string)]
      (second matches))))

;; Render-string positional args: style, status-text, show-percent, exclude-blockrefs
(defn format-render-string [current-string style status-text show-percent exclude-blockrefs]
  (if-let [code-block-uid (get-component-code-uid current-string)]
    (let [pattern #"\{\{(?:\[\[)?roam/render(?:\]\])?: *\(\([^)]+\)\).*?\}\}"
          replacement (str "{{roam/render: ((" code-block-uid ")) \""
                           style "\" \""
                           status-text "\" \""
                           show-percent "\" \""
                           exclude-blockrefs "\"}}")]
      (str/replace current-string pattern replacement))
    current-string))

(defn update-block-string [block-uid style status-text show-percent exclude-blockrefs]
  (let [current-block @(dr/pull '[:block/string] [:block/uid block-uid])
        current-string (:block/string current-block)
        new-string (format-render-string current-string style status-text show-percent exclude-blockrefs)]
    (when (not= current-string new-string)
      (block/update
       {:block {:uid block-uid
                :string new-string}}))))

(defn settings-menu [block-uid current-style current-status-text current-show-percent current-exclude-blockrefs on-close]
  (let [*style (r/atom current-style)
        *status-text (r/atom current-status-text)
        *show-percent (r/atom current-show-percent)
        *exclude-blockrefs (r/atom current-exclude-blockrefs)]
    (fn []
      [:div.bp3-card.dont-focus-block {:style {:padding "10px" :min-width "250px"}
                                       :on-click (fn [e] (.stopPropagation e))}
       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Display Style"]
        [bp-select {:value @*style
                    :class "dont-focus-block"
                    :onChange #(reset! *style (.. % -target -value))
                    :options [{:value "horizontal" :label "Horizontal Bar"}
                              {:value "radial" :label "Radial/Circle"}]}]]

       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Status Text"]
        [bp-input {:value @*status-text
                   :class "dont-focus-block"
                   :onChange #(reset! *status-text (.. % -target -value))
                   :placeholder "Done"}]]

       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Show Percentage"]
        [bp-switch {:checked (truthy? @*show-percent)
                    :class "dont-focus-block"
                    :onChange #(reset! *show-percent (str (.. % -target -checked)))}]]

       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Exclude Reference-Only Blocks"]
        [bp-switch {:checked (truthy? @*exclude-blockrefs)
                    :class "dont-focus-block"
                    :onChange #(reset! *exclude-blockrefs (str (.. % -target -checked)))}]]

       [:div.button-group.dont-focus-block {:style {:display "flex" :justify-content "flex-end" :gap "8px"}}
        [bp-button {:onClick on-close
                    :class "dont-focus-block"
                    :minimal true}
         "Cancel"]
        [bp-button {:intent "primary"
                    :class "dont-focus-block"
                    :onClick (fn [e]
                               (.stopPropagation e)
                               (.preventDefault e)
                               (update-block-string block-uid @*style @*status-text @*show-percent @*exclude-blockrefs)
                               (on-close))}
         "Apply"]]])))

(defn horizontal-progress-bar [done total status-text show-percent on-settings-click]
  (r/with-let [*hovered? (r/atom false)]
    [:span {:style {:display "inline-flex"
                    :align-items "center"
                    :gap "8px"
                    :vertical-align "middle"}
            :on-mouse-enter #(reset! *hovered? true)
            :on-mouse-leave #(reset! *hovered? false)}
     [:span {:style {:display "inline-block"
                     :width "150px"}}
      [:progress {:id "file"
                  :name "percent-done"
                  :value done
                  :max total
                  :style {:width "100%"}}]]
     [:span {:style {:white-space "nowrap"
                     :display "inline-flex"
                     :align-items "center"}}
      [:span (str done "/" total " " status-text
                  (when (truthy? show-percent)
                    (str " - " (if (zero? total) 0 (int (* (/ done total) 100))) "%")))]
      [:span {:style {:max-width (if @*hovered? "30px" "0px")
                      :overflow "hidden"
                      :margin-left (if @*hovered? "8px" "0")
                      :transition "all 0.3s ease-in-out"
                      :opacity (if @*hovered? "1" "0")
                      :display "inline-block"}}
       [bp-button
        {:icon "cog"
         :class "dont-focus-block"
         :minimal true
         :small true
         :onClick (fn [e]
                    (.stopPropagation e)
                    (on-settings-click))}]]]]))

(defn circle-progress-bar [done total status-text show-percent on-settings-click]
  (r/with-let [*hovered? (r/atom false)]
    (let [percentage (if (zero? total)
                       0
                       (* (/ done total) 100))]
      [:span.inline-flex.items-center.gap-2
       {:style {:vertical-align "middle"}
        :on-mouse-enter #(reset! *hovered? true)
        :on-mouse-leave #(reset! *hovered? false)}
       [:span.relative.inline-flex.items-center.justify-center.progress-circle
        [:svg
         {:width viewbox-size
          :height viewbox-size
          :viewBox (str "0 0 " viewbox-size " " viewbox-size)}
         [:circle
          {:cx center-point
           :cy center-point
           :r radius
           :fill "var(--circle-bg, #eee)"
           :stroke "var(--circle-bg, #eee)"
           :stroke-width "5px"}]
         [:path
          {:d (get-arc-path percentage)
           :fill "var(--circle-fill, #0d8050)"
           :stroke "none"}]]]
       [:span {:style {:display "inline-flex"
                       :align-items "center"}}
        [:span.text-base
         (str done "/" total " " status-text
              (when (truthy? show-percent)
                (str " - " (int percentage) "%")))]
        [:span {:style {:max-width (if @*hovered? "30px" "0px")
                        :overflow "hidden"
                        :margin-left (if @*hovered? "8px" "0")
                        :transition "all 0.3s ease-in-out"
                        :opacity (if @*hovered? "1" "0")
                        :display "inline-block"}}
         [bp-button
          {:icon "cog"
           :class "dont-focus-block"
           :minimal true
           :small true
           :onClick (fn [e]
                      (.stopPropagation e)
                      (on-settings-click))}]]]])))

(defn extension-running? []
  (try
    (boolean (.-running js/window.todoProgressBarExtensionData))
    (catch :default _e
      false)))

(defn main [{:keys [block-uid]} & args]
  (r/with-let [*settings-open? (r/atom false)
               *ext-ready? (r/atom (extension-running?))
               check-interval (when-not (extension-running?)
                                (js/setInterval
                                 (fn []
                                   (when (extension-running?)
                                     (reset! *ext-ready? true)))
                                 500))
               _ (js/setTimeout
                  (fn []
                    (when check-interval
                      (js/clearInterval check-interval)))
                  10000)]
    (if-not @*ext-ready?
      [:div [:strong {:style {:color "red"}}
             "Extension not installed. Please install Todo Progress Bar from Roam Depot."]]
      (let [style (or (first args) "horizontal")
            status-text (or (second args) "Done")
            show-percent (or (nth args 2 nil) "false")
            exclude-blockrefs (or (nth args 3 nil) "false")
            tasks (progress-counts block-uid (truthy? exclude-blockrefs))
            total (+ (:todo tasks) (:done tasks))]
        [:span.dont-focus-block {:on-click (fn [e] (.stopPropagation e))}
         [bp-popover
          {:isOpen @*settings-open?
           :onClose #(reset! *settings-open? false)
           :class "dont-focus-block"
           :position "auto"
           :content (r/as-element [settings-menu
                                   block-uid
                                   style
                                   status-text
                                   show-percent
                                   exclude-blockrefs
                                   #(reset! *settings-open? false)])}
          (if (= style "radial")
            [circle-progress-bar (:done tasks) total status-text show-percent #(reset! *settings-open? true)]
            [horizontal-progress-bar (:done tasks) total status-text show-percent #(reset! *settings-open? true)])]]))
    (finally
      (when check-interval
        (js/clearInterval check-interval))))))
