import React from 'react';
import { Link } from 'react-router-dom';
import {
  Sparkles,
  Camera,
  UploadCloud,
  UserCheck,
  Search,
  Download,
  ShieldCheck,
  Zap,
  ArrowRight,
  FolderArchive,
  Lock,
  CheckCircle2,
  Clock,
  Share2
} from 'lucide-react';
import { Navbar } from '../components/Navbar';
import { Footer } from '../components/Footer';

export const LandingPage = () => {
  return (
    <div className="min-h-screen flex flex-col bg-dark-950 text-slate-100">
      <Navbar />

      {/* Hero Section */}
      <section id="hero" className="relative pt-28 pb-16 sm:pt-36 sm:pb-24 md:pt-40 md:pb-28 overflow-hidden">
        {/* Subtle Ambient Glow */}
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[300px] bg-brand-600/10 blur-[120px] rounded-full pointer-events-none" />

        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
          <div className="text-center max-w-3xl mx-auto space-y-5">
            {/* Badge */}
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/5 border border-white/10 text-xs font-medium text-brand-300">
              <Sparkles className="w-3.5 h-3.5 text-brand-400" />
              <span>AI-Powered Event Photography</span>
            </div>

            {/* Headline */}
            <h1 className="text-3xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-white leading-[1.15]">
              Find Every Moment.{' '}
              <span className="bg-gradient-to-r from-brand-400 via-indigo-300 to-purple-400 bg-clip-text text-transparent">
                Instantly.
              </span>
            </h1>

            {/* Subtitle */}
            <p className="text-sm sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
              No more scrolling through thousands of event photos. FotoFetch matches guests to their pictures in seconds with a simple selfie.
            </p>

            {/* CTAs */}
            <div className="flex flex-col sm:flex-row items-center justify-center gap-3 pt-3">
              <Link
                to="/register"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-7 py-3 rounded-xl text-sm sm:text-base font-semibold text-white bg-gradient-to-r from-brand-600 via-brand-500 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-md shadow-brand-500/20 transition-all hover:scale-[1.02] active:scale-[0.98]"
              >
                <Sparkles className="w-4 h-4 text-brand-200" />
                Start as Photographer
                <ArrowRight className="w-4 h-4 text-brand-200" />
              </Link>
              <a
                href="#how-it-works"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-6 py-3 rounded-xl text-sm sm:text-base font-medium text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10 transition-colors"
              >
                How It Works
              </a>
            </div>
          </div>

          {/* Hero Visual Mockup */}
          <div className="mt-12 sm:mt-16 max-w-4xl mx-auto">
            <div className="relative rounded-2xl glass-panel p-2 sm:p-4 shadow-2xl border border-white/10">
              <div className="rounded-xl overflow-hidden bg-dark-900 border border-white/5 p-5 sm:p-6">
                <div className="flex items-center justify-between pb-4 border-b border-white/5">
                  <div className="flex items-center gap-2">
                    <div className="w-2.5 h-2.5 rounded-full bg-rose-500/80" />
                    <div className="w-2.5 h-2.5 rounded-full bg-amber-500/80" />
                    <div className="w-2.5 h-2.5 rounded-full bg-emerald-500/80" />
                    <span className="text-xs text-slate-400 ml-2 font-medium">FotoFetch Gallery Engine</span>
                  </div>
                  <div className="text-xs text-emerald-400 flex items-center gap-1.5 font-medium">
                    <span className="w-2 h-2 rounded-full bg-emerald-400" />
                    Live & Connected
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-5">
                  {/* Step 1 Visual Card */}
                  <div className="p-4 rounded-xl bg-white/[0.03] border border-white/5 space-y-2">
                    <div className="w-9 h-9 rounded-lg bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400">
                      <UploadCloud className="w-4 h-4" />
                    </div>
                    <h4 className="text-sm font-semibold text-white">1. Fast Photo Upload</h4>
                    <p className="text-xs text-slate-400">Bulk upload event photos with automatic thumbnails.</p>
                  </div>

                  {/* Step 2 Visual Card */}
                  <div className="p-4 rounded-xl bg-white/[0.03] border border-white/5 space-y-2">
                    <div className="w-9 h-9 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
                      <UserCheck className="w-4 h-4" />
                    </div>
                    <h4 className="text-sm font-semibold text-white">2. Guest Selfie</h4>
                    <p className="text-xs text-slate-400">Guests scan the QR code and snap a selfie on any phone.</p>
                  </div>

                  {/* Step 3 Visual Card */}
                  <div className="p-4 rounded-xl bg-white/[0.03] border border-white/5 space-y-2">
                    <div className="w-9 h-9 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                      <Sparkles className="w-4 h-4" />
                    </div>
                    <h4 className="text-sm font-semibold text-white">3. Instant Matches</h4>
                    <p className="text-xs text-slate-400">Accurate facial matching delivers private galleries.</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* How It Works Section */}
      <section id="how-it-works" className="py-20 bg-dark-900/60 border-y border-white/5 relative">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-12 sm:mb-16 space-y-2.5">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-brand-400">Simple Workflow</h2>
            <p className="text-2xl sm:text-3xl font-bold text-white tracking-tight">How FotoFetch Works</p>
            <p className="text-xs sm:text-sm text-slate-400">
              Transforming how event memories are delivered in three seamless steps.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Step 1 */}
            <div className="glass-card p-6 sm:p-8 rounded-2xl relative">
              <span className="text-4xl font-extrabold text-white/5 absolute top-5 right-5">01</span>
              <div className="w-11 h-11 rounded-xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400 mb-5">
                <UploadCloud className="w-5 h-5" />
              </div>
              <h3 className="text-lg font-bold text-white mb-2">Photographer Uploads</h3>
              <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                Create an event gallery and bulk upload high-resolution photos directly to secure cloud storage.
              </p>
            </div>

            {/* Step 2 */}
            <div className="glass-card p-6 sm:p-8 rounded-2xl relative">
              <span className="text-4xl font-extrabold text-white/5 absolute top-5 right-5">02</span>
              <div className="w-11 h-11 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 mb-5">
                <Camera className="w-5 h-5" />
              </div>
              <h3 className="text-lg font-bold text-white mb-2">Guest Scans & Snaps</h3>
              <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                Guests scan the event QR code or open the link. No app download or account creation needed.
              </p>
            </div>

            {/* Step 3 */}
            <div className="glass-card p-6 sm:p-8 rounded-2xl relative">
              <span className="text-4xl font-extrabold text-white/5 absolute top-5 right-5">03</span>
              <div className="w-11 h-11 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 mb-5">
                <Sparkles className="w-5 h-5" />
              </div>
              <h3 className="text-lg font-bold text-white mb-2">Instant Matches Found</h3>
              <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                Matching photos appear instantly. Guests can download full-resolution pictures individually or as a ZIP.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="features" className="py-20 relative">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-12 sm:mb-16 space-y-2.5">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-brand-400">Core Features</h2>
            <p className="text-2xl sm:text-3xl font-bold text-white tracking-tight">Built for Modern Events</p>
            <p className="text-xs sm:text-sm text-slate-400">
              Designed for reliability, privacy, and lightning-fast discovery.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            <div className="glass-card p-5 sm:p-6 rounded-2xl space-y-2.5">
              <div className="w-10 h-10 rounded-xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400">
                <Search className="w-5 h-5" />
              </div>
              <h4 className="text-base font-semibold text-white">Intelligent Face Search</h4>
              <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                Deep neural recognition matches guests across candid shots, angles, and group photos.
              </p>
            </div>

            <div className="glass-card p-5 sm:p-6 rounded-2xl space-y-2.5">
              <div className="w-10 h-10 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
                <Zap className="w-5 h-5" />
              </div>
              <h4 className="text-base font-semibold text-white">Fast Direct Uploads</h4>
              <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                Direct-to-cloud uploads ensure thousands of photos transfer quickly without server bottlenecks.
              </p>
            </div>

            <div className="glass-card p-5 sm:p-6 rounded-2xl space-y-2.5">
              <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                <Download className="w-5 h-5" />
              </div>
              <h4 className="text-base font-semibold text-white">Full-Resolution Downloads</h4>
              <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                Preserves uncompressed image quality so guests receive crisp original photographs.
              </p>
            </div>

            <div className="glass-card p-5 sm:p-6 rounded-2xl space-y-2.5">
              <div className="w-10 h-10 rounded-xl bg-purple-500/10 border border-purple-500/20 flex items-center justify-center text-purple-400">
                <ShieldCheck className="w-5 h-5" />
              </div>
              <h4 className="text-base font-semibold text-white">Private & Encrypted</h4>
              <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                Galleries are private and secure. All downloads require temporary authorized sessions.
              </p>
            </div>

            <div className="glass-card p-5 sm:p-6 rounded-2xl space-y-2.5">
              <div className="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400">
                <Lock className="w-5 h-5" />
              </div>
              <h4 className="text-base font-semibold text-white">Event Isolation</h4>
              <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                Each event maintains its own dedicated index. Photos are never shared across events.
              </p>
            </div>

            <div className="glass-card p-5 sm:p-6 rounded-2xl space-y-2.5">
              <div className="w-10 h-10 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400">
                <FolderArchive className="w-5 h-5" />
              </div>
              <h4 className="text-base font-semibold text-white">One-Click ZIP Bundle</h4>
              <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                Guests can package all their matched photos into a single ZIP archive with one click.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* For Photographers Section */}
      <section id="for-photographers" className="py-20 bg-dark-900/60 border-t border-white/5 relative">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-10 items-center">
            <div className="space-y-5">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand-500/10 border border-brand-500/20 text-xs font-medium text-brand-300">
                <Camera className="w-3.5 h-3.5" />
                For Event Photographers
              </div>
              <h2 className="text-2xl sm:text-4xl font-bold text-white tracking-tight">
                Spend Less Time Delivering Photos, More Time Shooting.
              </h2>
              <p className="text-xs sm:text-base text-slate-300 leading-relaxed">
                Instead of dumping thousands of unsorted photos into shared drives where guests struggle to find themselves, FotoFetch automates discovery.
              </p>

              <div className="space-y-3 pt-1">
                <div className="flex items-start gap-3">
                  <div className="w-5 h-5 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 shrink-0 mt-0.5">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                  </div>
                  <div>
                    <h5 className="text-xs sm:text-sm font-semibold text-white">Delight Event Guests</h5>
                    <p className="text-xs text-slate-400">Provide an unforgettable modern experience for weddings, galas, and corporate events.</p>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <div className="w-5 h-5 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 shrink-0 mt-0.5">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                  </div>
                  <div>
                    <h5 className="text-xs sm:text-sm font-semibold text-white">Print QR Codes for the Venue</h5>
                    <p className="text-xs text-slate-400">Place QR codes on table cards so guests can access their photos during or right after the event.</p>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <div className="w-5 h-5 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 shrink-0 mt-0.5">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                  </div>
                  <div>
                    <h5 className="text-xs sm:text-sm font-semibold text-white">Clean Studio Dashboard</h5>
                    <p className="text-xs text-slate-400">Monitor uploads, processing status, and gallery activity in real-time.</p>
                  </div>
                </div>
              </div>
            </div>

            {/* Photographer summary card */}
            <div className="glass-panel p-6 sm:p-8 rounded-2xl border border-white/10 space-y-4">
              <h4 className="text-lg font-bold text-white">Why Photographers Switch</h4>
              <div className="space-y-3">
                <div className="p-3.5 rounded-xl bg-white/[0.02] border border-white/5 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <Clock className="w-4 h-4 text-brand-400" />
                    <div>
                      <div className="text-xs sm:text-sm font-semibold text-white">Delivery Speed</div>
                      <div className="text-[11px] text-slate-400">Instant instead of days of sorting</div>
                    </div>
                  </div>
                  <span className="text-xs font-semibold text-emerald-400 px-2.5 py-0.5 rounded-full bg-emerald-500/10 border border-emerald-500/20">
                    Instant
                  </span>
                </div>

                <div className="p-3.5 rounded-xl bg-white/[0.02] border border-white/5 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <Share2 className="w-4 h-4 text-indigo-400" />
                    <div>
                      <div className="text-xs sm:text-sm font-semibold text-white">Guest Friction</div>
                      <div className="text-[11px] text-slate-400">No passwords or accounts required</div>
                    </div>
                  </div>
                  <span className="text-xs font-semibold text-brand-400 px-2.5 py-0.5 rounded-full bg-brand-500/10 border border-brand-500/20">
                    Zero
                  </span>
                </div>

                <div className="p-3.5 rounded-xl bg-white/[0.02] border border-white/5 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <ShieldCheck className="w-4 h-4 text-purple-400" />
                    <div>
                      <div className="text-xs sm:text-sm font-semibold text-white">Privacy</div>
                      <div className="text-[11px] text-slate-400">Selfies never permanently stored</div>
                    </div>
                  </div>
                  <span className="text-xs font-semibold text-purple-300 px-2.5 py-0.5 rounded-full bg-purple-500/10 border border-purple-500/20">
                    Secure
                  </span>
                </div>
              </div>

              <Link
                to="/register"
                className="w-full inline-flex items-center justify-center gap-2 py-2.5 rounded-xl text-xs sm:text-sm font-semibold text-white bg-brand-600 hover:bg-brand-500 transition-colors shadow-md shadow-brand-500/20"
              >
                Create Free Photographer Account
                <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Final CTA Section */}
      <section className="py-20 relative overflow-hidden">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center relative z-10">
          <div className="glass-panel rounded-3xl p-8 sm:p-12 border border-white/10 space-y-4">
            <div className="w-12 h-12 mx-auto rounded-xl bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center text-white shadow-md shadow-brand-500/25">
              <Sparkles className="w-6 h-6" />
            </div>

            <h2 className="text-2xl sm:text-4xl font-extrabold text-white tracking-tight">
              Make Every Event Memory Effortless to Find
            </h2>

            <p className="text-xs sm:text-base text-slate-300 max-w-lg mx-auto">
              Set up your first event gallery in under a minute.
            </p>

            <div className="pt-2">
              <Link
                to="/register"
                className="inline-flex items-center gap-2 px-6 py-3 rounded-xl text-xs sm:text-sm font-semibold text-white bg-gradient-to-r from-brand-600 via-brand-500 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-md shadow-brand-500/20 transition-all hover:scale-105"
              >
                Get Started as Photographer
                <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
};

