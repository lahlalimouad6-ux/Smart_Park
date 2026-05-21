'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import api from '@/lib/api';
import { Calendar, ChevronLeft, Search } from 'lucide-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';
import { clsx } from 'clsx';

type AdminReservationRow = {
  reservationId: number;
  userId: number;
  userNom: string;
  userPrenom: string;
  userEmail: string;
  parkingId: number;
  parkingNom: string;
  spotId: number;
  spotNumeroPlace: string;
  dateDebut: string;
  dateFin: string;
  montantTotal: number;
  statut: 'EN_ATTENTE' | 'PAYE' | 'ANNULE' | 'TERMINE';
};

type PagedResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

function isAdminUser(): boolean {
  try {
    const userStr = localStorage.getItem('user');
    if (!userStr) return false;
    const user = JSON.parse(userStr);
    const token = user?.token || user?.accessToken || user?.jwt;
    return Boolean(token) && Array.isArray(user?.roles) && (user.roles.includes('ADMIN') || user.roles.includes('ROLE_ADMIN'));
  } catch {
    return false;
  }
}

function getHttpStatus(err: unknown): number | null {
  if (typeof err !== 'object' || err === null) return null;
  const response = (err as { response?: unknown }).response;
  if (typeof response !== 'object' || response === null) return null;
  const status = (response as { status?: unknown }).status;
  return typeof status === 'number' ? status : null;
}

export default function AdminReservationsPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [rows, setRows] = useState<AdminReservationRow[]>([]);
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [size] = useState(5);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    if (!isAdminUser()) {
      router.replace('/parkings');
      return;
    }
    api.get('/auth/me')
      .then((res) => {
        const roles = res.data?.roles;
        const ok = Array.isArray(roles) && (roles.includes('ADMIN') || roles.includes('ROLE_ADMIN'));
        if (!ok) {
          localStorage.removeItem('user');
          router.replace('/login');
        }
      })
      .catch(() => {
        localStorage.removeItem('user');
        router.replace('/login');
      });
  }, [router]);

  const fetchRows = useCallback(async (targetPage: number, targetQ: string) => {
    try {
      setLoading(true);
      const res = await api.get<PagedResponse<AdminReservationRow>>('/reservations/admin/paged', {
        params: { page: targetPage, size, q: targetQ }
      });
      const data = res.data;
      setRows(data?.items || []);
      setTotalElements(data?.totalElements || 0);
      setTotalPages(data?.totalPages || 0);
      setPage(data?.page ?? targetPage);
    } catch (err) {
      const status = getHttpStatus(err);
      if (status === 401 || status === 403) {
        alert("Accès refusé. Reconnecte-toi avec un compte ADMIN.");
        localStorage.removeItem('user');
        router.replace('/login');
        return;
      }
      alert('Erreur lors du chargement des réservations.');
    } finally {
      setLoading(false);
    }
  }, [router, size]);

  useEffect(() => {
    const t = setTimeout(() => {
      void fetchRows(page, q);
    }, 250);
    return () => clearTimeout(t);
  }, [page, q, fetchRows]);

  if (loading) {
    return (
      <div className="sp-container py-10">
        <div className="sp-card p-10 text-center text-slate-600">Chargement...</div>
      </div>
    );
  }

  return (
    <div className="sp-container py-10">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center space-x-3">
          <button
            onClick={() => router.push('/admin')}
            className="sp-btn"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-slate-900">Réservations</h1>
            <p className="text-slate-600">Réservations des conducteurs</p>
          </div>
        </div>
        <button
          onClick={() => void fetchRows(page, q)}
          className="sp-btn"
        >
          Actualiser
        </button>
      </div>

      <div className="sp-ticket">
        <div className="p-6 flex items-center justify-between gap-4 flex-wrap">
          <div className="text-sm text-slate-600">
            Total : {totalElements} réservation(s) • Page {Math.min(page + 1, Math.max(1, totalPages))}/{Math.max(1, totalPages)}
          </div>
          <div className="relative w-full sm:w-[420px]">
            <Search className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              value={q}
              onChange={(e) => {
                setPage(0);
                setQ(e.target.value);
              }}
              placeholder="Rechercher (email, parking, place, #id)..."
              className="w-full pl-9 pr-3 py-2.5 rounded-2xl border-2 border-[rgba(11,18,32,0.16)] focus:outline-none focus:ring-2 focus:ring-blue-500 bg-[color:var(--card)] text-slate-900"
            />
          </div>
        </div>

        <div className="px-6 py-4 border-b-2 border-[rgba(11,18,32,0.16)] flex items-center justify-between">
          <button
            disabled={loading || page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className={clsx(
              "sp-btn",
              page <= 0 || loading ? "opacity-50 cursor-not-allowed" : ""
            )}
          >
            Précédent
          </button>
          <button
            disabled={loading || totalPages === 0 || page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            className={clsx(
              "sp-btn",
              totalPages === 0 || page >= totalPages - 1 || loading ? "opacity-50 cursor-not-allowed" : ""
            )}
          >
            Suivant
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="sp-table text-left">
            <thead>
              <tr>
                <th className="px-6 py-4">Utilisateur</th>
                <th className="px-6 py-4">Parking</th>
                <th className="px-6 py-4">Place</th>
                <th className="px-6 py-4">Début</th>
                <th className="px-6 py-4">Fin</th>
                <th className="px-6 py-4">Montant</th>
                <th className="px-6 py-4">Statut</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.reservationId}>
                  <td className="px-6 py-4">
                    <div className="font-bold text-gray-900">{r.userNom} {r.userPrenom}</div>
                    <div className="text-sm text-gray-500">{r.userEmail}</div>
                  </td>
                  <td className="px-6 py-4 font-bold text-gray-900">{r.parkingNom}</td>
                  <td className="px-6 py-4 text-gray-900">{r.spotNumeroPlace}</td>
                  <td className="px-6 py-4 text-gray-700">
                    <div className="flex items-center text-sm">
                      <Calendar className="h-4 w-4 text-gray-400 mr-2" />
                      {format(new Date(r.dateDebut), "dd/MM/yyyy HH:mm", { locale: fr })}
                    </div>
                  </td>
                  <td className="px-6 py-4 text-gray-700">
                    <div className="flex items-center text-sm">
                      <Calendar className="h-4 w-4 text-gray-400 mr-2" />
                      {format(new Date(r.dateFin), "dd/MM/yyyy HH:mm", { locale: fr })}
                    </div>
                  </td>
                  <td className="px-6 py-4 text-gray-900 font-bold">{Number(r.montantTotal ?? 0).toFixed(2)} €</td>
                  <td className="px-6 py-4">
                    <span
                      className={clsx(
                        "sp-badge",
                        r.statut === 'PAYE'
                          ? "sp-badge-success"
                          : r.statut === 'ANNULE'
                            ? "sp-badge-muted"
                            : r.statut === 'TERMINE'
                              ? "sp-badge-info"
                              : "sp-badge-warn"
                      )}
                    >
                      {r.statut}
                    </span>
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-6 py-10 text-center text-gray-400 italic">
                    Aucune réservation trouvée.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
