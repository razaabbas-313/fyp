import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { Camera, Sparkles, LogOut, LayoutDashboard, Menu, X, User } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export const Navbar = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  const isLandingPage = location.pathname === '/';

  const scrollToSection = (id) => {
    if (!isLandingPage) {
      navigate(`/#${id}`);
      return;
    }
    const element = document.getElementById(id);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
    }
    setMobileMenuOpen(false);
  };

  return (
    <nav className="fixed top-0 left-0 right-0 z-40 bg-dark-950/80 backdrop-blur-md border-b border-white/5">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-20">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2.5 group">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-brand-600 via-brand-500 to-indigo-400 flex items-center justify-center shadow-lg shadow-brand-500/25 group-hover:scale-105 transition-transform duration-200">
              <Camera className="w-5 h-5 text-white" />
            </div>
            <div className="flex flex-col">
              <span className="text-xl font-bold tracking-tight text-white flex items-center gap-1.5">
                FotoFetch <span className="text-brand-400 font-extrabold text-xs px-2 py-0.5 rounded-full bg-brand-500/10 border border-brand-500/20">AI</span>
              </span>
              <span className="text-[10px] text-slate-400 font-medium tracking-wide">YOUR MOMENTS. INSTANTLY.</span>
            </div>
          </Link>

          {/* Desktop Navigation Links */}
          <div className="hidden md:flex items-center gap-8 text-sm font-medium text-slate-300">
            <button
              onClick={() => scrollToSection('hero')}
              className="hover:text-white transition-colors"
            >
              Home
            </button>
            <button
              onClick={() => scrollToSection('how-it-works')}
              className="hover:text-white transition-colors"
            >
              How It Works
            </button>
            <button
              onClick={() => scrollToSection('features')}
              className="hover:text-white transition-colors"
            >
              Features
            </button>
            <button
              onClick={() => scrollToSection('for-photographers')}
              className="hover:text-white transition-colors"
            >
              For Photographers
            </button>
          </div>

          {/* Desktop Right Actions */}
          <div className="hidden md:flex items-center gap-4">
            {isAuthenticated ? (
              <div className="flex items-center gap-3">
                <Link
                  to="/dashboard"
                  className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium bg-white/5 hover:bg-white/10 text-white border border-white/10 transition-all duration-200"
                >
                  <LayoutDashboard className="w-4 h-4 text-brand-400" />
                  Dashboard
                </Link>
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-dark-900 border border-white/5 text-xs text-slate-300">
                  <User className="w-3.5 h-3.5 text-brand-400" />
                  <span>{user?.name || user?.email}</span>
                </div>
                <button
                  onClick={handleLogout}
                  title="Sign out"
                  className="p-2 text-slate-400 hover:text-rose-400 transition-colors"
                >
                  <LogOut className="w-4 h-4" />
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-3">
                <Link
                  to="/login"
                  className="text-sm font-medium text-slate-300 hover:text-white px-3 py-2 transition-colors"
                >
                  Log in
                </Link>
                <Link
                  to="/register"
                  className="relative group inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-lg shadow-brand-500/25 transition-all duration-200 hover:scale-[1.02] active:scale-[0.98]"
                >
                  <Sparkles className="w-4 h-4 text-brand-200" />
                  Get Started
                </Link>
              </div>
            )}
          </div>

          {/* Mobile hamburger button */}
          <div className="md:hidden flex items-center">
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="p-2 text-slate-400 hover:text-white"
            >
              {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
            </button>
          </div>
        </div>

        {/* Mobile menu dropdown */}
        {mobileMenuOpen && (
          <div className="md:hidden py-4 border-t border-white/5 space-y-3">
            <button
              onClick={() => scrollToSection('hero')}
              className="block w-full text-left px-3 py-2 text-base font-medium text-slate-300 hover:text-white"
            >
              Home
            </button>
            <button
              onClick={() => scrollToSection('how-it-works')}
              className="block w-full text-left px-3 py-2 text-base font-medium text-slate-300 hover:text-white"
            >
              How It Works
            </button>
            <button
              onClick={() => scrollToSection('features')}
              className="block w-full text-left px-3 py-2 text-base font-medium text-slate-300 hover:text-white"
            >
              Features
            </button>
            <button
              onClick={() => scrollToSection('for-photographers')}
              className="block w-full text-left px-3 py-2 text-base font-medium text-slate-300 hover:text-white"
            >
              For Photographers
            </button>

            <div className="pt-3 border-t border-white/5 space-y-2">
              {isAuthenticated ? (
                <>
                  <Link
                    to="/dashboard"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-2 px-3 py-2 text-base font-medium text-brand-400 hover:text-brand-300"
                  >
                    <LayoutDashboard className="w-4 h-4" />
                    Dashboard
                  </Link>
                  <button
                    onClick={() => {
                      handleLogout();
                      setMobileMenuOpen(false);
                    }}
                    className="flex items-center gap-2 w-full text-left px-3 py-2 text-base font-medium text-rose-400"
                  >
                    <LogOut className="w-4 h-4" />
                    Sign out
                  </button>
                </>
              ) : (
                <>
                  <Link
                    to="/login"
                    onClick={() => setMobileMenuOpen(false)}
                    className="block px-3 py-2 text-base font-medium text-slate-300 hover:text-white"
                  >
                    Log in
                  </Link>
                  <Link
                    to="/register"
                    onClick={() => setMobileMenuOpen(false)}
                    className="block text-center px-4 py-2.5 rounded-xl text-base font-medium text-white bg-gradient-to-r from-brand-600 to-indigo-600"
                  >
                    Get Started
                  </Link>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </nav>
  );
};
