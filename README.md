# SmartPark - Application de Smart Parking

Solution complète pour la gestion et la réservation de places de parking en temps réel.

## 🚀 Stack Technique
- **Backend** : Java Spring Boot 3, Spring Security, JWT, JPA/Hibernate.
- **Frontend** : Next.js 14 (App Router), TypeScript, Tailwind CSS, Leaflet.
- **Base de données** : MySQL.

## 🛠️ Installation et Lancement

### 1. Base de données (MySQL)
1. Ouvrez PHPMyAdmin ou votre client MySQL préféré.
2. Créez une base de données nommée `smart_parking`.
3. Importez le fichier `database/schema.sql`.

### 2. Backend (Spring Boot)
1. Allez dans le dossier `backend` :
   ```bash
   cd backend
   ```
2. Assurez-vous d'avoir Maven installé.
3. Configurez vos identifiants MySQL dans `src/main/resources/application.properties` (par défaut: root / sans mot de passe).
4. Lancez l'application :
   ```bash
   mvn spring-boot:run
   ```
   Le serveur démarrera sur `http://localhost:8080`.

### 3. Frontend (Next.js)
1. Allez dans le dossier `frontend` :
   ```bash
   cd frontend
   ```
2. Installez les dépendances :
   ```bash
   npm install
   ```
3. Lancez le serveur de développement :
   ```bash
   npm run dev
   ```
   L'application sera accessible sur `http://localhost:3000`.

## 🔑 Identifiants de Test
L'importation du script SQL crée automatiquement les comptes suivants :
- **Administrateur** : `admin@smartpark.com` / `password123`
- **Conducteur** : `jean.dupont@email.com` / `password123`

Vous pouvez également créer de nouveaux comptes via la page d'inscription.

## ✨ Fonctionnalités Clés
- **Carte Interactive** : Localisez les parkings sur une carte.
- **Visual Choice** : Plan interactif du parking pour choisir sa place exacte.
- **Réservation intelligente** : Vérification de disponibilité et calcul du prix.
- **Accès QR Code** : Génération de jetons sécurisés pour l'entrée/sortie.
- **Responsive Design** : Utilisable sur mobile et ordinateur.
