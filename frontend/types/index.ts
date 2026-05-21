export interface User {
  id: number;
  email: string;
  roles: string[];
  token: string;
}

export interface Parking {
  id: number;
  nom: string;
  adresse: string;
  ville: string;
  coordGps: string; // "lat,lng"
  tarifHeure: number;
}

export interface Zone {
  id: number;
  nomZone: string;
  spots: ParkingSpot[];
}

export interface ParkingSpot {
  id: number;
  numeroPlace: string;
  type: 'STANDARD' | 'HANDICAPE' | 'ELECTRIQUE';
  coordX: number;
  coordY: number;
  statut: 'LIBRE' | 'OCCUPE' | 'RESERVE';
  nextReservationStart?: string | null;
}

export interface Reservation {
  id: number;
  spot: ParkingSpot;
  dateDebut: string;
  dateFin: string;
  montantTotal: number;
  statut: 'EN_ATTENTE' | 'PAYE' | 'ANNULE' | 'TERMINE';
  qrCodeToken: string;
}
