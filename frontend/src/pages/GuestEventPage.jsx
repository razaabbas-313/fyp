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
  AlertCircle,
  CheckCircle2,
  User,
  Search
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
  const [consentGiven, setConsentGiven] = useState(false);
  const [toast, setToast] = useState({ message: '', type: 'info' });

  // Name-based search state
  const [searchTab, setSearchTab] = useState('selfie'); // 'selfie' | 'name'
  const [nameQuery, setNameQuery] = useState('');
  const [searchingName, setSearchingName] = useState(false);

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
        setToast({ message: 'Please select an image file', type: 'error' });
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

    if (!consentGiven) {
      setToast({
        message: 'Please accept the privacy consent checkbox to continue.',
        type: 'error',
      });
      return;
    }

    setSearching(true);
    try {
      const res = await guestApi.searchBySelfie(publicId, selfieBlob);
      const searchData = res.data || {};
      const matches = searchData.matches || [];
      const sessionToken = searchData.sessionToken || '';
      const expiresAt = searchData.expiresAt || '';

      const faceDetected = searchData.faceDetected !== false;
      const indexingCount = searchData.indexingCount || 0;

      // Store session authorization token and matches in sessionStorage
      sessionStorage.setItem(`fotofetch_${publicId}_matches`, JSON.stringify(matches));
      sessionStorage.setItem(`fotofetch_${publicId}_session_token`, sessionToken);
      sessionStorage.setItem(`fotofetch_${publicId}_session_expires`, expiresAt);
      sessionStorage.setItem(`fotofetch_${publicId}_selfie`, selfiePreview || '');
      sessionStorage.setItem(`fotofetch_${publicId}_face_detected`, JSON.stringify(faceDetected));
      sessionStorage.setItem(`fotofetch_${publicId}_indexing_count`, indexingCount.toString());

      navigate(`/guest/${publicId}/results`);
    } catch (err) {
      const errMsg = err.response?.data?.message || err.message || 'Photo search failed. Please try again.';
      setToast({
        message: errMsg,
        type: 'error',
      });
    } finally {
      setSearching(false);
    }
  };

  const handleNameSearch = async (e) => {
    e?.preventDefault();
    if (!nameQuery.trim() || searchingName) return;

    setSearchingName(true);
    try {
      const res = await guestApi.searchByName(publicId, nameQuery.trim());
      const searchData = res.data || {};
      const matches = searchData.matches || [];
      const sessionToken = searchData.sessionToken || '';
      const expiresAt = searchData.expiresAt || '';
      const taggedName = searchData.taggedName || '';
      const taggedUsername = searchData.taggedUsername || '';

      if (!matches || matches.length === 0) {
        setToast({
          message: `No photos found for "${nameQuery}". Please check the spelling or take a selfie first.`,
          type: 'error',
        });
        return;
      }

      sessionStorage.setItem(`fotofetch_${publicId}_matches`, JSON.stringify(matches));
      sessionStorage.setItem(`fotofetch_${publicId}_session_token`, sessionToken);
      sessionStorage.setItem(`fotofetch_${publicId}_session_expires`, expiresAt);
      sessionStorage.setItem(`fotofetch_${publicId}_selfie`, '');
      sessionStorage.setItem(`fotofetch_${publicId}_face_detected`, 'true');
      sessionStorage.setItem(`fotofetch_${publicId}_indexing_count`, '0');
      sessionStorage.setItem(`fotofetch_${publicId}_tagged_name`, taggedName);
      sessionStorage.setItem(`fotofetch_${publicId}_tagged_username`, taggedUsername);

      navigate(`/guest/${publicId}/results`);
    } catch (err) {
      const errMsg = err.response?.data?.message || err.message || 'No photos found for that name or username.';
      setToast({
        message: errMsg,
        type: 'error',
      });
    } finally {
      setSearchingName(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-dark-950 flex flex-col items-center justify-center text-slate-400 gap-2.5">
        <Loader2 className="w-8 h-8 animate-spin text-brand-500" />
        <p className="text-xs sm:text-sm">Connecting to event...</p>
      </div>
    );
  }

  if (error || !event) {
    return (
      <div className="min-h-screen bg-dark-950 flex flex-col items-center justify-center p-6 text-center">
        <div className="w-12 h-12 rounded-2xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400 mb-3">
          <AlertCircle className="w-6 h-6" />
        </div>
        <h2 className="text-lg font-bold text-white mb-1.5">Event Not Available</h2>
        <p className="text-xs sm:text-sm text-slate-400 max-w-sm mb-5">{error || 'Please check the event link and try again.'}</p>
        <Link to="/" className="text-brand-400 hover:underline text-xs sm:text-sm font-medium">
          Return to FotoFetch AI
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
      <header className="border-b border-white/5 py-3.5 px-4 sm:px-6 bg-dark-950/85 backdrop-blur-md sticky top-0 z-30">
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center">
              <Camera className="w-4 h-4 text-white" />
            </div>
            <span className="font-bold text-white text-base tracking-tight">
              FotoFetch <span className="text-brand-400 text-xs font-semibold">AI</span>
            </span>
          </Link>

          <span className="text-xs px-2.5 py-1 rounded-full bg-white/5 border border-white/10 text-slate-300 flex items-center gap-1.5">
            <Lock className="w-3 h-3 text-brand-400" />
            Private Guest Portal
          </span>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-xl w-full mx-auto px-4 py-8 sm:py-12 flex flex-col items-center">
        {/* Event Header Banner */}
        <div className="space-y-2 mb-8 text-center w-full">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-brand-500/10 border border-brand-500/20 text-xs font-medium text-brand-300">
            <Calendar className="w-3.5 h-3.5" />
            {event.eventDate}
          </div>
          <h1 className="text-2xl sm:text-4xl font-extrabold text-white tracking-tight">
            {event.name}
          </h1>
          {event.description && (
            <p className="text-xs sm:text-sm text-slate-300 max-w-md mx-auto leading-relaxed">{event.description}</p>
          )}
          <p className="text-xs text-slate-400 pt-1">
            Find your photos in seconds using a quick selfie or your name.
          </p>
        </div>

        {/* Interactive Search Box */}
        <div className="w-full glass-panel rounded-2xl p-5 sm:p-8 border border-white/10 shadow-xl relative space-y-5 text-center">
          {/* Method Tab Toggle */}
          <div className="inline-flex p-1 rounded-xl bg-white/5 border border-white/10">
            <button
              type="button"
              onClick={() => setSearchTab('selfie')}
              className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                searchTab === 'selfie'
                  ? 'bg-gradient-to-r from-brand-600 to-indigo-600 text-white shadow-md shadow-brand-500/20'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <Camera className="w-3.5 h-3.5" />
              Search with Selfie
            </button>
            <button
              type="button"
              onClick={() => setSearchTab('name')}
              className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                searchTab === 'name'
                  ? 'bg-gradient-to-r from-brand-600 to-indigo-600 text-white shadow-md shadow-brand-500/20'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <User className="w-3.5 h-3.5" />
              Search by Name
            </button>
          </div>

          {searchTab === 'name' ? (
            /* Name-based Retrieval */
            <div className="space-y-4 max-w-sm mx-auto py-2">
              <div className="space-y-1">
                <h3 className="text-base font-bold text-white">Find by Name or Username</h3>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Enter your name or assigned handle (e.g. <span className="font-mono text-brand-300 font-medium">ali</span> or <span className="font-mono text-brand-300 font-medium">ali2</span>) to retrieve your photos.
                </p>
              </div>

              <form onSubmit={handleNameSearch} className="space-y-3">
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                    <User className="w-4 h-4" />
                  </div>
                  <input
                    type="text"
                    value={nameQuery}
                    onChange={(e) => setNameQuery(e.target.value)}
                    placeholder="e.g. Ali, Sara, or ali2"
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-white/[0.04] border border-white/10 text-white text-xs sm:text-sm placeholder-slate-500 focus:outline-none focus:border-brand-500 transition-colors"
                  />
                </div>

                <button
                  type="submit"
                  disabled={searchingName || !nameQuery.trim()}
                  className="w-full flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-md shadow-brand-500/20 transition-all disabled:opacity-50 cursor-pointer"
                >
                  {searchingName ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Searching...
                    </>
                  ) : (
                    <>
                      <Search className="w-4 h-4" />
                      Find My Photos
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>
              </form>

              <p className="text-[11px] text-slate-400">
                First time here? Switch to <button type="button" onClick={() => setSearchTab('selfie')} className="text-brand-300 underline font-medium">Search with Selfie</button> to find your photos first.
              </p>
            </div>
          ) : selfieMode === 'camera' ? (
            <CameraCapture
              onCapture={handleCameraCapture}
              onCancel={() => setSelfieMode(null)}
            />
          ) : selfiePreview ? (
            /* Selected Selfie Display */
            <div className="flex flex-col items-center space-y-5">
              <div className="relative">
                <div className="w-36 h-36 sm:w-44 sm:h-44 rounded-full overflow-hidden border-3 border-brand-500 shadow-xl">
                  <img
                    src={selfiePreview}
                    alt="Your Selfie"
                    className="w-full h-full object-cover"
                  />
                </div>
                <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 px-3 py-0.5 rounded-full bg-emerald-500 text-white text-[11px] font-semibold shadow-md whitespace-nowrap flex items-center gap-1">
                  <CheckCircle2 className="w-3 h-3" />
                  Selfie Ready
                </div>
              </div>

              {/* Calm, Ethical Privacy Consent Checkbox */}
              <label className="flex items-start gap-2.5 p-3.5 rounded-xl bg-white/[0.03] border border-white/10 text-left cursor-pointer hover:bg-white/[0.05] transition-colors max-w-sm mx-auto">
                <input
                  type="checkbox"
                  checked={consentGiven}
                  onChange={(e) => setConsentGiven(e.target.checked)}
                  className="mt-0.5 w-4 h-4 rounded border-slate-700 text-brand-600 focus:ring-brand-500 bg-dark-900 cursor-pointer shrink-0"
                />
                <div className="text-[11px] text-slate-300 leading-relaxed">
                  I agree to find my photos using face matching. Selfies are processed securely and never stored.
                </div>
              </label>

              <div className="flex flex-col sm:flex-row items-center gap-2.5 pt-1 w-full max-w-sm">
                <button
                  onClick={() => {
                    setSelfieBlob(null);
                    setSelfiePreview(null);
                  }}
                  className="w-full sm:w-auto px-4 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:text-white bg-white/5 hover:bg-white/10 transition-colors cursor-pointer"
                >
                  Change Photo
                </button>
                <button
                  onClick={handleSearch}
                  disabled={searching || !consentGiven}
                  className="w-full sm:flex-1 flex items-center justify-center gap-2 px-6 py-2.5 rounded-xl text-xs sm:text-sm font-semibold text-white bg-gradient-to-r from-brand-600 via-brand-500 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-md shadow-brand-500/20 transition-all disabled:opacity-50 cursor-pointer"
                >
                  {searching ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Finding Your Photos...
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
            <div className="space-y-5">
              <p className="text-xs text-slate-400 max-w-xs mx-auto">
                Choose how you'd like to provide your selfie:
              </p>

              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleFileUpload}
                className="hidden"
              />

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 max-w-sm mx-auto">
                <button
                  onClick={() => setSelfieMode('camera')}
                  className="glass-card p-5 rounded-xl flex flex-col items-center gap-2 border border-white/10 hover:border-brand-500/50 transition-all group cursor-pointer"
                >
                  <div className="w-10 h-10 rounded-xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400 group-hover:scale-105 transition-transform">
                    <Camera className="w-5 h-5" />
                  </div>
                  <span className="text-xs sm:text-sm font-semibold text-white">Use Camera</span>
                  <span className="text-[10px] text-slate-400">Take a quick selfie</span>
                </button>

                <button
                  onClick={() => fileInputRef.current?.click()}
                  className="glass-card p-5 rounded-xl flex flex-col items-center gap-2 border border-white/10 hover:border-indigo-500/50 transition-all group cursor-pointer"
                >
                  <div className="w-10 h-10 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 group-hover:scale-105 transition-transform">
                    <Upload className="w-5 h-5" />
                  </div>
                  <span className="text-xs sm:text-sm font-semibold text-white">Upload Photo</span>
                  <span className="text-[10px] text-slate-400">From camera roll</span>
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Calm Privacy Guarantee Note */}
        <div className="mt-6 flex items-center gap-2 text-center text-[11px] text-slate-400">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          <span>Photos are privately matched. Your selfie is never stored or shared.</span>
        </div>
      </main>
    </div>
  );
};

