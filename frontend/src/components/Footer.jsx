import React from 'react';
import { Camera, Sparkles, Heart } from 'lucide-react';
import { Link } from 'react-router-dom';

export const Footer = () => {
  return (
    <footer className="border-t border-white/5 bg-dark-950 text-slate-400 py-12 md:py-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-10 mb-12">
          {/* Logo & Description */}
          <div className="md:col-span-2 space-y-4">
            <Link to="/" className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-brand-600 via-brand-500 to-indigo-400 flex items-center justify-center shadow-lg shadow-brand-500/20">
                <Camera className="w-4 h-4 text-white" />
              </div>
              <span className="text-xl font-bold tracking-tight text-white">
                FotoFetch <span className="text-brand-400 font-extrabold text-xs px-2 py-0.5 rounded-full bg-brand-500/10 border border-brand-500/20">AI</span>
              </span>
            </Link>
            <p className="text-sm text-slate-400 leading-relaxed max-w-sm">
              The AI-powered event photography platform that bridges photographers and guests. Upload thousands of photos and find yours in seconds using AWS facial recognition.
            </p>
          </div>

          {/* Quick Links */}
          <div className="space-y-3">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-200">Product</h4>
            <ul className="space-y-2 text-sm">
              <li><a href="#how-it-works" className="hover:text-white transition-colors">How It Works</a></li>
              <li><a href="#features" className="hover:text-white transition-colors">Features</a></li>
              <li><a href="#for-photographers" className="hover:text-white transition-colors">For Photographers</a></li>
              <li><Link to="/register" className="hover:text-white transition-colors">Photographer Portal</Link></li>
            </ul>
          </div>

          {/* Technology & Privacy */}
          <div className="space-y-3">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-200">Security & Cloud</h4>
            <ul className="space-y-2 text-sm">
              <li className="flex items-center gap-1.5"><span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span> AWS Rekognition Engine</li>
              <li className="flex items-center gap-1.5"><span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span> S3 Private Encrypted Storage</li>
              <li className="flex items-center gap-1.5"><span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span> Temporary Selfie Processing</li>
              <li className="flex items-center gap-1.5"><span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span> Isolated Event Collections</li>
            </ul>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className="pt-8 border-t border-white/5 flex flex-col sm:flex-row items-center justify-between text-xs text-slate-500 gap-4">
          <p>© {new Date().getFullYear()} FotoFetch AI. All rights reserved. Final Year Project MVP.</p>
          <p className="flex items-center gap-1">
            Engineered with Spring Boot 3, React & AWS Cloud
          </p>
        </div>
      </div>
    </footer>
  );
};
