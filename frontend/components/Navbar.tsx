'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { LogOut, User, Map as MapIcon, Calendar, Shield, Users, Building2 } from 'lucide-react';
import { User as AppUser } from '@/types';
import api from '@/lib/api';

export default function Navbar() {
  const [user, setUser] = useState<AppUser | null>(null);
  const router = useRouter();
  const isAdmin = Boolean(user?.roles?.includes('ADMIN') || user?.roles?.includes('ROLE_ADMIN'));
  const isConductor = Boolean(user?.roles?.includes('CONDUCTEUR') || user?.roles?.includes('ROLE_CONDUCTEUR'));
  const token = user?.token;

  useEffect(() => {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      setTimeout(() => setUser(JSON.parse(userStr)), 0);
    }
  }, []);

  useEffect(() => {
    if (!token) return;
    api.get('/auth/me')
      .then((res) => {
        const roles = res.data?.roles;
        if (!Array.isArray(roles)) return;
        setUser((prev) => {
          if (!prev) return prev;
          const nextUser = { ...prev, roles };
          localStorage.setItem('user', JSON.stringify(nextUser));
          return nextUser;
        });
      })
      .catch(() => {
        localStorage.removeItem('user');
        setUser(null);
      });
  }, [token]);

  const handleLogout = () => {
    localStorage.removeItem('user');
    setUser(null);
    router.push('/login');
  };

  return (
    <nav className="sticky top-0 z-50 border-b-2 border-[rgba(11,18,32,0.16)] bg-[color:var(--card)]/75 backdrop-blur">
      <div className="sp-container">
        <div className="flex justify-between h-16">
          <div className="flex">
            <Link href="/" className="flex-shrink-0 flex items-center">
              <Image
                src="/logo.jpeg"
                alt="SmartPark"
                width={40}
                height={40}
                style={{ width: 'auto', height: 'auto' }}
                className="mr-3 rounded-xl"
              />
              <span className="font-extrabold tracking-tight text-slate-900">
                Smart<span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-700 to-emerald-500">Park</span>
              </span>
            </Link>
            <div className="hidden sm:ml-6 sm:flex sm:space-x-8">
              {(!user || isConductor) && !isAdmin && (
                <Link href="/parkings" className="text-slate-700 hover:text-slate-900 px-3 py-2 rounded-lg text-sm font-semibold flex items-center">
                  <MapIcon className="h-4 w-4 mr-1 text-slate-400" />
                  Parkings
                </Link>
              )}
              {user && isConductor && !isAdmin && (
                <Link href="/reservations" className="text-slate-700 hover:text-slate-900 px-3 py-2 rounded-lg text-sm font-semibold flex items-center">
                  <Calendar className="h-4 w-4 mr-1 text-slate-400" />
                  Mes Réservations
                </Link>
              )}
              {user && isAdmin && (
                <>
                  <Link href="/admin" className="text-slate-700 hover:text-slate-900 px-3 py-2 rounded-lg text-sm font-semibold flex items-center">
                    <Shield className="h-4 w-4 mr-1 text-slate-400" />
                    Dashboard Admin
                  </Link>
                  <Link href="/admin/parkings" className="text-slate-700 hover:text-slate-900 px-3 py-2 rounded-lg text-sm font-semibold flex items-center">
                    <Building2 className="h-4 w-4 mr-1 text-slate-400" />
                    Parkings
                  </Link>
                  <Link href="/admin/reservations" className="text-slate-700 hover:text-slate-900 px-3 py-2 rounded-lg text-sm font-semibold flex items-center">
                    <Users className="h-4 w-4 mr-1 text-slate-400" />
                    Réservations
                  </Link>
                </>
              )}
            </div>
          </div>
          <div className="flex items-center">
            {user ? (
              <div className="flex items-center space-x-4">
                <div className="flex flex-col items-end mr-2">
                  <span className="text-[11px] font-semibold text-slate-500 uppercase">{user.roles[0]}</span>
                  <span className="text-sm text-slate-700 flex items-center">
                    <User className="h-3 w-3 mr-1 text-slate-400" />
                    {user.email}
                  </span>
                </div>
                <button
                  onClick={handleLogout}
                  className="sp-btn"
                >
                  <LogOut className="h-4 w-4 mr-1" />
                  Déconnexion
                </button>
              </div>
            ) : (
              <div className="space-x-2">
                <Link href="/login" className="sp-btn">Connexion</Link>
                <Link href="/register" className="sp-btn sp-btn-primary">Inscription</Link>
              </div>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
