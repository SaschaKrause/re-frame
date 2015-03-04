(ns re-frame.undo
  (:require-macros [reagent.ratom  :refer [reaction]])
  (:require
    [reagent.core      :as     reagent]
    [re-frame.db       :refer  [app-db]]
    [re-frame.handlers :as     handlers]
    [re-frame.subs     :as     subs ]))


;; -- History -------------------------------------------------------------------------------------
;;
;;
(def ^:private max-undos (atom 50))     ;; maximum number of undo states maintained
(defn set-max-undos
  [n]
  (reset! max-undos n))

;;
(def ^:private undo-list (reagent/atom []))   ;; a list of history states
(def ^:private redo-list (reagent/atom []))   ;; a list of future states, caused by undoing


(defn clear-history!
  []
  (reset! undo-list [])
  (reset! redo-list []))


(defn store-now!
  "stores the value currently in app-db, so the user can later undo"
  []
  (reset! redo-list [])           ;; clear and redo state created by previous undos
  (reset! undo-list (vec (take
                           @max-undos
                           (conj @undo-list @app-db)))))

(defn undos?
  []
  (pos? (count @undo-list)))

(defn redos?
  []
  (pos? (count @redo-list)))


;; -- subscriptions  -----------------------------------------------------------------------------

(subs/register
  :undos?
  (fn handler
    ; "return true if anything is stored in the undo list, otherwise false"
    [_ _]
    (reaction (undos?))))

(subs/register
  :redos?
  (fn handler
    ; "return true if anything is stored in the redo list, otherwise false"
    [_ _]
    (reaction (redos?))))


;; -- event handlers  ----------------------------------------------------------------------------

(handlers/register     ;; not pure
  :undo                ;; usage:  (dispatch [:undo])
  (fn handler
    [_ _]
    (when (undos?)
      (reset! redo-list (cons @app-db @redo-list))
      (reset! app-db (last @undo-list))
      (reset! undo-list (pop @undo-list)))))


(handlers/register     ;; not pure
  :redo                ;; usage:  (dispatch [:redo])
  (fn handler
    [_ _]
    (when (redos?)
      (reset! app-db (first @redo-list))
      (reset! redo-list (rest @redo-list))
      (reset! undo-list (conj @undo-list @app-db)))))