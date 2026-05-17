'use client';

import { ParkingSpot } from '@/types';
import { clsx } from 'clsx';
import { Zap, Accessibility, Circle } from 'lucide-react';

interface ParkingPlanProps {
  spots: ParkingSpot[];
  selectedSpotId: number | null;
  onSelectSpot: (spot: ParkingSpot) => void;
}

export default function ParkingPlan({ spots, selectedSpotId, onSelectSpot }: ParkingPlanProps) {
  // We'll use a grid system or absolute positioning based on coordX/coordY
  // For simplicity and responsiveness, let's assume a container of fixed aspect ratio
  
  const getSpotIcon = (type: string) => {
    switch (type) {
      case 'ELECTRIQUE': return <Zap className="h-4 w-4" />;
      case 'HANDICAPE': return <Accessibility className="h-4 w-4" />;
      default: return <Circle className="h-4 w-4" />;
    }
  };

  const getSpotColor = (spot: ParkingSpot) => {
    if (spot.id === selectedSpotId) return 'bg-blue-500 text-white border-blue-700 shadow-lg scale-105 z-10';
    if (spot.statut === 'OCCUPE') return 'bg-red-100 text-red-500 border-red-200 cursor-not-allowed';
    if (spot.statut === 'RESERVE') return 'bg-orange-100 text-orange-500 border-orange-200 cursor-not-allowed';
    return 'bg-green-100 text-green-600 border-green-200 hover:bg-green-200 hover:scale-105 cursor-pointer';
  };

  return (
    <div className="relative w-full bg-gray-50 border-2 border-gray-200 rounded-xl p-8 min-h-[400px] shadow-inner overflow-auto">
      <div className="grid grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-4">
        {spots.map((spot) => (
          <div
            key={spot.id}
            onClick={() => spot.statut === 'LIBRE' && onSelectSpot(spot)}
            className={clsx(
              "flex flex-col items-center justify-center p-3 rounded-lg border-2 transition-all duration-200 aspect-square",
              getSpotColor(spot)
            )}
            title={`Place ${spot.numeroPlace} - ${spot.type}`}
          >
            <span className="text-xs font-bold mb-1">{spot.numeroPlace}</span>
            {getSpotIcon(spot.type)}
            <span className="text-[10px] mt-1 capitalize hidden sm:block">
              {spot.statut.toLowerCase()}
            </span>
          </div>
        ))}
      </div>
      
      {/* Legend */}
      <div className="mt-8 flex flex-wrap gap-4 border-t pt-4 text-sm text-gray-600">
        <div className="flex items-center"><div className="w-3 h-3 bg-green-100 border border-green-200 rounded mr-2"></div> Libre</div>
        <div className="flex items-center"><div className="w-3 h-3 bg-red-100 border border-red-200 rounded mr-2"></div> Occupé</div>
        <div className="flex items-center"><div className="w-3 h-3 bg-orange-100 border border-orange-200 rounded mr-2"></div> Réservé</div>
        <div className="flex items-center"><div className="w-3 h-3 bg-blue-500 border border-blue-700 rounded mr-2"></div> Sélectionné</div>
        <div className="flex items-center ml-auto"><Zap className="h-3 w-3 mr-1" /> Électrique</div>
        <div className="flex items-center"><Accessibility className="h-3 w-3 mr-1" /> Handicapé</div>
      </div>
    </div>
  );
}
