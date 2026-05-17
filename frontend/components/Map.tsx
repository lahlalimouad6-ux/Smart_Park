'use client';

import { useEffect, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMapEvents } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import { Parking } from '@/types';
import Link from 'next/link';
import L from 'leaflet';

interface MapProps {
  parkings?: Parking[];
  mode?: 'list' | 'route';
  destination?: { lat: number; lon: number; name?: string };
  height?: number;
}

function ClickToSetStart({ enabled, onPick }: { enabled: boolean; onPick: (lat: number, lon: number) => void }) {
  useMapEvents({
    click: (e) => {
      if (!enabled) return;
      onPick(e.latlng.lat, e.latlng.lng);
    }
  });
  return null;
}

export default function Map({ parkings = [], mode = 'list', destination, height = 400 }: MapProps) {
  const [isMounted, setIsMounted] = useState(false);
  const [start, setStart] = useState<{ lat: number; lon: number } | null>(null);
  const [routeCoords, setRouteCoords] = useState<Array<[number, number]>>([]);
  const [routeMeta, setRouteMeta] = useState<{ distanceMeters: number; durationSeconds: number } | null>(null);
  const [routeError, setRouteError] = useState<string | null>(null);
  const [routeLoading, setRouteLoading] = useState(false);
  const [pickEnabled, setPickEnabled] = useState(false);

  useEffect(() => {
    // Fix for default marker icons in Leaflet with Next.js
    const defaultIconProto = L.Icon.Default.prototype as unknown as { _getIconUrl?: unknown };
    delete defaultIconProto._getIconUrl;
    L.Icon.Default.mergeOptions({
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-icon-2x.png',
      iconUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-icon.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-shadow.png',
    });
    setTimeout(() => setIsMounted(true), 0);
  }, []);

  useEffect(() => {
    if (mode !== 'route') return;
    setTimeout(() => {
      setRouteCoords([]);
      setRouteMeta(null);
      setRouteError(null);
    }, 0);
  }, [mode, destination?.lat, destination?.lon]);

  useEffect(() => {
    const fetchRoute = async () => {
      if (mode !== 'route') return;
      if (!destination) return;
      if (!start) return;

      setRouteLoading(true);
      setRouteError(null);
      try {
        const url =
          `https://router.project-osrm.org/route/v1/driving/` +
          `${start.lon},${start.lat};${destination.lon},${destination.lat}` +
          `?overview=full&geometries=geojson&alternatives=false`;
        const res = await fetch(url);
        if (!res.ok) {
          throw new Error(`OSRM error: ${res.status}`);
        }
        const data = await res.json();
        const route = data?.routes?.[0];
        const coords: Array<[number, number]> = route?.geometry?.coordinates || [];
        const latLng = coords.map(([lon, lat]: [number, number]) => [lat, lon] as [number, number]);
        setRouteCoords(latLng);
        setRouteMeta({
          distanceMeters: Number(route?.distance ?? 0),
          durationSeconds: Number(route?.duration ?? 0)
        });
      } catch {
        setRouteCoords([]);
        setRouteMeta(null);
        setRouteError("Impossible de calculer le trajet pour le moment.");
      } finally {
        setRouteLoading(false);
      }
    };

    void fetchRoute();
  }, [destination, mode, start]);

  const useMyLocation = () => {
    if (!navigator.geolocation) {
      setRouteError("La géolocalisation n'est pas supportée par ce navigateur.");
      return;
    }
    setRouteError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setStart({ lat: pos.coords.latitude, lon: pos.coords.longitude });
        setPickEnabled(false);
      },
      () => {
        setRouteError("Impossible de récupérer ta position. Vérifie les permissions.");
      },
      { enableHighAccuracy: true, timeout: 10_000 }
    );
  };

  if (!isMounted) return <div className="h-[400px] bg-gray-100 animate-pulse rounded-lg"></div>;

  if (mode === 'route' && destination) {
    const center: [number, number] = [destination.lat, destination.lon];
    const durationMin = routeMeta ? Math.round((routeMeta.durationSeconds / 60) * 10) / 10 : null;
    const distanceKm = routeMeta ? Math.round((routeMeta.distanceMeters / 1000) * 100) / 100 : null;

    return (
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => setPickEnabled(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-xl font-bold transition"
          >
            Spécifie ta localisation pour un trajet optimal à notre parking
          </button>
          <button
            onClick={useMyLocation}
            className="bg-white hover:bg-gray-50 text-gray-900 px-4 py-2 rounded-xl font-bold border border-gray-200 transition"
          >
            Utiliser ma position (GPS)
          </button>
          {start && (
            <button
              onClick={() => {
                setStart(null);
                setRouteCoords([]);
                setRouteMeta(null);
                setPickEnabled(false);
              }}
              className="bg-white hover:bg-gray-50 text-gray-700 px-4 py-2 rounded-xl font-bold border border-gray-200 transition"
            >
              Réinitialiser
            </button>
          )}
          {routeLoading && <div className="text-sm text-gray-500 font-medium">Calcul du trajet...</div>}
          {routeError && <div className="text-sm text-red-600 font-medium">{routeError}</div>}
          {durationMin !== null && distanceKm !== null && (
            <div className="text-sm text-gray-700 font-bold">
              {durationMin} min • {distanceKm} km
            </div>
          )}
        </div>

        {pickEnabled && (
          <div className="text-sm text-gray-600">
            Clique sur la carte pour définir ta position de départ.
          </div>
        )}

        <MapContainer
          center={center}
          zoom={13}
          style={{ height: `${height}px`, width: '100%', borderRadius: '0.5rem' }}
        >
          <ClickToSetStart
            enabled={pickEnabled}
            onPick={(lat, lon) => {
              setStart({ lat, lon });
              setPickEnabled(false);
            }}
          />
          <TileLayer
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          />
          <Marker position={[destination.lat, destination.lon]}>
            <Popup>
              <div className="p-1">
                <h3 className="font-bold">{destination.name || 'Parking'}</h3>
                <p className="text-sm text-gray-600">Destination</p>
              </div>
            </Popup>
          </Marker>
          {start && (
            <Marker position={[start.lat, start.lon]}>
              <Popup>
                <div className="p-1">
                  <h3 className="font-bold">Toi</h3>
                  <p className="text-sm text-gray-600">Point de départ</p>
                </div>
              </Popup>
            </Marker>
          )}
          {routeCoords.length > 0 && <Polyline positions={routeCoords} pathOptions={{ color: '#2563eb', weight: 5 }} />}
        </MapContainer>
      </div>
    );
  }

  return (
    <MapContainer
      center={[48.8566, 2.3522]}
      zoom={13}
      style={{ height: `${height}px`, width: '100%', borderRadius: '0.5rem' }}
    >
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      />
      {parkings.map((parking) => {
        const coords = parking.coordGps.split(',').map(Number);
        if (coords.length !== 2) return null;
        
        return (
          <Marker key={parking.id} position={[coords[0], coords[1]]}>
            <Popup>
              <div className="p-1">
                <h3 className="font-bold">{parking.nom}</h3>
                <p className="text-sm text-gray-600">{parking.adresse}</p>
                <p className="text-blue-600 font-semibold">{parking.tarifHeure}€ / heure</p>
                <Link 
                  href={`/parkings/${parking.id}`}
                  className="mt-2 block w-full text-center bg-blue-600 text-white py-1 px-3 rounded text-sm hover:bg-blue-700 transition"
                >
                  Voir les places
                </Link>
              </div>
            </Popup>
          </Marker>
        );
      })}
    </MapContainer>
  );
}
