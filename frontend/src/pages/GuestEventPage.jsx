import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  Camera,
  Upload,
  Sparkles,
  Calendar,
  ShieldCheck,
  Loader2,
  Lock,
  ArrowRight,
  AlertCircle
} from 'lucide-react';
import { guestApi } from '../api/guestApi';
import { CameraCapture } from '../components/CameraCapture';
import { Toast } from '../components/Toast';

export const GuestEventPage = () => {
  const { publicId } = useParams();
  const navigate = useNavigate();

  const [event, setEvent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Selfie search state
  const [selfieMode, setSelfieMode] = useState(null); // null | 'camera' | 'file'
  const [selfieBlob, setSelfieBlob] = useState(null);
  const [selfiePreview, setSelfiePreview] = useState(null);
  const [searching, setSearching] = useState(false);
  const [toast, setToast] = useState({ message: '', type: 'info' });

  const fileInputRef = useRef(null);

  useEffect(() => {
    fetchEventDetails();
  }, [publicId]);

  const fetchEventDetails = async () => {
    setLoading(true);
    try {
      const res = await guestApi.getPublicEvent(publicId);
      setEvent(res.data);
    } catch (err) {
      setError(err.message || 'Event not found or link has expired');
    } finally {
      setLoading(false);
    }
  };

  const handleFileUpload = (e) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      if (!file.type.startsWith('image/')) {
        setToast({ message: 'Please select a valid image file', type: 'error' });
        return;
      }
      setSelfieBlob(file);
      setSelfiePreview(URL.createObjectURL(file));
      setSelfieMode(null);
    }
  };

  const handleCameraCapture = (blob, previewUrl) => {
    setSelfieBlob(blob);
    setSelfiePreview(previewUrl);
    setSelfieMode(null);
  };

  const handleSearch = async () => {
    if (!selfieBlob) {
      setToast({ message: 'Please take or upload a selfie first', type: 'error' });
      return;
    }

    setSearching(true);
    try {
      const res = await guestApi.searchBySelfie(publicId, selfieBlob);
      // Store results in sessionStorage for results page
      sessionStorage.setItem(`fotofetch_${publicId}_matches`, JSON.stringify(res.data || []));
      sessionStorage.setItem(`fotofetch_${publicId}_selfie`, selfiePreview || '');
      navigate(`/guest/${publicId}/results`);
    } catch (err) {
      setToast({
        message: err.message || 'Facial recognition search failed. Ensure your face is clearly visible.',
        type: 'error',
      });
    } finally {
      setSearching(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-dark-950 flex flex-col items-center justify-center text-slate-400 gap-3">
        <Loader2 className="w-8 h-8 animate-spin text-brand-500" />
        <p className="text-sm">Connecting to event...</p>
      </div>
    );
  }

  if (error || !event) {
    return (
      <div className="min-h-screen bg-dark-950 flex flex-col items-center justify-center p-6 text-center">
        <div className="w-14 h-14 rounded-2xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400 mb-4">
          <AlertCircle className="w-7 h-7" />
        </div>
        <h2 className="text-xl font-bold text-white mb-2">Event Not Available</h2>
        <p className="text-sm text-slate-400 max-w-sm mb-6">{error || 'Please check the event link and try again.'}</p>
        <Link to="/" className="text-brand-400 hover:underline text-sm font-semibold">
          Return to FotoFetch AI Home
        </Link>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-dark-950 text-slate-100 relative">
      <Toast
        message={toast.message}
        type={toast.type}
        onClose={() => setToast({ message: '', type: 'info' })}
      />

      {/* Top Header */}
      <header className="border-b border-white/5 py-4 px-6 bg-dark-950/80 backdrop-blur-md sticky top-0 z-30">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center">
              <Camera className="w-4 h-4 text-white" />
            </div>
            <span className="font-bold text-white text-base tracking-tight">
              FotoFetch <span className="text-brand-400 text-xs font-mono">AI</span>
            </span>
          </Link>

          <div className="flex items-center gap-2">
            <span className="text-xs px-2.5 py-1 rounded-full bg-white/5 border border-white/10 text-slate-400 flex items-center gap-1.5">
              <Lock className="w-3 h-3 text-brand-400" />
              Face-Gated Guest Portal
            </span>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-3xl w-full mx-auto px-4 py-12 flex flex-col items-center">
        {/* Event Welcome Banner */}
        <div className="space-y-3 mb-10 text-center max-w-xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand-500/10 border border-brand-500/20 text-xs font-medium text-brand-300">
            <Calendar className="w-3.5 h-3.5" />
            {event.eventDate}
          </div>
          <h1 className="text-3xl sm:text-5xl font-extrabold text-white tracking-tight">
            {event.name}
          </h1>
          {event.description && (
            <p className="text-sm text-slate-300 max-w-lg mx-auto">{event.description}</p>
          )}
          <p className="text-xs text-brand-400/90 font-medium">
            Take or upload a selfie below to find all photos containing your face.
          </p>
        </div>

        {/* Camera / Upload Interactive Box */}
        <div className="w-full glass-panel rounded-3xl p-6 sm:p-10 border border-white/10 shadow-2xl relative space-y-8 text-center">
          {selfieMode === 'camera' ? (
            <CameraCapture
              onCapture={handleCameraCapture}
              onCancel={() => setSelfieMode(null)}
            />
          ) : selfiePreview ? (
            /* Selected Selfie Display */
            <div className="flex flex-col items-center space-y-6">
              <div className="relative">
                <div className="w-44 h-44 sm:w-52 sm:h-52 rounded-full overflow-hidden border-4 border-brand-500 shadow-2xl">
                  <img
                    src={selfiePreview}
                    alt="Your Selfie"
                    className="w-full h-full object-cover"
                  />
                </div>
                <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 px-3.5 py-1 rounded-full bg-emerald-500 text-white text-[11px] font-bold shadow-lg whitespace-nowrap">
                  Selfie Ready
                </div>
              </div>

              <div className="flex flex-col sm:flex-row items-center gap-3 pt-2">
                <button
                  onClick={() => {
                    setSelfieBlob(null);
                    setSelfiePreview(null);
                  }}
                  className="px-5 py-2.5 rounded-xl text-xs font-semibold text-slate-400 hover:text-white bg-white/5 hover:bg-white/10 transition-colors cursor-pointer"
                >
                  Change Photo
                </button>
                <button
                  onClick={handleSearch}
                  disabled={searching}
                  className="flex items-center gap-2.5 px-8 py-3.5 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-brand-600 via-brand-500 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-xl shadow-brand-500/25 transition-all hover:scale-105 disabled:opacity-50 cursor-pointer"
                >
                  {searching ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Scanning Event Photos with AI...
                    </>
                  ) : (
                    <>
                      <Sparkles className="w-4 h-4 text-brand-200" />
                      Find My Photos
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>
              </div>
            </div>
          ) : (
            /* Step Selection: Camera vs Upload */
            <div className="space-y-6">
              <div className="space-y-2">
                <h3 className="text-lg font-bold text-white">Snap or Upload Your Selfie</h3>
                <p className="text-xs text-slate-400 max-w-sm mx-auto">
                  Our AI will scan all photos taken at this event and retrieve only the photos where your face appears.
                </p>
                <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/[0.04] border border-white/5 text-[11px] text-slate-400 mt-1">
                  <ShieldCheck className="w-3.5 h-3.5 text-brand-400 shrink-0" />
                  <span>Face Protected: Only photos with your face will be displayed to you.</span>
                </div>
              </div>

              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleFileUpload}
                className="hidden"
              />

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-md mx-auto">
                <button
                  onClick={() => setSelfieMode('camera')}
                  className="glass-card p-6 rounded-2xl flex flex-col items-center gap-3 border border-white/10 hover:border-brand-500 transition-all group cursor-pointer"
                >
                  <div className="w-12 h-12 rounded-xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400 group-hover:scale-110 transition-transform">
                    <Camera className="w-6 h-6" />
                  </div>
                  <span className="text-sm font-semibold text-white">Use Camera</span>
                  <span className="text-[11px] text-slate-400">Snap a quick selfie</span>
                </button>

                <button
                  onClick={() => fileInputRef.current?.click()}
                  className="glass-card p-6 rounded-2xl flex flex-col items-center gap-3 border border-white/10 hover:border-indigo-500 transition-all group cursor-pointer"
                >
                  <div className="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 group-hover:scale-110 transition-transform">
                    <Upload className="w-6 h-6" />
                  </div>
                  <span className="text-sm font-semibold text-white">Upload Photo</span>
                  <span className="text-[11px] text-slate-400">Select from camera roll</span>
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Biometric Privacy Guarantee Card */}
        <div className="mt-8 p-4 rounded-2xl bg-white/[0.02] border border-white/5 max-w-lg text-left flex items-start gap-3">
          <ShieldCheck className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
          <div className="space-y-1">
            <h5 className="text-xs font-semibold text-slate-200">Biometric Privacy Guarantee</h5>
            <p className="text-[11px] text-slate-400 leading-relaxed">
              Your selfie is processed ephemerally in-memory by AWS Rekognition solely to find matching photos for this event. For privacy, full event photo galleries are face-protected and never publicly browsable.
            </p>
          </div>
        </div>
      </main>
    </div>
  );
};
