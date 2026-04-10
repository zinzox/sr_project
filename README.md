# Sarbi Rohek

Plateforme web de mise en relation entre clients et prestataires, avec des espaces dedies par role (client, prestataire, admin), authentification et messagerie.

## Fonctionnalites

- Inscription et connexion (client / prestataire)
- Gestion de session (auth status, logout)
- Espace client
- Espace prestataire (profil, services)
- Espace admin (gestion des comptes)
- Messagerie entre utilisateurs
- Prestataires mis en avant

## Stack Technique

- Frontend: HTML, CSS, JavaScript
- Backend: Java Servlets (Jakarta Servlet)
- Serveur applicatif: Apache Tomcat 10
- Base de donnees: MySQL
- Acces DB: JDBC (mysql-connector-j)

## Structure du Projet

```text
sarbi_rohek/
  admin.html, admin.css, admin.js
  client-space.html, client-space.css, client-space.js
  provider-space.html, provider-space.css, provider-space.js
  login.html, login.css, login.js
  register.html, register.css, register.js
  index.html, styles.css, index.js, index-landing.js

  RegisterVerificationServlet.java
  LoginVerificationServlet.java
  AuthStatusServlet.java
  LogoutServlet.java
  CurrentUserProfileServlet.java
  ProviderProfileServlet.java
  FeaturedProvidersServlet.java
  AllProvidersServlet.java
  AdminAccountsServlet.java
  MessagingServlet.java
  ProviderRepository.java

  WEB-INF/
    web.xml
  lib/
  support-presentation.html
```

## Prerequis

- Java JDK 17+ (ou version compatible Tomcat 10)
- Apache Tomcat 10
- MySQL Server 8+
- Driver MySQL (`mysql-connector-j`) disponible dans `WEB-INF/lib/` (ou `lib/` selon ton montage)

## Base de Donnees

La base utilisee est `sarbi_rohek` sur `localhost:3306`.

Dans la classe `ProviderRepository`, la connexion JDBC est configuree avec une URL de type:

```text
jdbc:mysql://localhost:3306/sarbi_rohek?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

Au demarrage, l'application initialise automatiquement le schema si les tables n'existent pas:

- `providers` (table prestataires uniquement)
- `clients` (table clients uniquement)
- `client_provider_links` (table fille de liaison client <-> prestataire)
- `messages`
- `message_alerts` (alertes detectees par IA/Ollama)
- `account_sanctions` (blacklist, bans temporaires)

Les tables de moderation (`message_alerts`, `account_sanctions`) sont initialisees automatiquement
par le backend lors du premier acces aux endpoints de moderation/messages/login/register.

## Deploiement sur Tomcat

1. Copier le projet dans le dossier `webapps/` de Tomcat (nom du contexte: `sarbi_rohek`).
2. Verifier que les classes compilees et dependances sont accessibles:
   - classes dans `WEB-INF/classes/`
   - dependances dans `WEB-INF/lib/`
3. Demarrer Tomcat.
4. Ouvrir l'application:

```text
http://localhost:8080/sarbi_rohek/
```

## Endpoints Principaux

- `POST /api/register` - inscription
- `POST /api/login` - connexion
- `GET /api/auth-status` - etat de session
- `POST /api/logout` - deconnexion
- `GET /api/profile/current` - profil utilisateur courant
- `GET /api/providers/featured` - prestataires en avant
- `GET /api/providers/all` - tous les prestataires
- `POST /api/messages` - envoi de message

Nouveaux endpoints MVP (hors securite):

- `GET /api/favorites/list` (via `GET /api/favorites`)
- `POST /api/favorites/add`
- `POST /api/favorites/remove`
- `GET /api/reviews?providerEmail=...`
- `POST /api/reviews`
- `GET /api/quotes/list` (via `GET /api/quotes`)
- `POST /api/quotes/request`
- `POST /api/quotes/respond`
- `GET /api/slots?providerEmail=...`
- `POST /api/slots/create`
- `POST /api/slots/delete`
- `POST /api/slots/book`
- `GET /api/notifications`
- `POST /api/notifications/mark-read`
- `GET /api/dashboard/client`
- `GET /api/dashboard/provider`
- `GET /api/loyalty/balance`
- `GET /api/providers/search`
- `GET /api/providers/recommended`
- `GET /api/commerce/payments`
- `POST /api/commerce/payments/create`
- `POST /api/commerce/payments/confirm`
- `GET /api/commerce/invoices`
- `POST /api/commerce/invoices/create`

## Instructions Produit (Hors Securite)

Un plan detaille des evolutions produit (avis, favoris, devis, statuts, agenda, notifications,
dashboards, multi-langue, fidelite, etc.) est disponible dans:

- `PRODUCT_INSTRUCTIONS.md`

## Notes Securite

- Ne pas publier de mots de passe DB en clair dans un repo public.
- Idealement, deplacer les identifiants DB vers des variables d'environnement ou un fichier de config non versionne.

## Verification Email a l'inscription

L'inscription fonctionne en 2 etapes:

- etape 1: envoi d'un code a 6 chiffres par email
- etape 2: validation du code avant ouverture de l'espace client/prestataire

Variables d'environnement SMTP a definir pour l'envoi:

- `SMTP_HOST` (ex: `smtp.gmail.com`)
- `SMTP_PORT` (par defaut `465`)
- `SMTP_USER`
- `SMTP_PASSWORD`
- `SMTP_FROM`

## Publication GitHub

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<username>/<repo>.git
git push -u origin main
```

## Auteur

Projet realise par Moez.
