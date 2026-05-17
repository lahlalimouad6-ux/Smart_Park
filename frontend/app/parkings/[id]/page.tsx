'use client';

import { useEffect, useState, use } from 'react';
import api from '@/lib/api';
import { Parking, Zone } from '@/types';
import ParkingPlan from '@/components/ParkingPlan';
import { useRouter } from 'next/navigation';
import { Calendar, Clock, CreditCard, ChevronLeft, CheckCircle2 } from 'lucide-react';
import { format, addHours, differenceInMinutes, parseISO } from 'date-fns';
import dynamic from 'next/dynamic';
import { clsx } from 'clsx';

const Map = dynamic(() => import('@/components/Map'), {
  loading: () => <div className="h-[400px] bg-gray-100 animate-pulse rounded-lg"></div>,
  ssr: false
});

type CameraPlanSpot = {
  spotId: number;
  numeroPlace: string;
  statut: 'LIBRE' | 'OCCUPE' | 'RESERVE';
  x: number | null;
  y: number | null;
  w: number | null;
  h: number | null;
};

type CameraPlan = {
  parkingId: number;
  parkingNom: string;
  videoFile: string;
  spots: CameraPlanSpot[];
};

type PricingAppliedRule = {
  id: number;
  name: string;
  type: 'DATE_RANGE' | 'TIME_WINDOW' | 'OCCUPANCY';
  priority: number;
  multiplier: string | null;
  overrideRate: string | null;
};

type PricingQuote = {
  parkingId: number;
  spotId: number;
  baseHourlyRate: string;
  totalAmount: string;
  totalMinutes: number;
  minHourlyRate: string;
  maxHourlyRate: string;
  appliedRules: PricingAppliedRule[];
};

export default function ParkingDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const [parking, setParking] = useState<Parking | null>(null);
  const [zones, setZones] = useState<Zone[]>([]);
  const [selectedZone, setSelectedZone] = useState<Zone | null>(null);
  const [selectedSpotId, setSelectedSpotId] = useState<number | null>(null);
  const [cameraPlan, setCameraPlan] = useState<CameraPlan | null>(null);
  const [dateDebut, setDateDebut] = useState(format(new Date(), "yyyy-MM-dd'T'HH:mm"));
  const [dateFin, setDateFin] = useState(format(addHours(new Date(), 2), "yyyy-MM-dd'T'HH:mm"));
  const [loading, setLoading] = useState(true);
  const [reserving, setReserving] = useState(false);
  const [pricingQuote, setPricingQuote] = useState<PricingQuote | null>(null);
  const [pricingLoading, setPricingLoading] = useState(false);
  const router = useRouter();

  const selectSpotId = (spotId: number | null) => {
    setSelectedSpotId(spotId);
    setPricingQuote(null);
  };

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [pRes, zRes, camRes] = await Promise.all([
          api.get(`/parkings/${id}`),
          api.get(`/parkings/${id}/zones`),
          api.get(`/parkings/${id}/camera-plan`).catch(() => ({ data: null }))
        ]);
        setParking(pRes.data);
        const loadedZones: Zone[] = zRes.data || [];
        setZones(loadedZones);
        if (loadedZones.length > 0) {
          const firstZone = loadedZones[0];
          if (!firstZone.spots || firstZone.spots.length === 0) {
            const spotsRes = await api.get(`/parkings/zones/${firstZone.id}/spots`);
            const zoneWithSpots = { ...firstZone, spots: spotsRes.data || [] };
            setSelectedZone(zoneWithSpots);
            setZones((prev) => prev.map((z) => (z.id === zoneWithSpots.id ? zoneWithSpots : z)));
          } else {
            setSelectedZone(firstZone);
          }
        }
        const plan: CameraPlan | null = camRes?.data || null;
        setCameraPlan(plan && Array.isArray(plan.spots) ? plan : null);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [id]);

  useEffect(() => {
    if (!id) return;
    const interval = setInterval(async () => {
      try {
        const zRes = await api.get(`/parkings/${id}/zones`);
        const loadedZones: Zone[] = zRes.data || [];
        setZones(loadedZones);
        if (selectedZone) {
          const updated = loadedZones.find((z) => z.id === selectedZone.id);
          if (updated) setSelectedZone(updated);
        }
      } catch {}
    }, 5_000);
    return () => clearInterval(interval);
  }, [id, selectedZone?.id]);

  useEffect(() => {
    if (!id) return;
    const interval = setInterval(async () => {
      try {
        const res = await api.get(`/parkings/${id}/camera-plan`);
        const plan: CameraPlan | null = res.data || null;
        setCameraPlan(plan && Array.isArray(plan.spots) ? plan : null);
      } catch {}
    }, 2_000);
    return () => clearInterval(interval);
  }, [id]);

  useEffect(() => {
    if (!id || !selectedSpotId) return;
    if (!dateDebut || !dateFin) return;

    let cancelled = false;
    const t = setTimeout(async () => {
      if (cancelled) return;
      setPricingLoading(true);
      try {
        const res = await api.get(`/parkings/${id}/pricing/quote`, {
          params: { spotId: selectedSpotId, dateDebut, dateFin }
        });
        if (!cancelled) setPricingQuote(res.data);
      } catch {
        if (!cancelled) setPricingQuote(null);
      } finally {
        if (!cancelled) setPricingLoading(false);
      }
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [id, selectedSpotId, dateDebut, dateFin]);

  const handleReservation = async () => {
    if (!selectedSpotId) return;
    setReserving(true);
    try {
      await api.post('/reservations', {
        spotId: selectedSpotId,
        dateDebut,
        dateFin,
        useSubscription: false
      });
      router.push('/reservations');
    } catch (err) {
      alert('Erreur lors de la réservation. Veuillez vérifier la disponibilité.');
    } finally {
      setReserving(false);
    }
  };

  if (loading) return <div className="p-8 text-center">Chargement...</div>;
  if (!parking) return <div className="p-8 text-center text-red-500">Parking non trouvé</div>;

  const minutes = Math.max(0, differenceInMinutes(parseISO(dateFin), parseISO(dateDebut)));
  const hours = Math.round((minutes / 60) * 100) / 100;
  const totalEstime = selectedSpotId ? parking.tarifHeure * hours : 0;
  const totalAffiche = pricingQuote ? Number(pricingQuote.totalAmount) : totalEstime;
  const coords = parking.coordGps?.split(',').map((v) => Number(v.trim())) || [];
  const destination =
    coords.length === 2 && Number.isFinite(coords[0]) && Number.isFinite(coords[1])
      ? { lat: coords[0], lon: coords[1], name: parking.nom }
      : null;

  const cameraSpots = (cameraPlan?.spots || []).filter((s) => s.x !== null && s.y !== null && s.w !== null && s.h !== null) as Array<{
    spotId: number;
    numeroPlace: string;
    statut: 'LIBRE' | 'OCCUPE' | 'RESERVE';
    x: number;
    y: number;
    w: number;
    h: number;
  }>;
  const useCameraPlan = cameraSpots.length > 0;
  const selectedSpotLabel =
    useCameraPlan
      ? cameraSpots.find((s) => s.spotId === selectedSpotId)?.numeroPlace || 'Aucune'
      : (selectedZone?.spots || []).find((s) => s.id === selectedSpotId)?.numeroPlace || 'Aucune';

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <button 
        onClick={() => router.back()}
        className="flex items-center text-gray-500 hover:text-gray-700 mb-6 transition"
      >
        <ChevronLeft className="h-5 w-5 mr-1" /> Retour aux parkings
      </button>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 space-y-8">
          <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
            <h1 className="text-3xl font-bold text-gray-900">{parking.nom}</h1>
            <p className="text-gray-600 flex items-center mt-2">
              <span className="bg-blue-50 text-blue-700 px-3 py-1 rounded-full text-sm font-medium mr-3">
                {parking.tarifHeure}€ / heure
              </span>
              {parking.adresse}, {parking.ville}
            </p>
          </div>

          <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-bold flex items-center">
                <CheckCircle2 className="h-5 w-5 mr-2 text-green-500" /> Choisir une place
              </h2>
              {!useCameraPlan && zones.length > 0 && (
                <div className="flex bg-gray-100 p-1 rounded-lg">
                  {zones.map(zone => (
                    <button
                      key={zone.id}
                      onClick={async () => {
                        selectSpotId(null);
                        if (!zone.spots || zone.spots.length === 0) {
                          const spotsRes = await api.get(`/parkings/zones/${zone.id}/spots`);
                          const zoneWithSpots = { ...zone, spots: spotsRes.data || [] };
                          setSelectedZone(zoneWithSpots);
                          setZones((prev) => prev.map((z) => (z.id === zoneWithSpots.id ? zoneWithSpots : z)));
                        } else {
                          setSelectedZone(zone);
                        }
                      }}
                      className={`px-4 py-1.5 rounded-md text-sm font-medium transition ${
                        selectedZone?.id === zone.id ? 'bg-white text-blue-600 shadow-sm' : 'text-gray-500 hover:text-gray-700'
                      }`}
                    >
                      {zone.nomZone}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {useCameraPlan ? (
              <CameraParkingPlan
                spots={cameraSpots}
                selectedSpotId={selectedSpotId}
                onSelectSpotId={selectSpotId}
              />
            ) : zones.length === 0 ? (
              <div className="p-8 text-center text-gray-500">
                Aucune zone / place n&apos;est configurée pour ce parking.
              </div>
            ) : selectedZone ? (
              <ParkingPlan
                spots={selectedZone.spots || []}
                selectedSpotId={selectedSpotId}
                onSelectSpot={(spot) => selectSpotId(spot.id)}
              />
            ) : null}
          </div>

          <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
            <h2 className="text-xl font-bold text-gray-900 mb-2">Trajet optimal</h2>
            <p className="text-gray-600 mb-4">
              Choisis ta position sur la carte (ou utilise le GPS) pour obtenir l&apos;itinéraire le plus rapide vers ce parking.
            </p>
            {destination ? (
              <Map mode="route" destination={destination} height={360} />
            ) : (
              <div className="p-6 text-center text-gray-500 bg-gray-50 rounded-xl">
                Coordonnées GPS du parking manquantes ou invalides.
              </div>
            )}
          </div>
        </div>

        <div className="space-y-6">
          <div className="bg-white p-6 rounded-2xl shadow-lg border border-gray-100 sticky top-8">
            <h2 className="text-xl font-bold mb-6 flex items-center">
              <Calendar className="h-5 w-5 mr-2 text-blue-600" /> Réservation
            </h2>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1 flex items-center">
                  <Clock className="h-4 w-4 mr-1 text-gray-400" /> Début
                </label>
                <input
                  type="datetime-local"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                  value={dateDebut}
                  onChange={(e) => setDateDebut(e.target.value)}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1 flex items-center">
                  <Clock className="h-4 w-4 mr-1 text-gray-400" /> Fin
                </label>
                <input
                  type="datetime-local"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                  value={dateFin}
                  onChange={(e) => setDateFin(e.target.value)}
                />
              </div>
            </div>

            <div className="mt-8 pt-6 border-t border-gray-100">
              <div className="flex justify-between items-center mb-4">
                <span className="text-gray-600">Place sélectionnée</span>
                <span className="font-bold text-gray-900">{selectedSpotLabel}</span>
              </div>
              <div className="flex justify-between items-center text-lg">
                <span className="font-medium text-gray-900">Total estimé</span>
                <span className="font-bold text-blue-600">
                  {totalAffiche.toFixed(2)} €
                </span>
              </div>
              {pricingLoading && selectedSpotId && (
                <div className="mt-2 text-xs text-gray-400 text-right">Calcul du tarif dynamique...</div>
              )}
              {pricingQuote && pricingQuote.appliedRules && pricingQuote.appliedRules.length > 0 && (
                <div className="mt-3 text-xs text-gray-600">
                  <div className="flex justify-between">
                    <span>Tarif dynamique</span>
                    <span className="font-bold text-gray-900">
                      {Number(pricingQuote.minHourlyRate).toFixed(2)}–{Number(pricingQuote.maxHourlyRate).toFixed(2)} €/h
                    </span>
                  </div>
                  <div className="mt-1 text-[11px] text-gray-500">
                    {pricingQuote.appliedRules.map((r) => r.name).join(' • ')}
                  </div>
                </div>
              )}
              <div className="mt-1 text-xs text-gray-500 text-right">
                {hours.toFixed(2)} h
              </div>
              
              <button
                disabled={!selectedSpotId || reserving}
                onClick={handleReservation}
                className="w-full mt-6 bg-blue-600 text-white py-4 rounded-xl font-bold flex items-center justify-center hover:bg-blue-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <CreditCard className="h-5 w-5 mr-2" />
                {reserving ? 'Réservation...' : 'Confirmer la réservation'}
              </button>
              <p className="text-[10px] text-center text-gray-400 mt-4 uppercase tracking-wider">
                Paiement sécurisé par cryptage SSL
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function CameraParkingPlan({
  spots,
  selectedSpotId,
  onSelectSpotId
}: {
  spots: Array<{ spotId: number; numeroPlace: string; statut: 'LIBRE' | 'OCCUPE' | 'RESERVE'; x: number; y: number; w: number; h: number }>;
  selectedSpotId: number | null;
  onSelectSpotId: (spotId: number) => void;
}) {
  return (
    <div className="relative w-full bg-gray-50 border-2 border-gray-200 rounded-xl p-4 min-h-[420px] shadow-inner overflow-hidden">
      <div className="relative w-full aspect-video bg-black rounded-lg overflow-hidden">
        {spots.map((s) => {
          const selected = selectedSpotId === s.spotId;
          const blocked = s.statut !== 'LIBRE';
          const bg =
            selected
              ? 'bg-blue-500/35 border-blue-400'
              : s.statut === 'OCCUPE'
                ? 'bg-red-500/25 border-red-400'
                : s.statut === 'RESERVE'
                  ? 'bg-orange-500/25 border-orange-400'
                  : 'bg-green-500/25 border-green-400';
          return (
            <button
              key={s.spotId}
              disabled={blocked}
              onClick={() => onSelectSpotId(s.spotId)}
              className={clsx(
                'absolute border-2 rounded-lg transition',
                bg,
                blocked ? 'cursor-not-allowed' : 'cursor-pointer hover:brightness-110'
              )}
              style={{
                left: `${s.x * 100}%`,
                top: `${s.y * 100}%`,
                width: `${s.w * 100}%`,
                height: `${s.h * 100}%`
              }}
              title={`Place ${s.numeroPlace}`}
            >
              <span className="absolute -top-2 left-2 px-2 py-0.5 rounded bg-black/60 text-white text-[10px] font-bold">
                {s.numeroPlace}
              </span>
            </button>
          );
        })}
      </div>

      <div className="mt-4 flex flex-wrap gap-4 border-t pt-4 text-sm text-gray-600">
        <div className="flex items-center"><div className="w-3 h-3 bg-green-100 border border-green-200 rounded mr-2"></div> Libre</div>
        <div className="flex items-center"><div className="w-3 h-3 bg-red-100 border border-red-200 rounded mr-2"></div> Occupé</div>
        <div className="flex items-center"><div className="w-3 h-3 bg-orange-100 border border-orange-200 rounded mr-2"></div> Réservé</div>
        <div className="flex items-center"><div className="w-3 h-3 bg-blue-500 border border-blue-700 rounded mr-2"></div> Sélectionné</div>
      </div>
    </div>
  );
}
