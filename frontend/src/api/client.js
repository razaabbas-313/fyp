import axios from 'axios';

const rawApiUrl = import.meta.env.VITE_API_URL;
let resolvedBaseUrl = '/api';

if (rawApiUrl && rawApiUrl.trim() !== '') {
  const trimmed = rawApiUrl.trim().replace(/\/+$/, '');
  resolvedBaseUrl = trimmed.endsWith('/api') ? trimmed : `${trimmed}/api`;
} else if (typeof window !== 'undefined' && window.location.hostname !== 'localhost') {
  console.warn(
    'FotoFetch AI: VITE_API_URL is not set in Vercel environment variables. Requests will default to /api. ' +
    'Please set VITE_API_URL=https://<your-render-backend-url> in Vercel Project Settings.'
  );
}

const api = axios.create({
  baseURL: resolvedBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to attach JWT token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('fotofetch_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to handle errors globally
api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      // If unauthorized on a protected route, remove token
      const isAuthEndpoint = error.config.url.includes('/auth/');
      if (!isAuthEndpoint && window.location.pathname.startsWith('/dashboard')) {
        localStorage.removeItem('fotofetch_token');
        localStorage.removeItem('fotofetch_user');
        window.location.href = '/login';
      }
    }
    const message = error.response?.data?.message || error.message || 'Something went wrong';
    return Promise.reject(new Error(message));
  }
);

export default api;
