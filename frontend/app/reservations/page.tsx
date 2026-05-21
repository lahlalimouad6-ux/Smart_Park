'use client';

import { useEffect, useState } from 'react';
import api from '@/lib/api';
import { Reservation } from '@/types';
import { QRCodeSVG } from 'qrcode.react';
import { Calendar, MapPin, Clock, Tag, ChevronRight, QrCode, XCircle } from 'lucide-react';
import { format, isAfter } from 'date-fns';
import { fr } from 'date-fns/locale';
import { clsx } from 'clsx';

export default function ReservationsPage() {
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedRes, setSelectedRes] = useState<Reservation | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const resolvedBackendOrigin = (() => {
    const env = process.env.NEXT_PUBLIC_BACKEND_ORIGIN;
    if (typeof env === 'string' && env.trim().length > 0) return env.trim().replace(/\/$/, '');
    if (typeof window !== 'undefined' && window.location?.hostname) {
      const host = window.location.hostname;
      if (host && host !== 'localhost' && host !== '127.0.0.1') return `http://${host}:8080`;
    }
    return 'http://localhost:8080';
  })();
  const needsPublicOriginConfig = typeof window !== 'undefined'
    && (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')
    && !(typeof process.env.NEXT_PUBLIC_BACKEND_ORIGIN === 'string' && process.env.NEXT_PUBLIC_BACKEND_ORIGIN.trim().length > 0);
  const qrUrl = selectedRes ? `${resolvedBackendOrigin}/api/reservations/qr-image/${selectedRes.qrCodeToken}` : '';

  useEffect(() => {
    api.get('/reservations/my')
      .then(res => {
        setReservations(res.data);
        if (res.data.length > 0) setSelectedRes(res.data[0]);
      })
      .catch(err => console.error(err))
      .finally(() => setLoading(false));
  }, []);

  const refreshReservations = async () => {
    const res = await api.get('/reservations/my');
    setReservations(res.data);
    if (selectedRes) {
      const updated = res.data.find((r: Reservation) => r.id === selectedRes.id) || null;
      setSelectedRes(updated);
    } else if (res.data.length > 0) {
      setSelectedRes(res.data[0]);
    }
  };

  const canCancel = (res: Reservation) => {
    if (res.statut === 'ANNULE' || res.statut === 'TERMINE') return false;
    return isAfter(new Date(res.dateDebut), new Date());
  };

  const handleCancel = async () => {
    if (!selectedRes) return;
    if (!canCancel(selectedRes)) return;
    if (!confirm('Annuler cette réservation ?')) return;
    setCancelling(true);
    try {
      await api.post(`/reservations/${selectedRes.id}/cancel`);
      await refreshReservations();
    } catch {
      alert("Impossible d'annuler cette réservation.");
    } finally {
      setCancelling(false);
    }
  };

  if (loading) return <div className="p-8 text-center">Chargement...</div>;

  return (
    <div className="sp-container py-10">
      <div className="flex items-end justify-between gap-6 mb-8">
        <div>
          <div className="sp-chip">Mes réservations</div>
          <h1 className="mt-4 text-3xl sm:text-4xl font-extrabold tracking-tight text-slate-900">Accès & QR Codes</h1>
          <p className="mt-2 text-slate-600">Sélectionne une réservation pour afficher son QR et ses détails.</p>
        </div>
      </div>

      {reservations.length === 0 ? (
        <div className="sp-card p-12 text-center">
          <Calendar className="h-16 w-16 text-slate-200 mx-auto mb-4" />
          <h2 className="text-xl font-extrabold text-slate-900">Aucune réservation pour le moment</h2>
          <p className="text-slate-600 mt-2">Vos futures places de parking apparaîtront ici.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div className="space-y-4 max-h-[600px] overflow-y-auto pr-2">
            {reservations.map(res => (
              <div 
                key={res.id}
                onClick={() => setSelectedRes(res)}
                className={clsx(
                  "sp-card p-5 cursor-pointer flex items-center justify-between group",
                  selectedRes?.id === res.id ? "ring-2 ring-blue-500" : "hover:shadow-lg transition-shadow"
                )}
              >
                <div className="flex items-center space-x-4">
                  <div className={clsx(
                    "p-3 rounded-2xl",
                    res.statut === 'PAYE' ? "bg-emerald-50 text-emerald-700" : "bg-orange-50 text-orange-700"
                  )}>
                    <QrCode className="h-6 w-6" />
                  </div>
                  <div>
                    <h3 className="font-extrabold text-slate-900">Place {res.spot.numeroPlace}</h3>
                    <p className="text-sm text-slate-600 flex items-center">
                      <Clock className="h-3 w-3 mr-1 text-slate-400" />
                      {format(new Date(res.dateDebut), "d MMMM 'à' HH:mm", { locale: fr })}
                    </p>
                  </div>
                </div>
                <ChevronRight className={clsx(
                  "h-5 w-5 transition",
                  selectedRes?.id === res.id ? "text-blue-600 translate-x-1" : "text-slate-300 group-hover:text-slate-400"
                )} />
              </div>
            ))}
          </div>

          <div className="lg:sticky lg:top-8">
            {selectedRes && (
              <div className="sp-ticket">
                <div className="p-6">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div className="text-sm font-extrabold text-slate-900 tracking-tight">SmartPark Pass</div>
                      <div className="mt-1 text-sm text-slate-600">Place {selectedRes.spot.numeroPlace}</div>
                    </div>
                    <div className={clsx(
                      "sp-chip",
                      selectedRes.statut === 'PAYE' ? "border-emerald-200 bg-emerald-50 text-emerald-700" : "border-orange-200 bg-orange-50 text-orange-700"
                    )}>
                      {selectedRes.statut}
                    </div>
                  </div>

                  <div className="mt-6 sp-divider" />

                  <div className="mt-6 flex justify-center bg-white/60 p-6 rounded-2xl border border-dashed border-[rgba(15,23,42,0.14)]">
                    <QRCodeSVG value={qrUrl} size={200} />
                  </div>
                  <div className="text-center -mt-4 mb-8">
                    <a
                      href={qrUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-sm font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-cyan-500 underline"
                    >
                      Ouvrir le lien du QR
                    </a>
                    {needsPublicOriginConfig && (
                      <div className="mt-3 text-xs text-orange-700 bg-orange-50 border border-orange-200 rounded-2xl px-3 py-2 inline-block">
                       
                      </div>
                    )}
                  </div>

                  <div className="space-y-6">
                    <div className="flex items-start">
                      <MapPin className="h-5 w-5 text-slate-400 mt-0.5 mr-3" />
                      <div>
                        <p className="text-xs text-slate-500 uppercase font-bold tracking-wider">Emplacement</p>
                        <p className="font-extrabold text-slate-900">Place {selectedRes.spot.numeroPlace}</p>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                      <div className="flex items-start">
                        <Calendar className="h-5 w-5 text-slate-400 mt-0.5 mr-3" />
                        <div>
                          <p className="text-xs text-slate-500 uppercase font-bold tracking-wider">Arrivée</p>
                          <p className="font-extrabold text-slate-900">{format(new Date(selectedRes.dateDebut), "HH:mm")}</p>
                          <p className="text-xs text-slate-600">{format(new Date(selectedRes.dateDebut), "dd/MM/yyyy")}</p>
                        </div>
                      </div>
                      <div className="flex items-start">
                        <Clock className="h-5 w-5 text-slate-400 mt-0.5 mr-3" />
                        <div>
                          <p className="text-xs text-slate-500 uppercase font-bold tracking-wider">Départ</p>
                          <p className="font-extrabold text-slate-900">{format(new Date(selectedRes.dateFin), "HH:mm")}</p>
                          <p className="text-xs text-slate-600">{format(new Date(selectedRes.dateFin), "dd/MM/yyyy")}</p>
                        </div>
                      </div>
                    </div>

                    <div className="pt-6 border-t border-[rgba(15,23,42,0.10)] flex items-center justify-between">
                      <div className="flex items-center">
                        <Tag className="h-5 w-5 text-slate-400 mr-3" />
                        <span className="text-sm text-slate-600">Statut</span>
                      </div>
                      <span className={clsx(
                        "sp-chip",
                        selectedRes.statut === 'PAYE' ? "border-emerald-200 bg-emerald-50 text-emerald-700" : "border-orange-200 bg-orange-50 text-orange-700"
                      )}>
                        {selectedRes.statut}
                      </span>
                    </div>
                    
                    {canCancel(selectedRes) && (
                      <button
                        disabled={cancelling}
                        onClick={handleCancel}
                        className="w-full mt-2 sp-btn sp-btn-danger h-12 disabled:opacity-50"
                      >
                        <XCircle className="h-5 w-5 mr-2" />
                        {cancelling ? 'Annulation...' : 'Annuler la réservation'}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
