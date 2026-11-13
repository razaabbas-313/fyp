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
      <section id="hero" className="relative pt-36 pb-20 md:pt-44 md:pb-28 overflow-hidden">
        {/* Ambient Glows */}
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[350px] bg-gradient-to-tr from-brand-600/20 to-indigo-600/10 blur-[130px] rounded-full pointer-events-none" />

        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
          <div className="text-center max-w-3xl mx-auto space-y-6">
            {/* Badge */}
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/5 border border-white/10 text-xs font-medium text-brand-300 backdrop-blur-md">
              <Sparkles className="w-3.5 h-3.5 text-brand-400" />
              <span>Next-Gen Event Photography AI</span>
            </div>

            {/* Headline */}
            <h1 className="text-4xl sm:text-6xl lg:text-7xl font-extrabold tracking-tight text-white leading-[1.1]">
              Find Every Moment.{' '}
              <span className="bg-gradient-to-r from-brand-400 via-indigo-300 to-purple-400 bg-clip-text text-transparent">
                Instantly.
              </span>
            </h1>

            {/* Subtitle */}
            <p className="text-base sm:text-xl text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
              No more scrolling through thousands of event photos. FotoFetch AI uses AWS facial recognition so guests find all their photos in seconds with a simple selfie.
            </p>

            {/* CTAs */}
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-4">
              <Link
                to="/register"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-8 py-3.5 rounded-xl text-base font-semibold text-white bg-gradient-to-r from-brand-600 via-brand-500 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-xl shadow-brand-500/25 hover:scale-[1.02] active:scale-[0.98] transition-all"
              >
                <Sparkles className="w-5 h-5 text-brand-200" />
                Get Started as Photographer
                <ArrowRight className="w-4 h-4 text-brand-200" />
              </Link>
              <a
                href="#how-it-works"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-7 py-3.5 rounded-xl text-base font-medium text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10 transition-colors"
              >
                See How It Works
              </a>
            </div>
          </div>

          {/* Hero Visual Mockup */}
          <div className="mt-16 max-w-5xl mx-auto">
            <div className="relative rounded-2xl glass-panel p-2 sm:p-4 shadow-2xl border border-white/10">
              <div className="rounded-xl overflow-hidden bg-dark-900 border border-white/5 p-6 sm:p-8">
                {/* Visual Dashboard preview */}
                <div className="flex items-center justify-between pb-6 border-b border-white/5">
                  <div className="flex items-center gap-3">
                    <div className="w-3 h-3 rounded-full bg-rose-500/80" />
                    <div className="w-3 h-3 rounded-full bg-amber-500/80" />
                    <div className="w-3 h-3 rounded-full bg-emerald-500/80" />
                    <span className="text-xs text-slate-400 font-mono ml-2">FotoFetch AI — Live Event Engine</span>
                  </div>
                  <div className="text-xs text-emerald-400 flex items-center gap-1.5 font-medium">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                    AWS Rekognition Connected
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-6">
                  {/* Step 1 Visual Card */}
                  <div className="p-5 rounded-xl bg-white/[0.03] border border-white/5 space-y-3">
                    <div className="w-10 h-10 rounded-lg bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400">
                      <UploadCloud className="w-5 h-5" />
                    </div>
                    <h4 className="text-sm font-semibold text-white">1. Bulk Upload S3</h4>
                    <p className="text-xs text-slate-400">Direct presigned multipart uploads without server bottleneck.</p>
                    <div className="w-full bg-white/10 rounded-full h-1.5 overflow-hidden">
                      <div className="bg-brand-500 h-full w-full" />
                    </div>
                  </div>

                  {/* Step 2 Visual Card */}
                  <div className="p-5 rounded-xl bg-white/[0.03] border border-white/5 space-y-3">
                    <div className="w-10 h-10 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
                      <UserCheck className="w-5 h-5" />
                    </div>
                    <h4 className="text-sm font-semibold text-white">2. Guest Selfie Snap</h4>
                    <p className="text-xs text-slate-400">Guests scan QR code, snap a selfie or pick from camera roll.</p>
                    <span className="inline-block text-[10px] px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300">
                      Zero App Install Needed
                    </span>
                  </div>

                  {/* Step 3 Visual Card */}
                  <div className="p-5 rounded-xl bg-white/[0.03] border border-white/5 space-y-3">
                    <div className="w-10 h-10 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                      <Sparkles className="w-5 h-5" />
                    </div>
                    <h4 className="text-sm font-semibold text-white">3. Instant Recognition</h4>
                    <p className="text-xs text-slate-400">Rekognition indexes event collections with 99%+ accuracy.</p>
                    <span className="inline-block text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300">
                      98.7% Face Match
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* How It Works Section */}
      <section id="how-it-works" className="py-24 bg-dark-900/60 border-y border-white/5 relative">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-16 space-y-3">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-brand-400">Simple Workflow</h2>
            <p className="text-3xl sm:text-4xl font-bold text-white tracking-tight">How FotoFetch AI Works</p>
            <p className="text-sm text-slate-400">
              Transforming how event memories are delivered and discovered in three seamless steps.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {/* Step 1 */}
            <div className="glass-card p-8 rounded-2xl relative">
              <span className="text-5xl font-black text-white/5 absolute top-6 right-6">01</span>
              <div className="w-12 h-12 rounded-xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400 mb-6">
                <UploadCloud className="w-6 h-6" />
              </div>
              <h3 className="text-xl font-bold text-white mb-2">Photographer Uploads</h3>
              <p className="text-sm text-slate-400 leading-relaxed">
                Create an event in seconds. Bulk upload high-resolution photographs directly to cloud storage with automatic thumbnail generation.
              </p>
            </div>

            {/* Step 2 */}
            <div className="glass-card p-8 rounded-2xl relative">
              <span className="text-5xl font-black text-white/5 absolute top-6 right-6">02</span>
              <div className="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 mb-6">
                <Camera className="w-6 h-6" />
              </div>
              <h3 className="text-xl font-bold text-white mb-2">Guest Snaps a Selfie</h3>
              <p className="text-sm text-slate-400 leading-relaxed">
                Guests scan your venue QR code or open your public event link. No account creation or login required — just take or upload a selfie.
              </p>
            </div>

            {/* Step 3 */}
            <div className="glass-card p-8 rounded-2xl relative">
              <span className="text-5xl font-black text-white/5 absolute top-6 right-6">03</span>
              <div className="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 mb-6">
                <Sparkles className="w-6 h-6" />
              </div>
              <h3 className="text-xl font-bold text-white mb-2">Instant Matches Found</h3>
              <p className="text-sm text-slate-400 leading-relaxed">
                AWS Rekognition scans only that event’s collection. Guests view matching thumbnails, download HD originals, or bundle all matches as a ZIP.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="features" className="py-24 relative">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-16 space-y-3">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-brand-400">Core Capabilities</h2>
            <p className="text-3xl sm:text-4xl font-bold text-white tracking-tight">Built for Modern Event Scale</p>
            <p className="text-sm text-slate-400">
              Engineered with enterprise-grade AWS services for unmatched reliability, privacy, and speed.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <div className="glass-card p-6 rounded-2xl space-y-3">
              <div className="w-10 h-10 rounded-xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400">
                <Search className="w-5 h-5" />
              </div>
              <h4 className="text-lg font-semibold text-white">AI Face Recognition</h4>
              <p className="text-sm text-slate-400">
                Deep neural vision algorithms index all visible faces and match guests even across group photos and varied angles.
              </p>
            </div>

            <div className="glass-card p-6 rounded-2xl space-y-3">
              <div className="w-10 h-10 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
                <Zap className="w-5 h-5" />
              </div>
              <h4 className="text-lg font-semibold text-white">Fast Bulk Upload</h4>
              <p className="text-sm text-slate-400">
                Direct browser-to-S3 uploads with presigned PUT URLs bypass Spring server limits for maximum transfer speed.
              </p>
            </div>

            <div className="glass-card p-6 rounded-2xl space-y-3">
              <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                <Download className="w-5 h-5" />
              </div>
              <h4 className="text-lg font-semibold text-white">HD Photo Downloads</h4>
              <p className="text-sm text-slate-400">
                Preserve original camera quality. Guests download crisp, uncompressed high-definition originals directly via secure presigned GETs.
              </p>
            </div>

            <div className="glass-card p-6 rounded-2xl space-y-3">
              <div className="w-10 h-10 rounded-xl bg-purple-500/10 border border-purple-500/20 flex items-center justify-center text-purple-400">
                <ShieldCheck className="w-5 h-5" />
              </div>
              <h4 className="text-lg font-semibold text-white">Secure Cloud Storage</h4>
              <p className="text-sm text-slate-400">
                Private Amazon S3 bucket architecture. No public bucket access; every file is served via time-limited presigned credentials.
              </p>
            </div>

            <div className="glass-card p-6 rounded-2xl space-y-3">
              <div className="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400">
                <Lock className="w-5 h-5" />
              </div>
              <h4 className="text-lg font-semibold text-white">Isolated Event Collections</h4>
              <p className="text-sm text-slate-400">
                Strict event-level isolation. Each event maintains its own dedicated Rekognition index; guest faces are never searched across other events.
              </p>
            </div>

            <div className="glass-card p-6 rounded-2xl space-y-3">
              <div className="w-10 h-10 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400">
                <FolderArchive className="w-5 h-5" />
              </div>
              <h4 className="text-lg font-semibold text-white">Streaming ZIP Downloads</h4>
              <p className="text-sm text-slate-400">
                Download all matched memories with one click. Memory-efficient server streaming archives photos on-the-fly.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* For Photographers Section */}
      <section id="for-photographers" className="py-24 bg-dark-900/60 border-t border-white/5 relative">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
            <div className="space-y-6">
              <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-brand-500/10 border border-brand-500/20 text-xs font-medium text-brand-300">
                <Camera className="w-3.5 h-3.5" />
                Designed for Event Professionals
              </div>
              <h2 className="text-3xl sm:text-4xl font-bold text-white tracking-tight">
                Spend Less Time Delivering Photos, More Time Shooting.
              </h2>
              <p className="text-base text-slate-300 leading-relaxed">
                Traditional photo delivery involves uploading massive folders to Google Drive or Dropbox, where guests spend hours hunting for their faces or give up entirely. FotoFetch AI solves this permanently.
              </p>

              <div className="space-y-4 pt-2">
                <div className="flex items-start gap-3">
                  <div className="w-6 h-6 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 shrink-0 mt-0.5">
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                  <div>
                    <h5 className="text-sm font-semibold text-white">Delight Event Organizers & Guests</h5>
                    <p className="text-xs text-slate-400">Provide an unforgettable modern guest experience that makes your photography brand stand out.</p>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <div className="w-6 h-6 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 shrink-0 mt-0.5">
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                  <div>
                    <h5 className="text-sm font-semibold text-white">Instant QR Code at the Venue</h5>
                    <p className="text-xs text-slate-400">Print the auto-generated event QR code on table stands so guests can access photos as soon as you upload.</p>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <div className="w-6 h-6 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 shrink-0 mt-0.5">
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                  <div>
                    <h5 className="text-sm font-semibold text-white">Full Event Analytics</h5>
                    <p className="text-xs text-slate-400">Track total uploaded photos, successful indexing status, and event engagement from your dashboard.</p>
                  </div>
                </div>
              </div>
            </div>

            {/* Photographer benefit feature card */}
            <div className="glass-panel p-8 rounded-3xl border border-white/10 space-y-6">
              <h4 className="text-xl font-bold text-white">Why Photographers Choose FotoFetch</h4>
              <div className="space-y-4">
                <div className="p-4 rounded-xl bg-white/[0.02] border border-white/5 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <Clock className="w-5 h-5 text-brand-400" />
                    <div>
                      <div className="text-sm font-semibold text-white">Delivery Time</div>
                      <div className="text-xs text-slate-400">From days of manual sorting to seconds</div>
                    </div>
                  </div>
                  <span className="text-xs font-bold text-emerald-400 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20">
                    Instant
                  </span>
                </div>

                <div className="p-4 rounded-xl bg-white/[0.02] border border-white/5 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <Share2 className="w-5 h-5 text-indigo-400" />
                    <div>
                      <div className="text-sm font-semibold text-white">Guest Friction</div>
                      <div className="text-xs text-slate-400">No passwords or account required for guests</div>
                    </div>
                  </div>
                  <span className="text-xs font-bold text-brand-400 px-2.5 py-1 rounded-full bg-brand-500/10 border border-brand-500/20">
                    Zero
                  </span>
                </div>

                <div className="p-4 rounded-xl bg-white/[0.02] border border-white/5 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <ShieldCheck className="w-5 h-5 text-purple-400" />
                    <div>
                      <div className="text-sm font-semibold text-white">Privacy Standard</div>
                      <div className="text-xs text-slate-400">Selfies deleted immediately after search</div>
                    </div>
                  </div>
                  <span className="text-xs font-bold text-purple-300 px-2.5 py-1 rounded-full bg-purple-500/10 border border-purple-500/20">
                    Ephemeral
                  </span>
                </div>
              </div>

              <Link
                to="/register"
                className="w-full inline-flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-semibold text-white bg-brand-600 hover:bg-brand-500 transition-colors shadow-lg shadow-brand-500/20"
              >
                Create Photographer Account
                <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Final CTA Section */}
      <section className="py-24 relative overflow-hidden">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 text-center relative z-10">
          <div className="glass-panel rounded-3xl p-10 sm:p-16 border border-white/10 relative overflow-hidden space-y-6">
            <div className="w-16 h-16 mx-auto rounded-2xl bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center text-white shadow-xl shadow-brand-500/30">
              <Sparkles className="w-8 h-8" />
            </div>

            <h2 className="text-3xl sm:text-5xl font-extrabold text-white tracking-tight">
              Make Every Guest's Memories Easy to Find.
            </h2>

            <p className="text-base sm:text-lg text-slate-300 max-w-xl mx-auto">
              Start creating private event galleries powered by AWS Rekognition today.
            </p>

            <div className="pt-2">
              <Link
                to="/register"
                className="inline-flex items-center gap-2 px-8 py-3.5 rounded-xl text-base font-semibold text-white bg-gradient-to-r from-brand-600 via-brand-500 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-xl shadow-brand-500/25 transition-all hover:scale-105"
              >
                Get Started Now — It's Free
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
