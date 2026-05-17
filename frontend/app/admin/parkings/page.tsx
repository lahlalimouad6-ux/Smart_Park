'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import api from '@/lib/api';
import { Parking } from '@/types';
import { clsx } from 'clsx';
import { ChevronLeft, Video, RefreshCw } from 'lucide-react';

type CameraSummary = {
  parkingId: number;
  parkingNom: string;
  videoFile: string;
  configuredSpots: number;
};

type CameraSpot = {
  spotId: number;
  numeroPlace: string;
  statut: 'LIBRE' | 'OCCUPE' | 'RESERVE';
  x: number | null;
  y: number | null;
  w: number | null;
  h: number | null;
};

type CameraDetail = {
  parkingId: number;
  parkingNom: string;
  videoFile: string;
  spots: CameraSpot[];
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

function getToken(): string | null {
  try {
    const userStr = localStorage.getItem('user');
    if (!userStr) return null;
    const user = JSON.parse(userStr);
    return user?.token || user?.accessToken || user?.jwt || null;
  } catch {
    return null;
  }
}

export default function AdminParkingsPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [parkings, setParkings] = useState<Parking[]>([]);
  const [cameras, setCameras] = useState<CameraSummary[]>([]);
  const [selectedParkingId, setSelectedParkingId] = useState<number | null>(null);
  const [cameraDetail, setCameraDetail] = useState<CameraDetail | null>(null);
  const [cameraLoading, setCameraLoading] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [q, setQ] = useState('');

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [statuses, setStatuses] = useState<Record<number, 'LIBRE' | 'OCCUPE' | 'RESERVE'>>({});
  const [videoError, setVideoError] = useState(false);

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

  const fetchAll = async () => {
    setLoading(true);
    try {
      const [pRes, cRes] = await Promise.all([api.get('/parkings'), api.get('/cameras')]);
      setParkings(pRes.data || []);
      setCameras(cRes.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setTimeout(() => {
      void fetchAll();
    }, 0);
  }, []);

  const cameraByParkingId = useMemo(() => {
    const map = new Map<number, CameraSummary>();
    cameras.forEach((c) => map.set(c.parkingId, c));
    return map;
  }, [cameras]);

  const filteredParkings = useMemo(() => {
    const needle = q.trim().toLowerCase();
    if (!needle) return parkings;
    return parkings.filter((p) => `${p.nom} ${p.ville} ${p.adresse}`.toLowerCase().includes(needle));
  }, [parkings, q]);

  const selectedParking = useMemo(() => {
    if (!selectedParkingId) return null;
    return parkings.find((p) => p.id === selectedParkingId) || null;
  }, [parkings, selectedParkingId]);

  const selectedCamera = useMemo(() => {
    if (!selectedParkingId) return null;
    return cameraByParkingId.get(selectedParkingId) || null;
  }, [cameraByParkingId, selectedParkingId]);

  const loadCameraDetail = async (parkingId: number) => {
    setCameraLoading(true);
    setCameraError(null);
    setVideoError(false);
    try {
      const res = await api.get(`/cameras/${parkingId}`);
      const detail: CameraDetail = res.data;
      setCameraDetail(detail);
      const nextStatuses: Record<number, 'LIBRE' | 'OCCUPE' | 'RESERVE'> = {};
      detail.spots.forEach((s) => {
        nextStatuses[s.spotId] = s.statut;
      });
      setStatuses((prev) => ({ ...prev, ...nextStatuses }));
    } catch {
      setCameraDetail(null);
      setStatuses({});
      setCameraError("Impossible de charger la caméra (vérifie que tu es connecté en ADMIN).");
    } finally {
      setCameraLoading(false);
    }
  };

  useEffect(() => {
    if (!selectedParkingId || !selectedCamera) {
      setTimeout(() => {
        setCameraDetail(null);
        setCameraLoading(false);
        setCameraError(null);
        setVideoError(false);
      }, 0);
      return;
    }
    setTimeout(() => {
      void loadCameraDetail(selectedParkingId);
    }, 0);
  }, [selectedParkingId, selectedCamera]);

  useEffect(() => {
    if (!selectedParkingId || !selectedCamera) return;
    const interval = setInterval(() => {
      void loadCameraDetail(selectedParkingId);
    }, 10_000);
    return () => clearInterval(interval);
  }, [selectedParkingId, selectedCamera]);

  useEffect(() => {
    if (!selectedParkingId || !cameraDetail) return;
    const interval = setInterval(async () => {
      const v = videoRef.current;
      if (!v || v.readyState < 2) return;
      try {
        const res = await api.get(`/cameras/${selectedParkingId}/live-status`, { params: { second: v.currentTime } });
        const rows = Array.isArray(res.data) ? res.data : [];
        if (rows.length === 0) return;
        setStatuses((prev) => {
          const next = { ...prev };
          for (const r of rows) {
            if (!r || typeof r.spotId !== 'number' || !r.statut) continue;
            next[r.spotId] = r.statut;
          }
          return next;
        });
      } catch {}
    }, 500);
    return () => clearInterval(interval);
  }, [selectedParkingId, cameraDetail]);

  const videoUrl = useMemo(() => {
    if (!cameraDetail) return null;
    const token = getToken();
    if (!token) return null;
    return `http://localhost:8080/api/cameras/video/${encodeURIComponent(cameraDetail.videoFile)}?token=${encodeURIComponent(token)}`;
  }, [cameraDetail]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !videoUrl) return;
    const tryPlay = () => {
      try {
        video.muted = true;
        void video.play();
      } catch {}
    };
    tryPlay();
    video.addEventListener('loadeddata', tryPlay);
    video.addEventListener('canplay', tryPlay);
    return () => {
      video.removeEventListener('loadeddata', tryPlay);
      video.removeEventListener('canplay', tryPlay);
    };
  }, [videoUrl]);

  const configuredRegions = useMemo(() => {
    if (!cameraDetail) return [];
    return cameraDetail.spots
      .filter((s) => s.x !== null && s.y !== null && s.w !== null && s.h !== null)
      .map((s) => ({ spotId: s.spotId, numeroPlace: s.numeroPlace, x: s.x as number, y: s.y as number, w: s.w as number, h: s.h as number }));
  }, [cameraDetail]);

  if (loading) return <div className="p-8 text-center">Chargement...</div>;

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center space-x-3">
          <button
            onClick={() => router.push('/admin')}
            className="p-2 rounded-xl hover:bg-white border border-transparent hover:border-gray-200 transition"
          >
            <ChevronLeft className="h-5 w-5 text-gray-700" />
          </button>
          <div>
            <h1 className="text-3xl font-bold text-gray-900">Parkings (Admin)</h1>
            <p className="text-gray-500">Gestion + monitoring caméra (dataset)</p>
          </div>
        </div>
        <button
          onClick={() => void fetchAll()}
          className="bg-white hover:bg-gray-50 text-gray-900 px-5 py-3 rounded-xl font-bold border border-gray-200 transition flex items-center"
        >
          <RefreshCw className="h-4 w-4 mr-2" />
          Actualiser
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
          <div className="p-4 border-b border-gray-100 flex items-center gap-3">
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Rechercher parking..."
              className="w-full px-3 py-2 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-200"
            />
          </div>
          <div className="max-h-[70vh] overflow-y-auto divide-y divide-gray-100">
            {filteredParkings.map((p) => {
              const cam = cameraByParkingId.get(p.id);
              return (
                <button
                  key={p.id}
                  onClick={() => setSelectedParkingId(p.id)}
                  className={clsx(
                    'w-full text-left p-4 hover:bg-gray-50 transition flex items-center justify-between',
                    selectedParkingId === p.id ? 'bg-blue-50' : 'bg-white'
                  )}
                >
                  <div>
                    <div className="font-bold text-gray-900">{p.nom}</div>
                    <div className="text-xs text-gray-500">{p.ville} • {p.adresse}</div>
                  </div>
                  {cam ? (
                    <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold bg-purple-100 text-purple-700">
                      <Video className="h-3 w-3 mr-1" />
                      Caméra
                    </span>
                  ) : null}
                </button>
              );
            })}
            {filteredParkings.length === 0 && (
              <div className="p-8 text-center text-gray-400">Aucun parking</div>
            )}
          </div>
        </div>

        <div className="lg:col-span-2">
          {!selectedParking ? (
            <div className="bg-white rounded-2xl border border-dashed border-gray-200 p-10 text-center text-gray-500">
              Sélectionne un parking à gauche.
            </div>
          ) : selectedCamera && cameraDetail ? (
            <div className="space-y-4">
              <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-5 flex items-start justify-between gap-4 flex-wrap">
                <div>
                  <div className="text-xl font-extrabold text-gray-900">{selectedParking.nom}</div>
                  <div className="text-sm text-gray-500">Vidéo : {cameraDetail.videoFile}</div>
                </div>
                <div className="flex items-center gap-2 flex-wrap">
                </div>
              </div>

              <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-5">
                <div className="flex items-center gap-4 flex-wrap mb-4">
                  <div className="ml-auto text-sm text-gray-500">
                    Places configurées : {configuredRegions.length}
                  </div>
                </div>

                <div className="relative w-full overflow-hidden rounded-xl border border-gray-200 bg-black">
                  <video
                    ref={videoRef}
                    src={videoUrl || undefined}
                    className="w-full h-auto"
                    autoPlay
                    loop
                    muted
                    playsInline
                    crossOrigin="anonymous"
                    controls={false}
                    disablePictureInPicture
                    controlsList="nodownload noplaybackrate noremoteplayback"
                    onPause={() => {
                      const v = videoRef.current;
                      if (!v) return;
                      v.muted = true;
                      void v.play();
                    }}
                    onError={() => setVideoError(true)}
                  />
                  <div
                    className="absolute inset-0 pointer-events-none"
                  >
                    {configuredRegions.map((r) => {
                      const status = statuses[r.spotId] || 'LIBRE';
                      const bg =
                        status === 'OCCUPE' ? 'bg-red-500/25 border-red-400'
                          : status === 'RESERVE' ? 'bg-orange-500/25 border-orange-400'
                            : 'bg-green-500/25 border-green-400';
                      return (
                        <div
                          key={`${r.numeroPlace}-${r.x}-${r.y}-${r.w}-${r.h}`}
                          className={clsx('absolute border-2 rounded-lg', bg)}
                          style={{
                            left: `${r.x * 100}%`,
                            top: `${r.y * 100}%`,
                            width: `${r.w * 100}%`,
                            height: `${r.h * 100}%`
                          }}
                        >
                          <div className="absolute -top-2 left-2 px-2 py-0.5 rounded bg-black/60 text-white text-[10px] font-bold">
                            {r.numeroPlace}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
                {videoError && (
                  <div className="mt-3 text-sm text-red-600 font-bold">
                    Erreur de lecture vidéo. Vérifie le fichier MP4 dans le dossier datasets et le token admin.
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-8 text-center text-gray-600">
              <div className="font-extrabold text-gray-900 mb-1">{selectedParking.nom}</div>
              {selectedCamera ? (
                cameraLoading ? (
                  <div className="text-gray-500">Chargement de la caméra...</div>
                ) : cameraError ? (
                  <div className="text-gray-500">{cameraError}</div>
                ) : (
                  <div className="text-gray-500">Chargement de la caméra...</div>
                )
              ) : (
                <div className="text-gray-500">Ce parking n&apos;a pas de vidéo caméra dans le dataset.</div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
