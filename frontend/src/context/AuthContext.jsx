import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi } from '../api/authApi';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('fotofetch_user');
    return saved ? JSON.parse(saved) : null;
  });
  const [token, setToken] = useState(() => localStorage.getItem('fotofetch_token'));
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (token) {
      authApi.getCurrentUser()
        .then((res) => {
          if (res.data) {
            setUser(res.data);
            localStorage.setItem('fotofetch_user', JSON.stringify(res.data));
          }
        })
        .catch(() => {
          logout();
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, [token]);

  const login = async (email, password) => {
    const res = await authApi.login({ email, password });
    const { token: jwt, ...userData } = res.data;
    localStorage.setItem('fotofetch_token', jwt);
    localStorage.setItem('fotofetch_user', JSON.stringify(userData));
    setToken(jwt);
    setUser(userData);
    return res.data;
  };

  const register = async (name, email, password) => {
    const res = await authApi.register({ name, email, password });
    const { token: jwt, ...userData } = res.data;
    localStorage.setItem('fotofetch_token', jwt);
    localStorage.setItem('fotofetch_user', JSON.stringify(userData));
    setToken(jwt);
    setUser(userData);
    return res.data;
  };

  const logout = () => {
    localStorage.removeItem('fotofetch_token');
    localStorage.removeItem('fotofetch_user');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, isAuthenticated: !!token, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
