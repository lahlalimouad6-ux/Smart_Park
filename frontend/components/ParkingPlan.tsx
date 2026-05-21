'use client';

import { ParkingSpot } from '@/types';
import { clsx } from 'clsx';
import { Accessibility, Car, Circle, Zap } from 'lucide-react';

interface ParkingPlanProps {
  spots: ParkingSpot[];
  selectedSpotId: number | null;
  onSelectSpot: (spot: ParkingSpot) => void;
}

export default function ParkingPlan({ spots, selectedSpotId, onSelectSpot }: ParkingPlanProps) {
  const getSpotIcon = (type: string) => {
    switch (type) {
      case 'ELECTRIQUE': return <Zap className="h-4 w-4" />;
      case 'HANDICAPE': return <Accessibility className="h-4 w-4" />;
      default: return <Circle className="h-4 w-4" />;
    }
  };

  const getSpotState = (spot: ParkingSpot) => {
    if (spot.id === selectedSpotId) return 'selected' as const;
    if (spot.statut === 'OCCUPE') return 'occupied' as const;
    if (spot.statut === 'RESERVE') return 'reserved' as const;
    return 'free' as const;
  };

  return (
    <div className="sp-card p-5">
      <div className="relative overflow-hidden rounded-[28px] border-2 border-[rgba(11,18,32,0.16)] bg-white/70">
        <div className="p-5">
          <div className="grid grid-cols-4 sm:grid-cols-6 lg:grid-cols-8 gap-5">
            {spots.map((spot) => {
              const state = getSpotState(spot);
              const clickable = spot.statut === 'LIBRE';
              const upcomingLabel = spot.nextReservationStart
                ? (() => {
                    const d = new Date(spot.nextReservationStart);
                    if (!Number.isFinite(d.getTime())) return null;
                    const hh = String(d.getHours()).padStart(2, '0');
                    const mm = String(d.getMinutes()).padStart(2, '0');
                    return `${hh}:${mm}`;
                  })()
                : null;
              const dot =
                state === 'free' || state === 'selected' ? 'bg-emerald-500' : 'bg-red-500';
              const cardCls =
                state === 'selected'
                  ? 'ring-2 ring-blue-600 border-[rgba(11,18,32,0.16)] bg-emerald-50/35'
                  : state === 'free'
                    ? 'border-[rgba(11,18,32,0.16)] bg-emerald-50/30 hover:border-emerald-400'
                    : 'border-[rgba(11,18,32,0.12)] bg-red-50/30 opacity-80';

              return (
                <button
                  key={spot.id}
                  type="button"
                  disabled={!clickable}
                  onClick={() => clickable && onSelectSpot(spot)}
                  className={clsx(
                    "group relative text-left rounded-3xl border-2 bg-[color:var(--card)] shadow-[0_10px_0_rgba(11,18,32,0.06),0_18px_40px_rgba(11,18,32,0.10)] transition",
                    "px-3 pt-3 pb-4 min-h-[110px] sm:min-h-[120px]",
                    clickable ? "hover:-translate-y-0.5 cursor-pointer" : "cursor-not-allowed",
                    cardCls
                  )}
                  title={`Place ${spot.numeroPlace}`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="text-xs font-extrabold tracking-tight text-slate-900">
                      {spot.numeroPlace}
                    </div>
                    <div className={clsx("h-2.5 w-2.5 rounded-full", dot)} />
                  </div>

                  <div className="mt-3 flex items-center justify-center">
                    {state === 'occupied' || state === 'reserved' ? (
                      <Car className="h-10 w-10 text-slate-900" />
                    ) : (
                      <div className="h-10 w-10 rounded-2xl border-2 border-[rgba(11,18,32,0.14)] bg-white/70 flex items-center justify-center text-slate-500">
                        {getSpotIcon(spot.type)}
                      </div>
                    )}
                  </div>

                  <div className="mt-3 flex items-center justify-center">
                    <span
                      className={clsx(
                        "inline-flex items-center px-2 py-1 rounded-full text-[10px] font-extrabold leading-none whitespace-nowrap border",
                        clickable
                          ? "bg-emerald-100 text-emerald-800 border-emerald-200"
                          : "bg-red-100 text-red-700 border-red-200"
                      )}
                    >
                      {spot.statut === 'LIBRE' ? 'Libre' : 'Occupée'}
                    </span>
                  </div>

                  {clickable && upcomingLabel && (
                    <div className="mt-2 flex items-center justify-center">
                      <span className="inline-flex items-center px-2 py-1 rounded-full text-[10px] font-extrabold leading-none whitespace-nowrap border border-red-200 bg-red-50 text-red-700">
                        À {upcomingLabel}
                      </span>
                    </div>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <div className="mt-5 flex flex-wrap gap-2">
        <span className="sp-chip"><span className="h-2 w-2 rounded-full bg-emerald-500" /> Libre</span>
        <span className="sp-chip"><span className="h-2 w-2 rounded-full bg-red-500" /> Occupée</span>
        <span className="sp-chip"><span className="h-2 w-2 rounded-full bg-blue-600" /> Sélectionné</span>
      </div>
    </div>
  );
}
