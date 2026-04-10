# Instructions Produit (Hors Securite)

Ce document regroupe toutes les options proposees hors securite, avec des objectifs, des criteres d'acceptation et un plan technique adapte a ce projet (Servlets Java + MySQL + HTML/CSS/JS).

## 1) Avis et Notes
### Objectif
Permettre aux clients de noter les prestataires apres une interaction terminee.

### Criteres d'acceptation
- Un client peut donner une note de 1 a 5 et un commentaire.
- Un prestataire affiche sa note moyenne et le nombre d'avis.
- Un client ne peut pas noter plusieurs fois la meme mission.

### Plan technique
- Table `provider_reviews`:
  - `id`, `provider_email`, `client_email`, `rating`, `comment_text`, `created_at`.
- Endpoint `POST /api/reviews` pour creer un avis.
- Endpoint `GET /api/reviews?providerEmail=...` pour lister les avis.
- Ajouter dans l'espace client un formulaire d'avis.
- Ajouter dans l'espace prestataire un bloc "Note moyenne".

---

## 2) Favoris
### Objectif
Permettre aux clients de sauvegarder des prestataires favoris.

### Criteres d'acceptation
- Bouton "Ajouter aux favoris" sur chaque carte prestataire.
- Vue "Mes favoris" cote client.
- Pas de doublon favori pour le meme couple client/prestataire.

### Plan technique
- Table `client_favorites`:
  - `id`, `client_email`, `provider_email`, `created_at`, contrainte unique `(client_email, provider_email)`.
- Endpoints:
  - `POST /api/favorites/add`
  - `POST /api/favorites/remove`
  - `GET /api/favorites/list`
- Front client-space: onglet "Favoris".

---

## 3) Agenda et Disponibilites
### Objectif
Permettre aux prestataires de publier des creneaux disponibles.

### Criteres d'acceptation
- Un prestataire peut creer/supprimer ses creneaux.
- Le client voit les creneaux disponibles.
- Un creneau reserve ne doit plus etre disponible.

### Plan technique
- Table `provider_slots`:
  - `id`, `provider_email`, `start_at`, `end_at`, `status` (`AVAILABLE`, `BOOKED`).
- Endpoints:
  - `POST /api/slots/create`
  - `POST /api/slots/delete`
  - `GET /api/slots/list`
- Affichage calendrier simple dans `provider-space` et `client-space`.

---

## 4) Demande de Devis
### Objectif
Permettre au client d'envoyer un besoin chiffre a un prestataire.

### Criteres d'acceptation
- Le client envoie budget, delai, description.
- Le prestataire accepte/rejette/propose un prix.
- Historique visible des deux cotes.

### Plan technique
- Table `quote_requests`:
  - `id`, `client_email`, `provider_email`, `description`, `budget`, `deadline_at`, `status`, `provider_response`, `created_at`, `updated_at`.
- Endpoints:
  - `POST /api/quotes/request`
  - `POST /api/quotes/respond`
  - `GET /api/quotes/list`

---

## 5) Statut des Demandes
### Objectif
Suivre le cycle d'une demande de service.

### Criteres d'acceptation
- Statuts: `PENDING`, `ACCEPTED`, `IN_PROGRESS`, `DONE`, `CANCELED`.
- Timeline visible cote client et prestataire.

### Plan technique
- Soit reutiliser `quote_requests.status`, soit table `service_orders` dediee.
- Endpoint `POST /api/orders/update-status`.
- Journal simple de transitions avec horodatage.

---

## 6) Paiement Integre
### Objectif
Permettre un paiement en ligne et garder un historique.

### Criteres d'acceptation
- Initier paiement pour une demande acceptee.
- Etat de paiement visible (en attente, confirme, echoue).

### Plan technique
- Table `payments`:
  - `id`, `order_id`, `client_email`, `provider_email`, `amount`, `currency`, `status`, `provider_ref`, `created_at`.
- Endpoint serveur pour initier et confirmer paiement.
- Choisir passerelle (Stripe/PayPal) plus tard.

---

## 7) Factures Automatiques
### Objectif
Generer une facture apres validation de service.

### Criteres d'acceptation
- Une facture est creee pour chaque commande payee.
- Telechargement PDF disponible.

### Plan technique
- Table `invoices`:
  - `id`, `order_id`, `invoice_number`, `pdf_url`, `issued_at`.
- Generation PDF serveur (bibliotheque Java PDF).
- Endpoint `GET /api/invoices/download?id=...`.

---

## 8) Recherche Avancee
### Objectif
Aider le client a trouver rapidement le bon prestataire.

### Criteres d'acceptation
- Filtrage par categorie, note, prix, disponibilite.
- Recherche texte rapide.

### Plan technique
- Ajouter colonnes/metadonnees utiles au profil prestataire.
- Endpoint `GET /api/providers/search?...` avec filtres.
- Index SQL sur `service_type`, `main_activity`, `city`, `price_range`.

---

## 9) Recommandations Personnalisees
### Objectif
Proposer des prestataires pertinents selon l'historique client.

### Criteres d'acceptation
- Bloc "Recommande pour vous" cote client.
- Baseline simple (meme categorie + bonnes notes).

### Plan technique
- Job serveur simple qui calcule une liste par client.
- Table `client_recommendations` cachee avec score.
- Endpoint `GET /api/providers/recommended`.

---

## 10) Pieces Jointes dans le Chat
### Objectif
Envoyer des images/documents dans les conversations.

### Criteres d'acceptation
- Upload d'un fichier depuis client et prestataire.
- Affichage du fichier dans le thread.
- Limite taille et types autorises.

### Plan technique
- Table `message_attachments`:
  - `id`, `message_id`, `file_name`, `mime_type`, `file_url`, `size_bytes`.
- Dossier stockage dedie (hors source).
- Endpoint multipart `POST /api/messages/send-with-file`.

---

## 11) Notifications
### Objectif
Notifier les utilisateurs des evenements importants.

### Criteres d'acceptation
- Notification pour nouveau message, nouveau devis, statut change.
- Badge de non-lus dans l'UI.

### Plan technique
- Table `notifications`:
  - `id`, `user_email`, `type`, `payload_json`, `is_read`, `created_at`.
- Endpoints:
  - `GET /api/notifications`
  - `POST /api/notifications/mark-read`

---

## 12) Dashboard Prestataire
### Objectif
Donner des indicateurs metier au prestataire.

### Criteres d'acceptation
- KPIs: demandes recues, conversion, temps de reponse.
- Graphique simple par semaine.

### Plan technique
- Endpoint `GET /api/provider/dashboard`.
- Agregations SQL sur demandes/messages/paiements.

---

## 13) Dashboard Client
### Objectif
Donner de la visibilite au client sur ses activites.

### Criteres d'acceptation
- KPIs: demandes actives, depenses, favoris.
- Historique recent.

### Plan technique
- Endpoint `GET /api/client/dashboard`.
- UI dans `client-space` avec cartes statistiques.

---

## 14) Multi-langue (FR/AR/EN)
### Objectif
Rendre la plateforme accessible en plusieurs langues.

### Criteres d'acceptation
- Selecteur de langue visible.
- Textes principaux traduits sur login/register/client/provider/admin.

### Plan technique
- Fichiers `i18n` JSON par langue.
- Fonction JS `t(key)` pour afficher les labels.
- Stockage preference dans `localStorage`.

---

## 15) Programme Fidelite
### Objectif
Fideliser les clients avec points et niveaux.

### Criteres d'acceptation
- Gagner des points apres achat.
- Afficher solde et niveau (Bronze/Silver/Gold).

### Plan technique
- Table `loyalty_points`:
  - `id`, `client_email`, `delta_points`, `reason`, `created_at`.
- Endpoint `GET /api/loyalty/balance`.
- Regle de calcul points configurable.

---

## Ordre de priorite recommande
1. Favoris
2. Avis et notes
3. Demande de devis + statuts
4. Notifications
5. Recherche avancee
6. Dashboard client/prestataire
7. Agenda
8. Pieces jointes
9. Paiement + facture
10. Recommandations
11. Multi-langue
12. Fidelite

---

## Convention d'implementation
- Backend: servlet dediee par domaine fonctionnel.
- Repository: methodes SQL dans `ProviderRepository` ou repositories dedies.
- Reponses API: JSON UTF-8 coherent (`message`, `data`, `errors`).
- Front: reutiliser les patterns existants de `client-space.js`, `provider-space.js`, `admin.js`.
- DB: migration via `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE ... ADD COLUMN` defensifs.
