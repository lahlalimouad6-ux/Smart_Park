'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import api from '@/lib/api';
import { Lock, Mail, AlertCircle } from 'lucide-react';

function extractErrorMessage(err: unknown): string | null {
  if (typeof err !== 'object' || err === null) return null;
  const response = (err as { response?: unknown }).response;
  if (typeof response !== 'object' || response === null) return null;
  const data = (response as { data?: unknown }).data;
  if (typeof data === 'string') return data;
  if (typeof data === 'object' && data !== null && 'message' in data) {
    const message = (data as { message?: unknown }).message;
    if (typeof message === 'string') return message;
  }
  return null;
}

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await api.post('/auth/signin', { email, password });
      const userData = response.data;
      localStorage.setItem('user', JSON.stringify(userData));
      
      // Redirection basée sur le rôle
      if (userData.roles && (userData.roles.includes('ADMIN') || userData.roles.includes('ROLE_ADMIN'))) {
        window.location.href = '/admin';
      } else {
        window.location.href = '/parkings';
      }
    } catch (err: unknown) {
      console.error('Login error:', err);
      setError(extractErrorMessage(err) || 'Email ou mot de passe incorrect');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-64px)] flex items-center justify-center px-4 py-10">
      <div className="sp-ticket max-w-md w-full p-10">
        <div className="text-center">
          <div className="flex justify-center">
            <div className="bg-white p-4 rounded-3xl border border-[rgba(15,23,42,0.12)]">
              <Image
                src="/logo.jpeg"
                alt="SmartPark"
                width={120}
                height={120}
                priority
                style={{ width: 'auto', height: 'auto' }}
                className="rounded-xl object-contain"
              />
            </div>
          </div>
          <div className="mt-5">
            <div className="text-2xl font-extrabold text-gray-900 tracking-tight">SmartPark</div>
            <h2 className="mt-1 text-lg font-bold text-gray-700">Connexion</h2>
          </div>
          <p className="mt-2 text-sm text-gray-600">
            Ou{' '}
            <Link href="/register" className="font-medium text-blue-600 hover:text-blue-500 underline decoration-blue-200 underline-offset-4">
              créez un compte gratuitement
            </Link>
          </p>
        </div>

        {error && (
          <div className="bg-red-50 border-l-4 border-red-400 p-4 rounded flex items-center">
            <AlertCircle className="h-5 w-5 text-red-400 mr-2" />
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}

        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          <div className="space-y-4">
            <div>
              <label htmlFor="email-address" className="block text-sm font-medium text-gray-700 mb-1">
                Adresse Email
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Mail className="h-5 w-5 text-gray-400" />
                </div>
                <input
                  id="email-address"
                  name="email"
                  type="email"
                  required
                  className="appearance-none block w-full pl-10 pr-3 py-3 border border-gray-300 rounded-xl placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="exemple@email.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
            </div>
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
                Mot de passe
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Lock className="h-5 w-5 text-gray-400" />
                </div>
                <input
                  id="password"
                  name="password"
                  type="password"
                  required
                  className="appearance-none block w-full pl-10 pr-3 py-3 border border-gray-300 rounded-xl placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
            </div>
          </div>

          <div>
            <button
              type="submit"
              disabled={loading}
              className="w-full sp-btn sp-btn-primary h-12 disabled:opacity-50"
            >
              {loading ? 'Connexion...' : 'Se connecter'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
