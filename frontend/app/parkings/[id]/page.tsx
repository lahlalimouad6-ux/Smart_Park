'use client';

import { useEffect, useState, use } from 'react';
import api from '@/lib/api';
import { Parking, Zone } from '@/types';
import ParkingPlan from '@/components/ParkingPlan';
import { useRouter } from 'next/navigation';
import { Calendar, Clock, CreditCard, ChevronLeft, CheckCircle2, Euro } from 'lucide-react';
import { format, addHours, differenceInMinutes, parseISO } from 'date-fns';
import dynamic from 'next/dynamic';
import { clsx } from 'clsx';

const Map = dynamic(() => import('@/components/Map'), {
  loading: () => <div className="h-[400px] bg-slate-100 animate-pulse rounded-2xl border border-slate-200"></div>,
  ssr: false
});

type CameraPlanSpot = {
  spotId: number;
  numeroPlace: string;
  statut: 'LIBRE' | 'OCCUPE' | 'RESERVE';
  nextReservationStart?: string | null;
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
  const selectedZoneId = selectedZone?.id;

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
        if (selectedZoneId) {
          const updated = loadedZones.find((z) => z.id === selectedZoneId);
          if (updated) setSelectedZone(updated);
        }
      } catch {}
    }, 5_000);
    return () => clearInterval(interval);
  }, [id, selectedZoneId]);

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
    if (blocksBecauseNext) {
      alert('Créneau interdit : cette place est réservée plus tard. Choisis une fin avant la prochaine réservation.');
      return;
    }
    setReserving(true);
    try {
      await api.post('/reservations', {
        spotId: selectedSpotId,
        dateDebut,
        dateFin,
        useSubscription: false
      });
      router.push('/reservations');
    } catch {
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
    nextReservationStart?: string | null;
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

  const selectedSpotNextStart =
    useCameraPlan
      ? cameraSpots.find((s) => s.spotId === selectedSpotId)?.nextReservationStart || null
      : (selectedZone?.spots || []).find((s) => s.id === selectedSpotId)?.nextReservationStart || null;

  const nextStartDate = selectedSpotNextStart ? new Date(selectedSpotNextStart) : null;
  const deb = new Date(dateDebut);
  const fin = new Date(dateFin);
  const hasNextStart =
    nextStartDate !== null && Number.isFinite(nextStartDate.getTime()) && Number.isFinite(deb.getTime()) && Number.isFinite(fin.getTime());
  const blocksBecauseNext =
    hasNextStart && deb < (nextStartDate as Date) && fin > (nextStartDate as Date);
  const nextStartLabel = hasNextStart
    ? `${String((nextStartDate as Date).getHours()).padStart(2, '0')}:${String((nextStartDate as Date).getMinutes()).padStart(2, '0')}`
    : null;

  return (
    <div className="sp-container py-10">
      <button
        onClick={() => router.back()}
        className="sp-btn mb-6"
      >
        <ChevronLeft className="h-5 w-5 mr-1" /> Retour aux parkings
      </button>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 space-y-8">
          <div className="sp-ticket">
            <div className="p-6">
              <h1 className="text-3xl font-extrabold tracking-tight text-slate-900">{parking.nom}</h1>
              <p className="mt-3 flex flex-wrap items-center gap-2 text-slate-600">
                <span className="sp-chip">
                  <Euro className="h-3.5 w-3.5 text-slate-500" />
                  {parking.tarifHeure}€/h
                </span>
                <span>{parking.adresse}, {parking.ville}</span>
              </p>
            </div>
          </div>

          <div className="sp-card p-6">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-extrabold flex items-center text-slate-900">
                <CheckCircle2 className="h-5 w-5 mr-2 text-emerald-600" /> Choisir une place
              </h2>
              {!useCameraPlan && zones.length > 0 && (
                <div className="sp-panel px-2 py-2">
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
                      className={clsx(
                        "px-6 py-2 rounded-full text-sm font-extrabold transition border-2",
                        selectedZone?.id === zone.id
                          ? "bg-[color:var(--card)] text-slate-900 border-[rgba(11,18,32,0.16)] shadow-[0_10px_0_rgba(11,18,32,0.06),0_18px_40px_rgba(11,18,32,0.10)]"
                          : "bg-transparent text-slate-600 border-transparent hover:border-[rgba(11,18,32,0.14)] hover:bg-white/60"
                      )}
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
              <div className="p-8 text-center text-slate-500">
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

          <div className="sp-card p-6">
            <h2 className="text-xl font-extrabold text-slate-900 mb-2">Trajet optimal</h2>
            <p className="text-slate-600 mb-4">
              Choisis ta position sur la carte (ou utilise le GPS) pour obtenir l&apos;itinéraire le plus rapide vers ce parking.
            </p>
            {destination ? (
              <Map mode="route" destination={destination} height={360} />
            ) : (
              <div className="p-6 text-center text-slate-500 bg-white/60 rounded-2xl border border-[rgba(15,23,42,0.10)]">
                Coordonnées GPS du parking manquantes ou invalides.
              </div>
            )}
          </div>
        </div>

        <div className="space-y-6">
          <div className="sp-ticket sticky top-24">
            <div className="p-6">
              <h2 className="text-xl font-extrabold mb-6 flex items-center text-slate-900">
                <Calendar className="h-5 w-5 mr-2 text-blue-600" /> Réservation
              </h2>
              
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-bold text-slate-700 mb-1 flex items-center">
                    <Clock className="h-4 w-4 mr-1 text-slate-400" /> Début
                  </label>
                  <input
                    type="datetime-local"
                    className="w-full px-4 py-2.5 border border-[rgba(15,23,42,0.12)] rounded-2xl bg-white focus:ring-2 focus:ring-blue-500 outline-none"
                    value={dateDebut}
                    onChange={(e) => setDateDebut(e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-sm font-bold text-slate-700 mb-1 flex items-center">
                    <Clock className="h-4 w-4 mr-1 text-slate-400" /> Fin
                  </label>
                  <input
                    type="datetime-local"
                    className="w-full px-4 py-2.5 border border-[rgba(15,23,42,0.12)] rounded-2xl bg-white focus:ring-2 focus:ring-blue-500 outline-none"
                    value={dateFin}
                    onChange={(e) => setDateFin(e.target.value)}
                  />
                </div>
              </div>

              <div className="mt-7 pt-6 border-t border-[rgba(15,23,42,0.10)]">
                <div className="flex justify-between items-center mb-3">
                  <span className="text-slate-600 font-semibold">Place sélectionnée</span>
                  <span className="font-extrabold text-slate-900">{selectedSpotLabel}</span>
                </div>
                <div className="flex justify-between items-center text-lg">
                  <span className="font-bold text-slate-900">Total</span>
                  <span className="font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-cyan-500">
                    {totalAffiche.toFixed(2)} €
                  </span>
                </div>
                {pricingLoading && selectedSpotId && (
                  <div className="mt-2 text-xs text-slate-500 text-right">Calcul du tarif dynamique...</div>
                )}
                {pricingQuote && pricingQuote.appliedRules && pricingQuote.appliedRules.length > 0 && (
                  <div className="mt-3 text-xs text-slate-600">
                    <div className="flex justify-between">
                      <span>Tarif dynamique</span>
                      <span className="font-extrabold text-slate-900">
                        {Number(pricingQuote.minHourlyRate).toFixed(2)}–{Number(pricingQuote.maxHourlyRate).toFixed(2)} €/h
                      </span>
                    </div>
                    <div className="mt-1 text-[11px] text-slate-500">
                      {pricingQuote.appliedRules.map((r) => r.name).join(' • ')}
                    </div>
                  </div>
                )}
                <div className="mt-1 text-xs text-slate-500 text-right">
                  {hours.toFixed(2)} h
                </div>
                
                <button
                  disabled={!selectedSpotId || reserving || blocksBecauseNext}
                  onClick={handleReservation}
                  className="w-full mt-6 sp-btn sp-btn-primary h-12 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <CreditCard className="h-5 w-5" />
                  {reserving ? 'Réservation...' : 'Confirmer la réservation'}
                </button>
                {selectedSpotId && blocksBecauseNext && nextStartLabel && (
                  <div className="mt-3 sp-card p-4 bg-red-50/60 border-red-200">
                    <div className="text-sm font-extrabold text-red-800">
                      Cette place sera réservée à {nextStartLabel}.
                    </div>
                    <div className="text-sm text-red-700 mt-1">
                      Choisis une fin avant {nextStartLabel} ou sélectionne une autre place.
                    </div>
                  </div>
                )}
                <p className="text-[10px] text-center text-slate-500 mt-4 uppercase tracking-wider">
                  Paiement sécurisé
                </p>
              </div>
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
  spots: Array<{ spotId: number; numeroPlace: string; statut: 'LIBRE' | 'OCCUPE' | 'RESERVE'; nextReservationStart?: string | null; x: number; y: number; w: number; h: number }>;
  selectedSpotId: number | null;
  onSelectSpotId: (spotId: number) => void;
}) {
  return (
    <div className="relative w-full bg-white/60 border-2 border-[rgba(11,18,32,0.16)] rounded-2xl p-4 min-h-[560px] overflow-hidden">
      <div className="relative w-full h-[520px] sm:h-[580px] lg:h-[720px] bg-slate-950 rounded-2xl overflow-hidden">
        {spots.map((s) => {
          const selected = selectedSpotId === s.spotId;
          const blocked = s.statut !== 'LIBRE';
          const hasUpcoming =
            !blocked &&
            typeof s.nextReservationStart === 'string' &&
            Number.isFinite(new Date(s.nextReservationStart).getTime()) &&
            new Date(s.nextReservationStart) > new Date();
          const bg =
            selected
              ? 'bg-blue-500/35 border-blue-400'
            : hasUpcoming
              ? 'bg-orange-500/18 border-orange-400'
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
              {hasUpcoming && (
                <span className="absolute -bottom-2 left-2 px-2 py-0.5 rounded bg-black/60 text-white text-[10px] font-extrabold">
                  {String(new Date(s.nextReservationStart as string).getHours()).padStart(2, '0')}:{String(new Date(s.nextReservationStart as string).getMinutes()).padStart(2, '0')}
                </span>
              )}
            </button>
          );
        })}
      </div>

      <div className="mt-4 flex flex-wrap gap-4 border-t border-[rgba(11,18,32,0.16)] pt-4 text-sm text-slate-600">
        <div className="flex items-center"><div className="w-3 h-3 bg-emerald-500/20 border border-emerald-500/30 rounded mr-2"></div> Libre</div>
        <div className="flex items-center"><div className="w-3 h-3 bg-red-500/20 border border-red-500/30 rounded mr-2"></div> Occupé</div>
        <div className="flex items-center"><div className="w-3 h-3 bg-orange-500/20 border border-orange-500/30 rounded mr-2"></div> Réservé</div>
        <div className="flex items-center"><div className="w-3 h-3 bg-blue-500/30 border border-blue-500/40 rounded mr-2"></div> Sélectionné</div>
      </div>
    </div>
  );
}
