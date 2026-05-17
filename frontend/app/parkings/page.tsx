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
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-8 gap-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Trouver un Parking</h1>
          <p className="text-gray-600">Réservez votre place en quelques clics</p>
        </div>
        <div className="relative max-w-sm w-full">
          <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
            <Search className="h-5 w-5 text-gray-400" />
          </div>
          <input
            type="text"
            className="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg leading-5 bg-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
            placeholder="Rechercher par nom ou ville..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2">
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
            <Map parkings={filteredParkings} />
          </div>
        </div>

        <div className="space-y-4 max-h-[400px] overflow-y-auto pr-2">
          {loading ? (
            [1, 2, 3].map(i => <div key={i} className="h-32 bg-gray-100 animate-pulse rounded-xl"></div>)
          ) : filteredParkings.length > 0 ? (
            filteredParkings.map(parking => (
              <div key={parking.id} className="bg-white p-4 rounded-xl shadow-sm border border-gray-100 hover:border-blue-200 transition group">
                <h3 className="font-bold text-gray-900 group-hover:text-blue-600 transition">{parking.nom}</h3>
                <div className="flex items-center text-sm text-gray-500 mt-1">
                  <MapPin className="h-3 w-3 mr-1" />
                  {parking.ville}
                </div>
                <div className="flex items-center justify-between mt-4">
                  <div className="flex items-center text-blue-600 font-bold">
                    <Euro className="h-4 w-4 mr-1" />
                    {parking.tarifHeure}€ / h
                  </div>
                  <Link 
                    href={`/parkings/${parking.id}`}
                    className="flex items-center text-sm font-medium text-gray-700 hover:text-blue-600 transition"
                  >
                    Choisir <ArrowRight className="h-4 w-4 ml-1" />
                  </Link>
                </div>
              </div>
            ))
          ) : (
            <div className="text-center py-12 text-gray-500">
              Aucun parking trouvé pour &quot;{searchTerm}&quot;
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
