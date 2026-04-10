# Chatbot Ollama — Guide d'utilisation

## 📋 Vue d'ensemble

Un chatbot intelligent avec **Ollama** a été intégré à votre plateforme Sarbi Rohek. Il apparaît comme une petite icône flottante à droite de l'écran et aide les clients avec leurs questions et problèmes.

## 🎯 Fonctionnalités

- **Widget flottant** : Icône discrète en bas-à-droite de chaque page
- **Interface de chat** : Fenêtre élégante et réactive
- **IA francophone** : Réponses en français grâce à Ollama
- **Disponible partout** : Sur toutes les pages client (accueil, login, inscriptions, espace client, etc.)
- **Responsive** : Compatible mobile et desktop
- **Indicateur de chargement** : Animation de typing pendant le traitement

## 🚀 Configuration et déploiement

### 1. Installer Ollama

Téléchargez et installez Ollama : https://www.ollama.ai

### 2. Télécharger un modèle de langue

```bash
ollama pull llama3.1:8b-instruct
```

Autres modèles disponibles :
- `llama3.1:8b-instruct` (recommandé, ~5GB)
- `mistral:7b` (~4GB)
- `neural-chat:7b` (~4GB)

### 3. Démarrer le serveur Ollama

```bash
ollama serve
```

Par défaut, Ollama écoute sur `http://localhost:11434`

### 4. Définir les variables d'environnement (optionnel)

```bash
# Windows PowerShell
[Environment]::SetEnvironmentVariable("OLLAMA_URL", "http://localhost:11434", "User")
[Environment]::SetEnvironmentVariable("OLLAMA_MODEL", "llama3.1:8b-instruct", "User")
```

```bash
# Linux/Mac
export OLLAMA_URL="http://localhost:11434"
export OLLAMA_MODEL="llama3.1:8b-instruct"
```

### 5. Recompiler et déployer

```bash
cd c:\tomcat10\webapps\sarbi_rohek

# Compiler le ChatbotServlet
javac --release 17 ChatbotServlet.java

# Les fichiers doivent être présents :
# - ServerName\WEB-INF\classes\ChatbotServlet.class
# - ServerName\chatbot-widget.js
# - Mises à jour du web.xml ✓
```

### 6. Redémarrer Tomcat

```bash
cd C:\tomcat10\bin
shutdown.bat
startup.bat
```

## 📁 Fichiers modifiés/créés

| Fichier | Rôle |
|---------|------|
| `ChatbotServlet.java` | Backend Java qui communique avec Ollama |
| `chatbot-widget.js` | Widget flottant et interface de chat |
| `WEB-INF/web.xml` | Enregistrement du servlet |
| Pages HTML | Intégration du script chatbot |

### Pages avec le chatbot intégré :
- index.html
- login.html
- register.html
- client-space.html
- provider-space.html
- dashboard.html
- messaging.html

## 🎨 Personnalisation

### Modifier le message de bienvenue

Éditez `chatbot-widget.js` ligne ~150 :

```javascript
if (this.messages.length === 0) {
  this.addBotMessage('Votre message ici...');
}
```

### Changer les couleurs

Modifiez le fichier `chatbot-widget.js` sections CSS :

```javascript
background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
```

### Augmenter le timeout

Dans `ChatbotServlet.java`, modifiez :

```java
.timeout(Duration.ofSeconds(30))  // Augmentez ce nombre
```

## 🔍 Dépannage

### Le chatbot n'apparaît pas
- Vérifiez que `chatbot-widget.js` est chargé (F12 → Console)
- Assurez-vous que le script est bien ajouté aux pages HTML
- Ouvrez la console du navigateur pour les erreurs

### Pas de réponse du serveur
```
Error: POST /api/chatbot 404
```
→ Assurez-vous que `ChatbotServlet.java` a été compilé et la classe est dans `WEB-INF/classes/`

### Ollama ne répond pas
```
Error: Ollama statusCode: 500
```
→ Vérifiez que Ollama est en cours d'exécution : `http://localhost:11434/api/tags`

### Les réponses sont lentes
- Augmentez le RAM alloué au modèle Ollama
- Utilisez un modèle plus petit (mistral:7b)
- Augmentez le timeout dans ChatbotServlet

## 📊 Architecture

```
Utilisateur
    ↓ (clique sur l'icône)
chatbot-widget.js (interface)
    ↓ (fetch /api/chatbot)
ChatbotServlet.java
    ↓ (POST /api/generate)
Ollama (HTTP)
    ↓ (réponse JSON)
ChatbotServlet (traite réponse)
    ↓ (JSON)
chatbot-widget.js (affiche)
    ↓
Utilisateur voit réponse
```

## 🔒 Sécurité

- Le chatbot fonctionne côté client avec une simple URL publique
- Les messages ne sont pas sauvegardés (pas de base de données)
- Assurez-vous que Ollama est sécurisé derrière un pare-feu ou un VPN
- Vous pouvez ajouter une authentification dans `ChatbotServlet.doPost()`

## 📈 Optimisations futures

1. **Historique persistant** : Sauvegarder les conversations en base de données
2. **Contexte utilisateur** : Ajouter le nom et l'ID du client au prompt Ollama
3. **Analytics** : Tracker les questions fréquentes
4. **Fine-tuning** : Entraîner Ollama sur vos propres données métier
5. **Cache** : Mettre en cache les réponses similaires
6. **Multi-langue** : Ajouter le support d'autres langues

## 👥 Support

Pour toute question ou problème, consultez :
- Documentation Ollama : https://github.com/jmorganca/ollama
- Fichiers du projet : `ChatbotServlet.java`, `chatbot-widget.js`
