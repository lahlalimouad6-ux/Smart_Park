'use client';

import { useEffect, useState } from 'react';
import api from '@/lib/api';
import { Parking } from '@/types';
import Link from 'next/link';
import { Search, MapPin, Euro, ArrowRight } from 'lucide-react';
import dynamic from 'next/dynamic';

const Map = dynamic(() => import('@/components/Map'), {
  loading: () => (
    <div className="h-[400px] bg-gray-100 animate-pulse rounded-lg flex items-center justify-center text-gray-400">
      Chargement de la carte...
    </div>
  ),
  ssr: false
});

export default function ParkingsPage() {
  const [parkings, setParkings] = useState<Parking[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    api.get('/parkings')
      .then(res => setParkings(res.data || []))
      .catch(err => console.error(err))
      .finally(() => setLoading(false));
  }, []);

  const filteredParkings = parkings.filter(p => 
    p.nom.toLowerCase().includes(searchTerm.toLowerCase()) || 
    p.ville.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="sp-container py-10">
      <div className="flex flex-col lg:flex-row lg:items-end lg:justify-between gap-6 mb-8">
        <div>
          <div className="sp-chip">
            <MapPin className="h-3.5 w-3.5 text-blue-600" />
            Carte + liste
          </div>
          <h1 className="mt-4 text-3xl sm:text-4xl font-extrabold tracking-tight text-slate-900">Trouver un parking</h1>
          <p className="mt-2 text-slate-600">Recherchez par nom ou ville, puis sélectionnez une place sur le plan.</p>
        </div>
        <div className="sp-card p-3 w-full lg:w-[420px]">
          <div className="relative">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <Search className="h-5 w-5 text-slate-400" />
            </div>
            <input
              type="text"
              className="block w-full pl-10 pr-3 py-2.5 border border-[rgba(15,23,42,0.10)] rounded-2xl leading-5 bg-white placeholder-slate-400 text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
              placeholder="Rechercher par nom ou ville..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <div className="lg:col-span-2">
          <div className="sp-ticket">
            <div className="p-5 flex items-center justify-between">
              <div className="text-sm font-extrabold text-slate-900">Résultats</div>
              <div className="text-xs font-bold text-slate-500">
                {loading ? '...' : `${filteredParkings.length} parking(s)`}
              </div>
            </div>
            <div className="sp-divider" />
            <div className="p-5 space-y-4 max-h-[520px] overflow-y-auto">
              {loading ? (
                [1, 2, 3].map(i => <div key={i} className="h-28 bg-slate-100 animate-pulse rounded-2xl border border-slate-200"></div>)
              ) : filteredParkings.length > 0 ? (
                filteredParkings.map(parking => (
                  <div key={parking.id} className="sp-card p-4 hover:shadow-lg transition-shadow">
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <div className="font-extrabold text-slate-900">{parking.nom}</div>
                        <div className="mt-1 text-sm text-slate-600 flex items-center">
                          <MapPin className="h-4 w-4 mr-1 text-slate-400" />
                          {parking.ville}
                        </div>
                      </div>
                      <div className="sp-chip">
                        <Euro className="h-3.5 w-3.5 text-slate-500" />
                        {parking.tarifHeure}€/h
                      </div>
                    </div>
                    <div className="mt-4 flex items-center justify-end">
                      <Link href={`/parkings/${parking.id}`} className="sp-btn sp-btn-primary">
                        Choisir
                        <ArrowRight className="h-4 w-4" />
                      </Link>
                    </div>
                  </div>
                ))
              ) : (
                <div className="text-center py-10 text-slate-500">
                  Aucun parking trouvé pour &quot;{searchTerm}&quot;
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="lg:col-span-3">
          <div className="sp-card overflow-hidden">
            <div className="p-4 flex items-center justify-between">
              <div className="text-sm font-extrabold text-slate-900">Carte</div>
              <div className="text-xs font-bold text-slate-500">Clique sur un marqueur</div>
            </div>
            <div className="h-[520px] border-t border-[rgba(15,23,42,0.08)]">
              <Map parkings={filteredParkings} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
