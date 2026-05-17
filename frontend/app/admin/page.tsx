'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import api from '@/lib/api';
import { Parking, ParkingSpot, Zone } from '@/types';
import { 
  ArrowLeft,
  CheckCircle2,
  Clock,
  MapPin, 
  Plus,
  Edit, 
  Trash2,
  Layers,
  Square
} from 'lucide-react';
import { clsx } from 'clsx';

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

function Modal({ open, title, onClose, children }: { open: boolean; title: string; onClose: () => void; children: React.ReactNode }) {
  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
      <div className="w-full max-w-lg bg-white rounded-2xl shadow-xl border border-gray-100">
        <div className="p-5 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-lg font-bold text-gray-900">{title}</h3>
          <button onClick={onClose} className="px-3 py-2 rounded-xl hover:bg-gray-50 text-gray-600 font-bold">
            Fermer
          </button>
        </div>
        <div className="p-5">{children}</div>
      </div>
    </div>
  );
}

export default function AdminDashboard() {
  const router = useRouter();
  const [parkings, setParkings] = useState<Parking[]>([]);
  const [zones, setZones] = useState<Zone[]>([]);
  const [selectedParking, setSelectedParking] = useState<Parking | null>(null);

  const [loading, setLoading] = useState(true);
  const [authChecked, setAuthChecked] = useState(false);
  const [stats, setStats] = useState<{
    totalParkings: number;
    totalUsers: number;
    totalSpots: number;
    occupiedSpots: number;
    activeReservations: number;
    totalRevenue: number;
    occupationRate: number;
  }>({
    totalParkings: 0,
    totalUsers: 0,
    totalSpots: 0,
    occupiedSpots: 0,
    activeReservations: 0,
    totalRevenue: 0,
    occupationRate: 0
  });

  const [parkingModalOpen, setParkingModalOpen] = useState(false);
  const [editingParking, setEditingParking] = useState<Parking | null>(null);
  const [parkingForm, setParkingForm] = useState({
    nom: '',
    ville: '',
    adresse: '',
    coordGps: '',
    tarifHeure: 0
  });
  const [nombrePlaces, setNombrePlaces] = useState(20);

  const [zoneModalOpen, setZoneModalOpen] = useState(false);
  const [editingZone, setEditingZone] = useState<Zone | null>(null);
  const [zoneForm, setZoneForm] = useState({ nomZone: '' });

  const [spotModalOpen, setSpotModalOpen] = useState(false);
  const [editingSpot, setEditingSpot] = useState<ParkingSpot | null>(null);
  const [spotZoneId, setSpotZoneId] = useState<number | null>(null);
  const [spotForm, setSpotForm] = useState({
    numeroPlace: '',
    type: 'STANDARD' as ParkingSpot['type'],
    coordX: 0,
    coordY: 0,
    statut: 'LIBRE' as ParkingSpot['statut']
  });

  useEffect(() => {
    if (!isAdminUser()) {
      router.replace('/parkings');
      return;
    }
    setTimeout(() => setAuthChecked(true), 0);
  }, [router]);

  useEffect(() => {
    if (!authChecked) return;
    api.get('/auth/me')
      .then((res) => {
        const roles = res.data?.roles;
        const isAdmin = Array.isArray(roles) && (roles.includes('ADMIN') || roles.includes('ROLE_ADMIN'));
        if (!isAdmin) {
          localStorage.removeItem('user');
          router.replace('/login');
        }
      })
      .catch(() => {
        localStorage.removeItem('user');
        router.replace('/login');
      });
  }, [authChecked, router]);

  const fetchParkingsAndStats = async () => {
    try {
      setLoading(true);
      const [parkingsRes, statsRes] = await Promise.all([
        api.get('/parkings'),
        api.get('/parkings/admin/stats')
      ]);
      setParkings(parkingsRes.data);

      const s = statsRes.data;
      const totalRevenue =
        typeof s.totalRevenue === 'number'
          ? s.totalRevenue
          : typeof s.totalRevenue === 'string'
            ? Number(s.totalRevenue)
            : Number(s.totalRevenue ?? 0);

      setStats({
        totalParkings: Number(s.totalParkings ?? 0),
        totalUsers: Number(s.totalUsers ?? 0),
        totalSpots: Number(s.totalSpots ?? 0),
        occupiedSpots: Number(s.occupiedSpots ?? 0),
        activeReservations: Number(s.activeReservations ?? 0),
        totalRevenue: Number.isFinite(totalRevenue) ? totalRevenue : 0,
        occupationRate: Number(s.occupationRate ?? 0)
      });
    } catch (err) {
      console.error(err);
      const status = getHttpStatus(err);
      if (status === 401 || status === 403) {
        alert("Accès refusé. Reconnecte-toi avec un compte ADMIN.");
        localStorage.removeItem('user');
        router.replace('/login');
        return;
      }
    } finally {
      setLoading(false);
    }
  };

  const fetchZones = async (parkingId: number) => {
    try {
      const res = await api.get(`/parkings/${parkingId}/zones`);
      setZones(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    if (!authChecked) return;
    setTimeout(() => {
      void fetchParkingsAndStats();
    }, 0);
  }, [authChecked]);

  const openCreateParking = () => {
    setEditingParking(null);
    setParkingForm({ nom: '', ville: '', adresse: '', coordGps: '', tarifHeure: 0 });
    setNombrePlaces(20);
    setParkingModalOpen(true);
  };

  const openEditParking = (p: Parking) => {
    setEditingParking(p);
    setParkingForm({
      nom: p.nom,
      ville: p.ville,
      adresse: p.adresse,
      coordGps: p.coordGps,
      tarifHeure: p.tarifHeure
    });
    setParkingModalOpen(true);
  };

  const submitParking = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingParking) {
        await api.put(`/parkings/${editingParking.id}`, parkingForm);
      } else {
        await api.post('/parkings', { ...parkingForm, nombrePlaces });
      }
      setParkingModalOpen(false);
      await fetchParkingsAndStats();
    } catch (err) {
      console.error(err);
      const status = getHttpStatus(err);
      if (status === 401 || status === 403) {
        alert("Accès refusé. Connecte-toi avec un compte ADMIN.");
        setParkingModalOpen(false);
        router.replace('/parkings');
        return;
      }
      alert("Erreur lors de l'enregistrement du parking");
    }
  };

  const deleteParking = async (id: number) => {
    if (!confirm('Supprimer ce parking ?')) return;
    try {
      await api.delete(`/parkings/${id}`);
      if (selectedParking?.id === id) {
        setSelectedParking(null);
        setZones([]);
      }
      await fetchParkingsAndStats();
    } catch (err) {
      console.error(err);
      alert('Erreur lors de la suppression du parking');
    }
  };

  const openCreateZone = () => {
    setEditingZone(null);
    setZoneForm({ nomZone: '' });
    setZoneModalOpen(true);
  };

  const openEditZone = (z: Zone) => {
    setEditingZone(z);
    setZoneForm({ nomZone: z.nomZone });
    setZoneModalOpen(true);
  };

  const submitZone = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedParking) return;
    try {
      if (editingZone) {
        await api.put(`/parkings/zones/${editingZone.id}`, zoneForm);
      } else {
        await api.post(`/parkings/${selectedParking.id}/zones`, zoneForm);
      }
      setZoneModalOpen(false);
      await fetchZones(selectedParking.id);
    } catch (err) {
      console.error(err);
      alert("Erreur lors de l'enregistrement de la zone");
    }
  };

  const deleteZone = async (zoneId: number) => {
    if (!confirm('Supprimer cette zone ?')) return;
    try {
      await api.delete(`/parkings/zones/${zoneId}`);
      if (selectedParking) await fetchZones(selectedParking.id);
    } catch (err) {
      console.error(err);
      alert('Erreur lors de la suppression de la zone');
    }
  };

  const openCreateSpot = (zoneId: number) => {
    setSpotZoneId(zoneId);
    setEditingSpot(null);
    setSpotForm({ numeroPlace: '', type: 'STANDARD', coordX: 0, coordY: 0, statut: 'LIBRE' });
    setSpotModalOpen(true);
  };

  const openEditSpot = (zoneId: number, spot: ParkingSpot) => {
    setSpotZoneId(zoneId);
    setEditingSpot(spot);
    setSpotForm({
      numeroPlace: spot.numeroPlace,
      type: spot.type,
      coordX: spot.coordX,
      coordY: spot.coordY,
      statut: spot.statut
    });
    setSpotModalOpen(true);
  };

  const submitSpot = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedParking) return;
    if (!spotZoneId) return;
    try {
      if (editingSpot) {
        await api.put(`/parkings/spots/${editingSpot.id}`, spotForm);
      } else {
        await api.post(`/parkings/zones/${spotZoneId}/spots`, spotForm);
      }
      setSpotModalOpen(false);
      await fetchZones(selectedParking.id);
    } catch (err) {
      console.error(err);
      alert("Erreur lors de l'enregistrement de la place");
    }
  };

  const deleteSpot = async (spotId: number) => {
    if (!confirm('Supprimer cette place ?')) return;
    try {
      await api.delete(`/parkings/spots/${spotId}`);
      if (selectedParking) await fetchZones(selectedParking.id);
    } catch (err) {
      console.error(err);
      alert('Erreur lors de la suppression de la place');
    }
  };

  const selectParking = async (p: Parking) => {
    setSelectedParking(p);
    await fetchZones(p.id);
  };

  const occupationLabel = useMemo(() => {
    const value = Number.isFinite(stats.occupationRate) ? stats.occupationRate : 0;
    return `${value.toFixed(1)}%`;
  }, [stats.occupationRate]);

  if (!authChecked) return <div className="p-8 text-center">Chargement...</div>;
  if (loading) return <div className="p-8 text-center">Chargement du dashboard...</div>;

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      {!selectedParking ? (
        <>
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-3xl font-bold text-gray-900">Dashboard Admin</h1>
              <p className="text-gray-500">Gestion globale de la plateforme SmartPark</p>
            </div>
            <button onClick={openCreateParking} className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-xl font-bold flex items-center shadow-lg transition">
              <Plus className="h-5 w-5 mr-2" /> Nouveau Parking
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-10">
            <StatCard icon={<MapPin className="text-blue-600" />} label="Parkings" value={stats.totalParkings} bgColor="bg-blue-100" />
            <StatCard icon={<CheckCircle2 className="text-green-600" />} label="Occupation" value={occupationLabel} bgColor="bg-green-100" />
            <StatCard icon={<CheckCircle2 className="text-purple-600" />} label="Revenus" value={`${stats.totalRevenue.toFixed(2)} €`} bgColor="bg-purple-100" />
            <StatCard icon={<Clock className="text-orange-600" />} label="Réservations actives" value={stats.activeReservations} bgColor="bg-orange-100" />
          </div>

          <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
            <div className="p-6 border-b border-gray-100 flex justify-between items-center">
              <h2 className="text-xl font-bold">Liste des Parkings</h2>
              <div className="text-sm text-gray-400">Total : {parkings.length} parkings</div>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead className="bg-gray-50 text-gray-500 text-xs uppercase font-bold">
                  <tr>
                    <th className="px-6 py-4">Nom</th>
                    <th className="px-6 py-4">Ville</th>
                    <th className="px-6 py-4">Tarif (€/h)</th>
                    <th className="px-6 py-4">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {parkings.map((p) => (
                    <tr key={p.id} className="hover:bg-gray-50 transition">
                      <td className="px-6 py-4 font-bold text-gray-900">
                        <button onClick={() => selectParking(p)} className="hover:text-blue-600 transition-colors">
                          {p.nom}
                        </button>
                      </td>
                      <td className="px-6 py-4 text-gray-600">{p.ville}</td>
                      <td className="px-6 py-4 text-blue-600 font-bold">{p.tarifHeure}</td>
                      <td className="px-6 py-4">
                        <div className="flex space-x-2">
                          <button onClick={() => openEditParking(p)} className="p-2 text-gray-400 hover:text-blue-600 transition">
                            <Edit className="h-4 w-4" />
                          </button>
                          <button onClick={() => deleteParking(p.id)} className="p-2 text-gray-400 hover:text-red-600 transition">
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {parkings.length === 0 && (
                    <tr>
                      <td colSpan={4} className="px-6 py-10 text-center text-gray-400 italic">
                        Aucun parking pour le moment.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      ) : (
        <>
          <div className="flex items-center justify-between mb-8">
            <div className="flex items-center space-x-3">
              <button onClick={() => { setSelectedParking(null); setZones([]); }} className="p-2 rounded-xl hover:bg-white border border-transparent hover:border-gray-200 transition">
                <ArrowLeft className="h-5 w-5 text-gray-700" />
              </button>
              <div>
                <h1 className="text-2xl font-extrabold text-gray-900">{selectedParking.nom}</h1>
                <p className="text-gray-500">{selectedParking.adresse} • {selectedParking.ville}</p>
              </div>
            </div>
            <button onClick={openCreateZone} className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-xl font-bold flex items-center shadow-lg transition">
              <Plus className="h-5 w-5 mr-2" /> Nouvelle Zone
            </button>
          </div>

          <div className="space-y-6">
            {zones.map((z) => (
              <div key={z.id} className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
                <div className="p-6 border-b border-gray-100 flex items-center justify-between">
                  <div className="flex items-center space-x-2">
                    <Layers className="h-5 w-5 text-blue-600" />
                    <h2 className="text-lg font-extrabold text-gray-900">{z.nomZone}</h2>
                  </div>
                  <div className="flex items-center space-x-2">
                    <button onClick={() => openCreateSpot(z.id)} className="px-4 py-2 rounded-xl border border-gray-200 hover:border-blue-300 hover:bg-blue-50 text-blue-700 font-bold transition">
                      <Plus className="inline-block h-4 w-4 mr-1" />
                      Ajouter place
                    </button>
                    <button onClick={() => openEditZone(z)} className="p-2 text-gray-400 hover:text-blue-600 transition">
                      <Edit className="h-4 w-4" />
                    </button>
                    <button onClick={() => deleteZone(z.id)} className="p-2 text-gray-400 hover:text-red-600 transition">
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>

                <div className="p-6">
                  <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
                    {(z.spots || []).map((s) => (
                      <div key={s.id} className={clsx(
                        'relative p-4 rounded-2xl border-2 transition group',
                        s.statut === 'LIBRE' ? 'border-green-100 bg-green-50/40' : s.statut === 'OCCUPE' ? 'border-red-100 bg-red-50/40' : 'border-orange-100 bg-orange-50/40'
                      )}>
                        <div className="flex flex-col items-center space-y-2">
                          <Square className={clsx(
                            'h-8 w-8',
                            s.statut === 'LIBRE' ? 'text-green-500' : s.statut === 'OCCUPE' ? 'text-red-500' : 'text-orange-500'
                          )} />
                          <div className="font-extrabold text-gray-900">{s.numeroPlace}</div>
                          <div className="text-[10px] uppercase font-bold text-gray-500">{s.type}</div>
                        </div>

                        <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition flex items-center justify-center space-x-2 bg-white/80 rounded-2xl">
                          <button onClick={() => openEditSpot(z.id, s)} className="p-2 rounded-xl bg-white border border-gray-200 hover:border-blue-300 text-blue-700 font-bold">
                            <Edit className="h-4 w-4" />
                          </button>
                          <button onClick={() => deleteSpot(s.id)} className="p-2 rounded-xl bg-white border border-gray-200 hover:border-red-300 text-red-700 font-bold">
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>

                  {(z.spots || []).length === 0 && (
                    <div className="py-10 text-center text-gray-400 italic">Aucune place dans cette zone.</div>
                  )}
                </div>
              </div>
            ))}

            {zones.length === 0 && (
              <div className="bg-white rounded-2xl border border-dashed border-gray-200 p-10 text-center">
                <div className="text-gray-900 font-extrabold mb-1">Aucune zone</div>
                <div className="text-gray-500 mb-6">Créez une zone pour commencer à ajouter des places.</div>
                <button onClick={openCreateZone} className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-xl font-bold inline-flex items-center shadow-lg transition">
                  <Plus className="h-5 w-5 mr-2" /> Créer une zone
                </button>
              </div>
            )}
          </div>
        </>
      )}

      <Modal open={parkingModalOpen} title={editingParking ? 'Modifier le parking' : 'Créer un parking'} onClose={() => setParkingModalOpen(false)}>
        <form onSubmit={submitParking} className="space-y-4">
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-1">Nom</label>
            <input value={parkingForm.nom} onChange={(e) => setParkingForm((s) => ({ ...s, nom: e.target.value }))} required className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-bold text-gray-700 mb-1">Ville</label>
              <input value={parkingForm.ville} onChange={(e) => setParkingForm((s) => ({ ...s, ville: e.target.value }))} required className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label className="block text-sm font-bold text-gray-700 mb-1">Tarif (€/h)</label>
              <input type="number" step="0.01" value={parkingForm.tarifHeure} onChange={(e) => setParkingForm((s) => ({ ...s, tarifHeure: Number(e.target.value) }))} required className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          </div>
          {!editingParking && (
            <div>
              <label className="block text-sm font-bold text-gray-700 mb-1">Nombre de places</label>
              <input
                type="number"
                min={1}
                max={500}
                value={nombrePlaces}
                onChange={(e) => setNombrePlaces(Number(e.target.value))}
                required
                className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          )}
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-1">Adresse</label>
            <input value={parkingForm.adresse} onChange={(e) => setParkingForm((s) => ({ ...s, adresse: e.target.value }))} required className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-1">Coordonnées GPS (lat,lng)</label>
            <input value={parkingForm.coordGps} onChange={(e) => setParkingForm((s) => ({ ...s, coordGps: e.target.value }))} required placeholder="48.8566,2.3522" className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <button type="submit" className="w-full bg-blue-600 hover:bg-blue-700 text-white font-extrabold py-3 rounded-xl shadow-lg transition">
            {editingParking ? 'Enregistrer' : 'Créer'}
          </button>
        </form>
      </Modal>

      <Modal open={zoneModalOpen} title={editingZone ? 'Modifier la zone' : 'Créer une zone'} onClose={() => setZoneModalOpen(false)}>
        <form onSubmit={submitZone} className="space-y-4">
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-1">Nom de la zone</label>
            <input value={zoneForm.nomZone} onChange={(e) => setZoneForm({ nomZone: e.target.value })} required className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <button type="submit" className="w-full bg-blue-600 hover:bg-blue-700 text-white font-extrabold py-3 rounded-xl shadow-lg transition">
            {editingZone ? 'Enregistrer' : 'Créer'}
          </button>
        </form>
      </Modal>

      <Modal open={spotModalOpen} title={editingSpot ? 'Modifier la place' : 'Créer une place'} onClose={() => setSpotModalOpen(false)}>
        <form onSubmit={submitSpot} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-bold text-gray-700 mb-1">Numéro</label>
              <input value={spotForm.numeroPlace} onChange={(e) => setSpotForm((s) => ({ ...s, numeroPlace: e.target.value }))} required className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label className="block text-sm font-bold text-gray-700 mb-1">Type</label>
              <select value={spotForm.type} onChange={(e) => setSpotForm((s) => ({ ...s, type: e.target.value as ParkingSpot['type'] }))} className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option value="STANDARD">STANDARD</option>
                <option value="HANDICAPE">HANDICAPE</option>
                <option value="ELECTRIQUE">ELECTRIQUE</option>
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-bold text-gray-700 mb-1">Coord X</label>
              <input type="number" value={spotForm.coordX} onChange={(e) => setSpotForm((s) => ({ ...s, coordX: Number(e.target.value) }))} required className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label className="block text-sm font-bold text-gray-700 mb-1">Coord Y</label>
              <input type="number" value={spotForm.coordY} onChange={(e) => setSpotForm((s) => ({ ...s, coordY: Number(e.target.value) }))} required className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          </div>
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-1">Statut</label>
            <select value={spotForm.statut} onChange={(e) => setSpotForm((s) => ({ ...s, statut: e.target.value as ParkingSpot['statut'] }))} className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500">
              <option value="LIBRE">LIBRE</option>
              <option value="OCCUPE">OCCUPE</option>
              <option value="RESERVE">RESERVE</option>
            </select>
          </div>
          <button type="submit" className="w-full bg-blue-600 hover:bg-blue-700 text-white font-extrabold py-3 rounded-xl shadow-lg transition">
            {editingSpot ? 'Enregistrer' : 'Créer'}
          </button>
        </form>
      </Modal>
    </div>
  );
}

function StatCard({ icon, label, value, bgColor }: { icon: React.ReactNode, label: string, value: string | number, bgColor: string }) {
  return (
    <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 flex items-center space-x-4">
      <div className={clsx("p-4 rounded-xl", bgColor)}>
        {icon}
      </div>
      <div>
        <p className="text-sm text-gray-500 font-medium">{label}</p>
        <p className="text-2xl font-bold text-gray-900">{value}</p>
      </div>
    </div>
  );
}
