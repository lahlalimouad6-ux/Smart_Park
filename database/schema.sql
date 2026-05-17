-- Création de la base de données
CREATE DATABASE IF NOT EXISTS smart_parking CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_parking;

-- Table users
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role ENUM('CONDUCTEUR', 'ADMIN') DEFAULT 'CONDUCTEUR',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Table parkings
CREATE TABLE IF NOT EXISTS parkings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    adresse VARCHAR(255) NOT NULL,
    ville VARCHAR(100) NOT NULL,
    coord_gps VARCHAR(100), -- Format: "lat,lng"
    tarif_heure DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Table zones
CREATE TABLE IF NOT EXISTS zones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parking_id BIGINT NOT NULL,
    nom_zone VARCHAR(100) NOT NULL,
    FOREIGN KEY (parking_id) REFERENCES parkings(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Table parking_spots
CREATE TABLE IF NOT EXISTS parking_spots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_id BIGINT NOT NULL,
    numero_place VARCHAR(20) NOT NULL,
    type ENUM('STANDARD', 'HANDICAPE', 'ELECTRIQUE') DEFAULT 'STANDARD',
    coordx INT NOT NULL, -- Position sur le plan (pixels ou %)
    coordy INT NOT NULL, -- Position sur le plan (pixels ou %)
    statut ENUM('LIBRE', 'OCCUPE', 'RESERVE') DEFAULT 'LIBRE',
    FOREIGN KEY (zone_id) REFERENCES zones(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Table parking_cameras (caméras IoT simulées via vidéos du dataset)
CREATE TABLE IF NOT EXISTS parking_cameras (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parking_id BIGINT NOT NULL UNIQUE,
    video_file VARCHAR(255) NOT NULL,
    FOREIGN KEY (parking_id) REFERENCES parkings(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Table parking_spot_regions (zones de détection sur la vidéo, coordonnées normalisées 0..1)
CREATE TABLE IF NOT EXISTS parking_spot_regions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    spot_id BIGINT NOT NULL UNIQUE,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    w DOUBLE NOT NULL,
    h DOUBLE NOT NULL,
    FOREIGN KEY (spot_id) REFERENCES parking_spots(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Table reservations
CREATE TABLE IF NOT EXISTS reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    spot_id BIGINT NOT NULL,
    date_debut DATETIME NOT NULL,
    date_fin DATETIME NOT NULL,
    montant_total DECIMAL(10, 2) NOT NULL,
    hourly_rate_applied DECIMAL(10, 2),
    statut ENUM('EN_ATTENTE', 'PAYE', 'ANNULE', 'TERMINE') DEFAULT 'EN_ATTENTE',
    qr_code_token VARCHAR(255) UNIQUE,
    pricing_breakdown TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (spot_id) REFERENCES parking_spots(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Table subscriptions
CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type ENUM('MENSUEL', 'ANNUEL') NOT NULL,
    date_expiration DATE NOT NULL,
    actif BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Table parking_pricing_rules (tarification dynamique par parking)
CREATE TABLE IF NOT EXISTS parking_pricing_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parking_id BIGINT NOT NULL,
    name VARCHAR(140) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    rule_type VARCHAR(30) NOT NULL,
    priority INT DEFAULT 100,
    multiplier DECIMAL(10, 4),
    override_rate DECIMAL(10, 2),
    days_of_week VARCHAR(60),
    start_time TIME,
    end_time TIME,
    start_datetime DATETIME,
    end_datetime DATETIME,
    min_occupancy_pct DOUBLE,
    max_occupancy_pct DOUBLE,
    FOREIGN KEY (parking_id) REFERENCES parkings(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Insertion des données de test
-- Passwords: password123 (Hash BCrypt)
INSERT INTO users (nom, prenom, email, password, role) VALUES 
('Admin', 'SmartPark', 'admin@smartpark.com', '$2a$10$eovz7ZDFCmcH4uYvPZ.u9uX3B3mN4.m.o5hZz/v.W7l8Yq8h.e6XG', 'ADMIN'),
('Dupont', 'Jean', 'jean.dupont@email.com', '$2a$10$eovz7ZDFCmcH4uYvPZ.u9uX3B3mN4.m.o5hZz/v.W7l8Yq8h.e6XG', 'CONDUCTEUR');

-- Parkings
INSERT INTO parkings (nom, adresse, ville, coord_gps, tarif_heure) VALUES 
('Parking Indigo Paris Louvre', '1 Rue de l''Amiral de Coligny', 'Paris', '48.8606,2.3376', 4.50),
('Parking Lyon Part-Dieu', '38 Boulevard Marius Vivier Merle', 'Lyon', '45.7606,4.8592', 3.20);

-- Zones pour le premier parking
INSERT INTO zones (parking_id, nom_zone) VALUES 
(1, 'Niveau -1'),
(1, 'Niveau -2');

-- Places pour le Niveau -1
INSERT INTO parking_spots (zone_id, numero_place, type, coordx, coordy, statut) VALUES 
(1, 'A1', 'STANDARD', 10, 10, 'LIBRE'),
(1, 'A2', 'STANDARD', 20, 10, 'OCCUPE'),
(1, 'A3', 'HANDICAPE', 30, 10, 'LIBRE'),
(1, 'A4', 'ELECTRIQUE', 40, 10, 'LIBRE'),
(1, 'A5', 'STANDARD', 50, 10, 'LIBRE'),
(1, 'A6', 'STANDARD', 60, 10, 'RESERVE');

-- Règles de tarification dynamique (exemples)
INSERT INTO parking_pricing_rules (parking_id, name, enabled, rule_type, priority, multiplier, days_of_week, start_time, end_time) VALUES
(1, 'Heures de pointe (soir)', TRUE, 'TIME_WINDOW', 50, 1.2500, 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', '18:00:00', '21:00:00'),
(2, 'Heures de pointe (soir)', TRUE, 'TIME_WINDOW', 50, 1.2500, 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', '18:00:00', '21:00:00');

INSERT INTO parking_pricing_rules (parking_id, name, enabled, rule_type, priority, multiplier, start_time, end_time) VALUES
(1, 'Nuit (promo)', TRUE, 'TIME_WINDOW', 80, 0.8000, '22:00:00', '06:00:00'),
(2, 'Nuit (promo)', TRUE, 'TIME_WINDOW', 80, 0.8000, '22:00:00', '06:00:00');

INSERT INTO parking_pricing_rules (parking_id, name, enabled, rule_type, priority, multiplier, min_occupancy_pct) VALUES
(1, 'Forte demande (>=80%)', TRUE, 'OCCUPANCY', 90, 1.2000, 80.0),
(2, 'Forte demande (>=80%)', TRUE, 'OCCUPANCY', 90, 1.2000, 80.0);
