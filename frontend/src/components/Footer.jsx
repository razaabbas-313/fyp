import React from 'react';
import { Camera, Shield, Lock, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';

export const Footer = () => {
  return (
    <footer className="border-t border-white/5 bg-dark-950 text-slate-400 py-12 md:py-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8 md:gap-10 mb-12">
          {/* Logo & Description */}
          <div className="md:col-span-2 space-y-3">
            <Link to="/" className="inline-flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-brand-600 via-brand-500 to-indigo-500 flex items-center justify-center shadow-md shadow-brand-500/20">
                <Camera className="w-4 h-4 text-white" />
              </div>
              <span className="text-lg font-bold tracking-tight text-white">
                FotoFetch <span className="text-brand-400 font-semibold text-xs px-2 py-0.5 rounded-full bg-brand-500/10 border border-brand-500/20">AI</span>
              </span>
            </Link>
            <p className="text-xs sm:text-sm text-slate-400 leading-relaxed max-w-sm">
              The modern event photography platform that bridges photographers and guests. Upload event galleries and let guests find all their photos in seconds.
            </p>
          </div>

          {/* Quick Links */}
          <div className="space-y-3">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-200">Product</h4>
            <ul className="space-y-2 text-xs sm:text-sm">
              <li><a href="#how-it-works" className="hover:text-white transition-colors">How It Works</a></li>
              <li><a href="#features" className="hover:text-white transition-colors">Features</a></li>
              <li><a href="#for-photographers" className="hover:text-white transition-colors">For Photographers</a></li>
              <li><Link to="/register" className="hover:text-white transition-colors">Photographer Portal</Link></li>
            </ul>
          </div>

          {/* Security & Cloud */}
          <div className="space-y-3">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-200">Privacy & Security</h4>
            <ul className="space-y-2 text-xs sm:text-sm">
              <li className="flex items-center gap-2 text-slate-400">
                <Shield className="w-3.5 h-3.5 text-emerald-400 shrink-0" /> Private Face Matching
              </li>
              <li className="flex items-center gap-2 text-slate-400">
                <Lock className="w-3.5 h-3.5 text-emerald-400 shrink-0" /> Encrypted Cloud Storage
              </li>
              <li className="flex items-center gap-2 text-slate-400">
                <Sparkles className="w-3.5 h-3.5 text-emerald-400 shrink-0" /> Ephemeral Processing
              </li>
            </ul>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className="pt-8 border-t border-white/5 flex flex-col sm:flex-row items-center justify-between text-xs text-slate-500 gap-3 text-center sm:text-left">
          <p>© {new Date().getFullYear()} FotoFetch AI. All rights reserved.</p>
          <p className="text-slate-500">
            Intelligent Event Photography Platform
          </p>
        </div>
      </div>
    </footer>
  );
};

