(ns metabase.api.user
  (:require [compojure.core :refer [defroutes GET PUT]]
            [medley.core :refer [mapply]]
            [metabase.api.common :refer :all]
            [metabase.db :refer [sel upd exists?]]
            (metabase.models [hydrate :refer [hydrate]]
                             [user :refer [User]])
            [metabase.util :refer [select-non-nil-keys]]))

;; RE stripping fields and calculating fields:
;; -  Instead of stripping fields it makes more sense to just not select them in the first place.
;;    The default fields selected for any entity can be set by implementing `metabase.db/default-fields`.
;;    In fact, `User` is already doing this inside `metabase.models.user`.
;; -  Calculating fields can be done with `assoc` in `post-select`. You can give it an actual value, which
;;    means it will always be returned, or set it to a function or delay, which means it will only be returned
;;    if specified in a call to `hydrate`.

(defendpoint GET "/" []
  ;; TODO - permissions check
  (sel :many User :is_active true))

(defendpoint GET "/current" []
  (->404 @*current-user*
         (hydrate [:org_perms :organization])))

;; TODO - permissions check
(defendpoint GET "/:id" [id]
  (sel :one User :id id))

(defendpoint PUT "/:id" [id :as {:keys [body]}]
  (check-403 (= id *current-user-id*))                                     ; you can only update yourself (or can admins update other users?)
  (check-500 (->> (select-non-nil-keys body :email :first_name :last_name)
                  (mapply upd User id)))                                   ; `upd` returns `false` if no updates occured. So in that case return a 500
  (sel :one User :id id))                                                  ; return the updated user

;; TODO: do we want a permissions check here?
(defendpoint PUT "/:id/password" [id :as {:keys [body]}]
  (let [{:keys [password old_password]} body]
    (check (and password old_password) [400 "You must specify both old_password and password"])
    (check-404 (exists? User :id id))
    ;; TODO - match old password against current one
    ;; TODO - password encryption
    (upd User id :password password)
    (sel :one User :id id)))

(define-routes)