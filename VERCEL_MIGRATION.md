# Migration Sarbi Rohek vers Vercel (Next.js)

## 🚀 Structure créée

```
pages/
  └── api/
      ├── auth/
      │   ├── login.js          (Remplace LoginVerificationServlet)
      │   └── register.js       (Remplace RegisterVerificationServlet)
      └── admin/
lib/
  ├── db/
  │   └── mysql.js             (Pool MySQL)
  └── utils/
      ├── auth.js              (JWT, authentification)
      └── validation.js        (Validation email, téléphone, etc.)
```

## ✅ Étapes à faire

### 1. **Configurer la base de données MySQL**

Votre application utilise MySQL. Vous devez avoir :
- ✅ MySQL accessible en ligne (ex: AWS RDS, PlanetScale, Digital Ocean)
- ✅ Tables créées (accounts, moderation_blacklist, moderation_bans, etc.)

**Créer les tables essentielles:**

```sql
CREATE TABLE accounts (
  id INT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255) UNIQUE NOT NULL,
  firstName VARCHAR(100),
  lastName VARCHAR(100),
  phone VARCHAR(20),
  cin VARCHAR(20),
  role VARCHAR(50),
  passwordHash VARCHAR(255),
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE moderation_blacklist (
  id INT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255),
  phone VARCHAR(20),
  type VARCHAR(50),
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE moderation_bans (
  id INT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255),
  bannedUntil DATETIME,
  reason TEXT
);
```

### 2. **Créer un compte Vercel**

- Allez sur https://vercel.com
- Connectez votre GitHub
- Importez ce repository

### 3. **Configurer les variables d'environnement**

Dans Vercel Dashboard → Settings → Environment Variables, ajoutez:

```
MYSQL_HOST=your_mysql_host
MYSQL_USER=your_username
MYSQL_PASSWORD=your_password
MYSQL_DATABASE=sarbi_rohek
MYSQL_PORT=3306
JWT_SECRET=your_secret_key_min_32_chars
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your_email@gmail.com
SMTP_PASSWORD=your_app_password
ADMIN_EMAIL=roheksarbi@gmail.com
```

### 4. **Installer les dépendances localement**

```bash
npm install
```

### 5. **Tester localement**

```bash
# Créer .env.local avec vos credentials
cp .env.example .env.local

# Démarrer le serveur de développement
npm run dev
```

L'application sera disponible sur `http://localhost:3000`

### 6. **Pousser vers GitHub**

```bash
git add .
git commit -m "Migration vers Next.js pour Vercel"
git push origin main
```

Vercel se déploiera automatiquement!

## 📝 Prochaines étapes

- [ ] Créer API route pour les providers (`/api/provider-profile`)
- [ ] Créer API route pour les commandes (`/api/orders`)
- [ ] Créer API route pour la modération (`/api/admin/moderation`)
- [ ] Convertir les pages HTML en pages Next.js React
- [ ] Ajouter la gestion des uploads de fichiers
- [ ] Configurer Stripe/Paypal pour les paiements

## 🔗 Ressources

- [Next.js Docs](https://nextjs.org/docs)
- [Vercel Deployment](https://vercel.com/docs)
- [MySQL2 for Node.js](https://github.com/sidorares/node-mysql2)

## 💡 Notes importantes

1. **Base de données**: Assurez-vous que MySQL est accessible depuis Vercel
2. **JWT Secret**: Changez la clé secrète en production
3. **Email**: À implémenter avec nodemailer
4. **Cache**: Utilisez Redis pour stocker les codes de vérification en production
5. **CORS**: À configurer si le frontend est sur un domaine différent
