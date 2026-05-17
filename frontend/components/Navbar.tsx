'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { LogOut, User, Car, Map as MapIcon, Calendar, Shield, Users, Building2 } from 'lucide-react';
import { User as AppUser } from '@/types';
import api from '@/lib/api';

export default function Navbar() {
  const [user, setUser] = useState<AppUser | null>(null);
  const router = useRouter();
  const isAdmin = Boolean(user?.roles?.includes('ADMIN') || user?.roles?.includes('ROLE_ADMIN'));
  const isConductor = Boolean(user?.roles?.includes('CONDUCTEUR') || user?.roles?.includes('ROLE_CONDUCTEUR'));

  useEffect(() => {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      setTimeout(() => setUser(JSON.parse(userStr)), 0);
    }
  }, []);

  useEffect(() => {
    if (!user) return;
    api.get('/auth/me')
      .then((res) => {
        const roles = res.data?.roles;
        if (Array.isArray(roles)) {
          const nextUser = { ...user, roles };
          setUser(nextUser);
          localStorage.setItem('user', JSON.stringify(nextUser));
        }
      })
      .catch(() => {
        localStorage.removeItem('user');
        setUser(null);
      });
  }, [user?.token]);

  const handleLogout = () => {
    localStorage.removeItem('user');
    setUser(null);
    router.push('/login');
  };

  return (
    <nav className="bg-white shadow-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex">
            <Link href="/" className="flex-shrink-0 flex items-center">
              <Car className="h-8 w-8 text-blue-600 mr-2" />
              <span className="font-bold text-xl text-gray-800">SmartPark</span>
            </Link>
            <div className="hidden sm:ml-6 sm:flex sm:space-x-8">
              {(!user || isConductor) && !isAdmin && (
                <Link href="/parkings" className="text-gray-600 hover:text-blue-600 px-3 py-2 rounded-md text-sm font-medium flex items-center">
                  <MapIcon className="h-4 w-4 mr-1" />
                  Parkings
                </Link>
              )}
              {user && isConductor && !isAdmin && (
                <Link href="/reservations" className="text-gray-600 hover:text-blue-600 px-3 py-2 rounded-md text-sm font-medium flex items-center">
                  <Calendar className="h-4 w-4 mr-1" />
                  Mes Réservations
                </Link>
              )}
              {user && isAdmin && (
                <>
                  <Link href="/admin" className="text-red-600 hover:text-red-700 px-3 py-2 rounded-md text-sm font-bold flex items-center">
                    <Shield className="h-4 w-4 mr-1" />
                    Dashboard Admin
                  </Link>
                  <Link href="/admin/parkings" className="text-gray-600 hover:text-blue-600 px-3 py-2 rounded-md text-sm font-medium flex items-center">
                    <Building2 className="h-4 w-4 mr-1" />
                    Parkings
                  </Link>
                  <Link href="/admin/reservations" className="text-gray-600 hover:text-blue-600 px-3 py-2 rounded-md text-sm font-medium flex items-center">
                    <Users className="h-4 w-4 mr-1" />
                    Utilisateurs
                  </Link>
                </>
              )}
            </div>
          </div>
          <div className="flex items-center">
            {user ? (
              <div className="flex items-center space-x-4">
                <div className="flex flex-col items-end mr-2">
                  <span className="text-xs font-bold text-blue-600 uppercase">{user.roles[0]}</span>
                  <span className="text-sm text-gray-700 flex items-center">
                    <User className="h-3 w-3 mr-1" />
                    {user.email}
                  </span>
                </div>
                <button
                  onClick={handleLogout}
                  className="bg-red-500 hover:bg-red-600 text-white px-3 py-1.5 rounded-md text-sm font-medium flex items-center transition"
                >
                  <LogOut className="h-4 w-4 mr-1" />
                  Déconnexion
                </button>
              </div>
            ) : (
              <div className="space-x-2">
                <Link href="/login" className="text-blue-600 hover:text-blue-700 px-3 py-2 text-sm font-medium">Connexion</Link>
                <Link href="/register" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium transition">Inscription</Link>
              </div>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
