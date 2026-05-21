 'use client';

import Link from 'next/link';
import { Shield, Smartphone, MapPin, QrCode, Sparkles, Clock3 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

export default function Home() {
  const images = useMemo(
    () => [
      {
        src: 'https://images.unsplash.com/photo-1506521781263-d8422e82f27a?auto=format&fit=crop&w=1600&q=70',
        alt: 'Parking extérieur',
        label: 'Parkings en ville'
      },
      {
        src: 'https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1600&q=70',
        alt: 'Voiture sur route',
        label: 'Navigation & trajet'
      },
      {
        src: 'https://images.unsplash.com/photo-1493238792000-8113da705763?auto=format&fit=crop&w=1600&q=70',
        alt: 'Voiture et lumière',
        label: 'Expérience fluide'
      }
    ],
    []
  );
  const [activeIndex, setActiveIndex] = useState(0);

  useEffect(() => {
    const id = window.setInterval(() => {
      setActiveIndex((i) => (i + 1) % images.length);
    }, 3000);
    return () => window.clearInterval(id);
  }, [images.length]);

  return (
    <div>
      {/* Hero Section */}
      <div className="sp-container pt-12 pb-10 lg:pt-16 lg:pb-14">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-10 items-center">
          <div>
            <div className="flex flex-wrap gap-2">
              <span className="sp-chip">
                <Sparkles className="h-3.5 w-3.5 text-blue-600" />
                Plan interactif
              </span>
              <span className="sp-chip">
                <QrCode className="h-3.5 w-3.5 text-emerald-600" />
                Accès QR
              </span>
              <span className="sp-chip">
                <Clock3 className="h-3.5 w-3.5 text-slate-500" />
                Temps réel
              </span>
            </div>
            <h1 className="mt-6 text-4xl sm:text-5xl font-extrabold tracking-tight text-slate-900">
              Réservez, scannez, garez-vous.
            </h1>
            <p className="mt-4 text-lg text-slate-600 leading-relaxed">
              Un parcours simple : choisissez un parking, sélectionnez une place sur le plan, puis accédez avec un QR Code sécurisé.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link href="/parkings" className="sp-btn sp-btn-primary">
                Trouver un parking
              </Link>
              <Link href="/register" className="sp-btn">
                Créer un compte
              </Link>
            </div>
          </div>
          <div className="sp-ticket">
            <div className="p-5">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="text-sm font-extrabold text-slate-900 tracking-tight">
                    Aperçu SmartPark
                  </div>
                  <div className="mt-1 text-sm text-slate-600">
                    Parkings • Voitures • Navigation
                  </div>
                </div>
                <div className="sp-chip">
                  <span className="inline-block h-2 w-2 rounded-full bg-emerald-500" />
                  Live
                </div>
              </div>

              <div className="mt-5 overflow-hidden rounded-[28px] border-2 border-[rgba(11,18,32,0.16)] bg-black relative">
                <div className="relative aspect-[16/10]">
                  {images.map((img, idx) => (
                    <div
                      key={img.src}
                      role="img"
                      aria-label={img.alt}
                      className={`absolute inset-0 h-full w-full bg-center bg-cover transition-opacity duration-700 ${
                        idx === activeIndex ? 'opacity-100' : 'opacity-0'
                      }`}
                      style={{ backgroundImage: `url(${img.src})` }}
                    />
                  ))}

                  <div className="absolute inset-0 bg-gradient-to-t from-black/55 via-black/10 to-black/0" />

                  <div className="absolute left-4 right-4 bottom-4 flex items-end justify-between gap-3">
                    <div className="min-w-0">
                      <div className="text-white font-extrabold tracking-tight truncate">
                        {images[activeIndex]?.label}
                      </div>
                      
                    </div>
                    <div className="flex items-center gap-1.5">
                      {images.map((_, idx) => (
                        <button
                          key={idx}
                          type="button"
                          onClick={() => setActiveIndex(idx)}
                          className={`h-2.5 rounded-full transition-all ${
                            idx === activeIndex ? 'w-7 bg-white' : 'w-2.5 bg-white/45 hover:bg-white/70'
                          }`}
                          aria-label={`Aller à l'image ${idx + 1}`}
                        />
                      ))}
                    </div>
                  </div>
                </div>
              </div>

              <div className="mt-5 grid grid-cols-3 gap-2">
                <span className="sp-chip justify-center">
                  <MapPin className="h-3.5 w-3.5 text-blue-700" />
                  Carte
                </span>
                <span className="sp-chip justify-center">
                  <Smartphone className="h-3.5 w-3.5 text-emerald-700" />
                  Mobile
                </span>
                <span className="sp-chip justify-center">
                  <QrCode className="h-3.5 w-3.5 text-slate-700" />
                  QR
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Features Section */}
      <div className="sp-container pb-14 lg:pb-20">
          <div className="mx-auto max-w-2xl lg:text-center">
            <h2 className="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl">Une expérience claire</h2>
            <p className="mt-3 text-slate-600">Un design “produit” centré sur la carte, l’état des places et l’accès QR.</p>
          </div>
        <div className="mt-10 grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="sp-card p-6">
            <div className="h-12 w-12 rounded-2xl bg-blue-600 text-white flex items-center justify-center">
              <MapPin className="h-6 w-6" />
            </div>
            <div className="mt-4 font-extrabold text-slate-900">Choix visuel</div>
            <div className="mt-2 text-slate-600">Plan interactif + statuts (libre/occupé/réservé) pour choisir rapidement.</div>
          </div>
          <div className="sp-card p-6">
            <div className="h-12 w-12 rounded-2xl bg-emerald-600 text-white flex items-center justify-center">
              <Smartphone className="h-6 w-6" />
            </div>
            <div className="mt-4 font-extrabold text-slate-900">Accès QR</div>
            <div className="mt-2 text-slate-600">Un pass lisible, scannable et consultable sur téléphone (infos conducteur + parking).</div>
          </div>
          <div className="sp-card p-6">
            <div className="h-12 w-12 rounded-2xl bg-slate-900 text-white flex items-center justify-center">
              <Shield className="h-6 w-6" />
            </div>
            <div className="mt-4 font-extrabold text-slate-900">Sécurisé</div>
            <div className="mt-2 text-slate-600">JWT + rôles (ADMIN/CONDUCTEUR) et endpoints publics limités (QR).</div>
          </div>
        </div>
      </div>
    </div>
  );
}
