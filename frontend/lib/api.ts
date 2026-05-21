import axios, { AxiosHeaders } from 'axios';

const resolvedBackendOrigin = (() => {
  const env = process.env.NEXT_PUBLIC_BACKEND_ORIGIN;
  if (typeof env === 'string' && env.trim().length > 0) return env.trim().replace(/\/$/, '');
  if (typeof window !== 'undefined' && window.location?.hostname) {
    const host = window.location.hostname;
    if (host && host !== 'localhost' && host !== '127.0.0.1') return `http://${host}:8080`;
  }
  return 'http://localhost:8080';
})();

const api = axios.create({
  baseURL: `${resolvedBackendOrigin}/api`,
});

api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      try {
        const user = JSON.parse(userStr);
        const token = user?.token || user?.accessToken || user?.jwt;
        if (typeof token === 'string' && token.length > 0) {
          const headers = AxiosHeaders.from(config.headers);
          headers.set('Authorization', `Bearer ${token}`);
          config.headers = headers;
        }
      } catch {}
    }
  }
  return config;
});

export default api;
