import axios, { AxiosHeaders } from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
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
