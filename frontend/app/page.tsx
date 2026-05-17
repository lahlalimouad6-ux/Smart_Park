import Link from 'next/link';
import { Shield, Smartphone, MapPin, ArrowRight } from 'lucide-react';

export default function Home() {
  return (
    <div className="bg-white">
      {/* Hero Section */}
      <div className="relative isolate overflow-hidden bg-gradient-to-b from-blue-100/20">
        <div className="mx-auto max-w-7xl px-6 pb-24 pt-10 sm:pb-32 lg:flex lg:px-8 lg:py-40">
          <div className="mx-auto max-w-2xl lg:mx-0 lg:max-w-xl lg:flex-shrink-0 lg:pt-8">
            <div className="mt-24 sm:mt-32 lg:mt-16">
              <span className="rounded-full bg-blue-600/10 px-3 py-1 text-sm font-semibold leading-6 text-blue-600 ring-1 ring-inset ring-blue-600/10">
                Nouveau : Recharge Électrique incluse
              </span>
            </div>
            <h1 className="mt-10 text-4xl font-bold tracking-tight text-gray-900 sm:text-6xl">
              Garez-vous plus intelligemment avec <span className="text-blue-600">SmartPark</span>
            </h1>
            <p className="mt-6 text-lg leading-8 text-gray-600">
              Réservez votre place de parking à l&apos;avance, choisissez l&apos;emplacement exact sur le plan et accédez au parking instantanément via votre smartphone.
            </p>
            <div className="mt-10 flex items-center gap-x-6">
              <Link
                href="/parkings"
                className="rounded-xl bg-blue-600 px-8 py-4 text-lg font-bold text-white shadow-lg hover:bg-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 transition-all transform hover:scale-105"
              >
                Trouver une place
              </Link>
              <Link href="/register" className="text-sm font-bold leading-6 text-gray-900 flex items-center">
                Créer un compte <ArrowRight className="ml-2 h-4 w-4" />
              </Link>
            </div>
          </div>
          <div className="mx-auto mt-16 flex max-w-2xl sm:mt-24 lg:ml-10 lg:mr-0 lg:mt-0 lg:max-w-none lg:flex-none xl:ml-32">
            <div className="max-w-3xl flex-none sm:max-w-5xl lg:max-w-none">
              <div className="rounded-2xl bg-gray-900/5 p-2 ring-1 ring-inset ring-gray-900/10 lg:-m-4 lg:rounded-2xl lg:p-4">
                <div className="bg-white rounded-xl shadow-2xl border border-gray-100 overflow-hidden">
                   <img 
                    src="https://images.unsplash.com/photo-1506521781263-d8422e82f27a?auto=format&fit=crop&q=80&w=2070" 
                    alt="Parking Intelligent" 
                    className="w-[500px] h-[400px] object-cover"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Features Section */}
      <div className="py-24 sm:py-32 bg-gray-50">
        <div className="mx-auto max-w-7xl px-6 lg:px-8">
          <div className="mx-auto max-w-2xl lg:text-center">
            <h2 className="text-base font-semibold leading-7 text-blue-600 uppercase tracking-widest">Pourquoi nous choisir ?</h2>
            <p className="mt-2 text-3xl font-bold tracking-tight text-gray-900 sm:text-4xl">
              Tout ce dont vous avez besoin pour un stationnement sans stress
            </p>
          </div>
          <div className="mx-auto mt-16 max-w-2xl sm:mt-20 lg:mt-24 lg:max-w-none">
            <dl className="grid max-w-xl grid-cols-1 gap-x-8 gap-y-16 lg:max-w-none lg:grid-cols-3">
              <div className="flex flex-col items-center text-center">
                <dt className="flex flex-col items-center gap-y-4 text-base font-bold leading-7 text-gray-900">
                  <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-600 shadow-lg">
                    <MapPin className="h-8 w-8 text-white" />
                  </div>
                  Visual Choice
                </dt>
                <dd className="mt-4 flex flex-auto flex-col text-base leading-7 text-gray-600">
                  <p className="flex-auto">Visualisez le plan du parking en temps réel et cliquez sur la place exacte que vous souhaitez occuper.</p>
                </dd>
              </div>
              <div className="flex flex-col items-center text-center">
                <dt className="flex flex-col items-center gap-y-4 text-base font-bold leading-7 text-gray-900">
                  <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-600 shadow-lg">
                    <Smartphone className="h-8 w-8 text-white" />
                  </div>
                  Accès QR Code
                </dt>
                <dd className="mt-4 flex flex-auto flex-col text-base leading-7 text-gray-600">
                  <p className="flex-auto">Plus de tickets papier. Utilisez votre QR Code sécurisé pour entrer et sortir du parking en toute fluidité.</p>
                </dd>
              </div>
              <div className="flex flex-col items-center text-center">
                <dt className="flex flex-col items-center gap-y-4 text-base font-bold leading-7 text-gray-900">
                  <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-600 shadow-lg">
                    <Shield className="h-8 w-8 text-white" />
                  </div>
                  Paiement Sécurisé
                </dt>
                <dd className="mt-4 flex flex-auto flex-col text-base leading-7 text-gray-600">
                  <p className="flex-auto">Payez en ligne ou utilisez votre abonnement mensuel/annuel. Transactions sécurisées et transparentes.</p>
                </dd>
              </div>
            </dl>
          </div>
        </div>
      </div>
    </div>
  );
}
