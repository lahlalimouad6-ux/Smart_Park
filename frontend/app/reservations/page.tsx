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
    } catch (err) {
      alert("Impossible d'annuler cette réservation.");
    } finally {
      setCancelling(false);
    }
  };

  if (loading) return <div className="p-8 text-center">Chargement...</div>;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <h1 className="text-3xl font-bold text-gray-900 mb-8">Mes Réservations</h1>

      {reservations.length === 0 ? (
        <div className="bg-white p-12 rounded-2xl shadow-sm border border-gray-100 text-center">
          <Calendar className="h-16 w-16 text-gray-200 mx-auto mb-4" />
          <h2 className="text-xl font-medium text-gray-900">Aucune réservation pour le moment</h2>
          <p className="text-gray-500 mt-2">Vos futures places de parking apparaîtront ici.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div className="space-y-4 max-h-[600px] overflow-y-auto pr-2">
            {reservations.map(res => (
              <div 
                key={res.id}
                onClick={() => setSelectedRes(res)}
                className={clsx(
                  "bg-white p-5 rounded-xl border-2 transition cursor-pointer flex items-center justify-between group",
                  selectedRes?.id === res.id ? "border-blue-500 shadow-md" : "border-gray-100 hover:border-gray-200 shadow-sm"
                )}
              >
                <div className="flex items-center space-x-4">
                  <div className={clsx(
                    "p-3 rounded-lg",
                    res.statut === 'PAYE' ? "bg-green-50 text-green-600" : "bg-orange-50 text-orange-600"
                  )}>
                    <QrCode className="h-6 w-6" />
                  </div>
                  <div>
                    <h3 className="font-bold text-gray-900">Place {res.spot.numeroPlace}</h3>
                    <p className="text-sm text-gray-500 flex items-center">
                      <Clock className="h-3 w-3 mr-1" />
                      {format(new Date(res.dateDebut), "d MMMM 'à' HH:mm", { locale: fr })}
                    </p>
                  </div>
                </div>
                <ChevronRight className={clsx(
                  "h-5 w-5 transition",
                  selectedRes?.id === res.id ? "text-blue-500 translate-x-1" : "text-gray-300 group-hover:text-gray-400"
                )} />
              </div>
            ))}
          </div>

          <div className="lg:sticky lg:top-8">
            {selectedRes && (
              <div className="bg-white rounded-2xl shadow-xl border border-gray-100 overflow-hidden">
                <div className="bg-blue-600 p-6 text-white text-center">
                  <h2 className="text-xl font-bold">Détails de l&apos;Accès</h2>
                  <p className="text-blue-100 text-sm mt-1">Présentez ce QR Code à l&apos;entrée</p>
                </div>
                
                <div className="p-8">
                  <div className="flex justify-center mb-8 bg-gray-50 p-6 rounded-2xl border-2 border-dashed border-gray-200">
                    <QRCodeSVG value={qrUrl} size={200} />
                  </div>
                  <div className="text-center -mt-4 mb-8">
                    <a
                      href={qrUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-sm font-bold text-blue-600 hover:text-blue-700 underline"
                    >
                      Ouvrir le lien du QR
                    </a>
                    {needsPublicOriginConfig && (
                      <div className="mt-3 text-xs text-orange-700 bg-orange-50 border border-orange-200 rounded-xl px-3 py-2 inline-block">
                        Pour scanner depuis un téléphone, configure NEXT_PUBLIC_BACKEND_ORIGIN avec l’IP du PC (ex: http://192.168.x.x:8080), puis redémarre le frontend.
                      </div>
                    )}
                  </div>

                  <div className="space-y-6">
                    <div className="flex items-start">
                      <MapPin className="h-5 w-5 text-gray-400 mt-0.5 mr-3" />
                      <div>
                        <p className="text-xs text-gray-400 uppercase font-bold tracking-wider">Emplacement</p>
                        <p className="font-medium text-gray-900">Place {selectedRes.spot.numeroPlace}</p>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                      <div className="flex items-start">
                        <Calendar className="h-5 w-5 text-gray-400 mt-0.5 mr-3" />
                        <div>
                          <p className="text-xs text-gray-400 uppercase font-bold tracking-wider">Arrivée</p>
                          <p className="font-medium text-gray-900">{format(new Date(selectedRes.dateDebut), "HH:mm")}</p>
                          <p className="text-xs text-gray-500">{format(new Date(selectedRes.dateDebut), "dd/MM/yyyy")}</p>
                        </div>
                      </div>
                      <div className="flex items-start">
                        <Clock className="h-5 w-5 text-gray-400 mt-0.5 mr-3" />
                        <div>
                          <p className="text-xs text-gray-400 uppercase font-bold tracking-wider">Départ</p>
                          <p className="font-medium text-gray-900">{format(new Date(selectedRes.dateFin), "HH:mm")}</p>
                          <p className="text-xs text-gray-500">{format(new Date(selectedRes.dateFin), "dd/MM/yyyy")}</p>
                        </div>
                      </div>
                    </div>

                    <div className="pt-6 border-t border-gray-100 flex items-center justify-between">
                      <div className="flex items-center">
                        <Tag className="h-5 w-5 text-gray-400 mr-3" />
                        <span className="text-sm text-gray-600">Statut du paiement</span>
                      </div>
                      <span className={clsx(
                        "px-3 py-1 rounded-full text-xs font-bold",
                        selectedRes.statut === 'PAYE' ? "bg-green-100 text-green-700" : "bg-orange-100 text-orange-700"
                      )}>
                        {selectedRes.statut}
                      </span>
                    </div>
                    
                    {canCancel(selectedRes) && (
                      <button
                        disabled={cancelling}
                        onClick={handleCancel}
                        className="w-full bg-red-600 hover:bg-red-700 text-white py-3 rounded-xl font-bold flex items-center justify-center transition disabled:opacity-50"
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
