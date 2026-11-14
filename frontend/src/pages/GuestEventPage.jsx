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
  ImageIcon,
  FolderArchive,
  Download,
  CheckSquare,
  Square,
  Eye,
  Check,
  X,
  Users,
  Layers
} from 'lucide-react';
import { guestApi } from '../api/guestApi';
import { CameraCapture } from '../components/CameraCapture';
import { Toast } from '../components/Toast';
import { ImageGalleryLightbox } from '../components/ImageGalleryLightbox';

export const GuestEventPage = () => {
  const { publicId } = useParams();
  const navigate = useNavigate();

  const [event, setEvent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Active view tab: 'search' | 'gallery'
  const [activeTab, setActiveTab] = useState('search');

  // Selfie search state
  const [selfieMode, setSelfieMode] = useState(null); // null | 'camera' | 'file'
  const [selfieBlob, setSelfieBlob] = useState(null);
  const [selfiePreview, setSelfiePreview] = useState(null);
  const [searching, setSearching] = useState(false);

  // Gallery state
  const [galleryPhotos, setGalleryPhotos] = useState([]);
  const [loadingGallery, setLoadingGallery] = useState(false);
  const [selectedPhotoIds, setSelectedPhotoIds] = useState(new Set());

  // Lightbox state
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [lightboxIndex, setLightboxIndex] = useState(0);

  // Download state
  const [zipping, setZipping] = useState(false);
  const [downloadingPhotoId, setDownloadingPhotoId] = useState(null);
  const [toast, setToast] = useState({ message: '', type: 'info' });

  const fileInputRef = useRef(null);

  useEffect(() => {
    fetchEventDetails();
  }, [publicId]);

  // Lazy load public gallery photos when gallery tab is activated
  useEffect(() => {
    if (activeTab === 'gallery' && galleryPhotos.length === 0) {
      loadGalleryPhotos();
    }
  }, [activeTab]);

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

  const loadGalleryPhotos = async () => {
    setLoadingGallery(true);
    try {
      const res = await guestApi.getEventPhotos(publicId);
      setGalleryPhotos(res.data || []);
    } catch (err) {
      console.error('Failed to load gallery photos', err);
      setToast({ message: 'Failed to load event photos: ' + err.message, type: 'error' });
    } finally {
      setLoadingGallery(false);
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

  // Selection handlers
  const handleToggleSelect = (photoId, e) => {
    e?.stopPropagation();
    setSelectedPhotoIds((prev) => {
      const next = new Set(prev);
      if (next.has(photoId)) {
        next.delete(photoId);
      } else {
        next.add(photoId);
      }
      return next;
    });
  };

  const isAllSelected = galleryPhotos.length > 0 && selectedPhotoIds.size === galleryPhotos.length;

  const handleSelectAllToggle = () => {
    if (isAllSelected) {
      setSelectedPhotoIds(new Set());
    } else {
      setSelectedPhotoIds(new Set(galleryPhotos.map((p) => p.id)));
    }
  };

  // Open Lightbox
  const handleOpenLightbox = (index) => {
    setLightboxIndex(index);
    setLightboxOpen(true);
  };

  // Single download
  const handleDownloadSingle = async (photo, e) => {
    e?.stopPropagation();
    let downloadUrl = photo.downloadUrl;
    if (!downloadUrl) {
      setToast({
        message: 'Original HD downloads are protected. Please use the "Find My Photos" tab with your selfie to access and download your pictures!',
        type: 'info',
      });
      return;
    }

    setDownloadingPhotoId(photo.id);
    try {
      const link = document.createElement('a');
      link.href = downloadUrl;
      link.download = photo.originalFilename || 'photo.jpg';
      link.target = '_blank';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setToast({ message: 'Downloading original HD photo...', type: 'success' });
    } catch (err) {
      setToast({ message: 'Download failed: ' + err.message, type: 'error' });
    } finally {
      setDownloadingPhotoId(null);
    }
  };

  // ZIP download
  const handleDownloadZip = async (photoIdsToDownload, label = 'ZIP') => {
    if (!photoIdsToDownload || photoIdsToDownload.length === 0 || zipping) return;

    setZipping(true);
    setToast({ message: `Preparing your ${label} archive (${photoIdsToDownload.length} photos)...`, type: 'info' });

    try {
      const blob = await guestApi.downloadZipBlob(publicId, photoIdsToDownload);

      const url = window.URL.createObjectURL(new Blob([blob], { type: 'application/zip' }));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `fotofetch-${publicId}-photos.zip`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);

      setToast({ message: `${label} archive downloaded successfully!`, type: 'success' });
    } catch (err) {
      setToast({ message: 'ZIP generation failed: ' + err.message, type: 'error' });
    } finally {
      setZipping(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-dark-950 flex flex-col items-center justify-center text-slate-400 gap-3">
        <Loader2 className="w-8 h-8 animate-spin text-brand-500" />
        <p className="text-sm">Connecting to event gallery...</p>
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

      {/* Sliding Image Gallery Lightbox */}
      <ImageGalleryLightbox
        isOpen={lightboxOpen}
        photos={galleryPhotos}
        currentIndex={lightboxIndex}
        onClose={() => setLightboxOpen(false)}
        onNavigate={(newIdx) => setLightboxIndex(newIdx)}
      />

      {/* Floating Selection Action Bar for Gallery Tab */}
      {activeTab === 'gallery' && selectedPhotoIds.size > 0 && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 bg-dark-900/95 backdrop-blur-xl border border-brand-500/30 px-5 py-3 rounded-2xl shadow-2xl shadow-black/80 flex items-center gap-4 animate-slide-up">
          <div className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-brand-400 animate-pulse" />
            <span className="text-xs font-semibold text-white">
              {selectedPhotoIds.size} {selectedPhotoIds.size === 1 ? 'photo' : 'photos'} selected
            </span>
          </div>

          <div className="h-4 w-px bg-white/15" />

          <button
            onClick={handleSelectAllToggle}
            className="text-xs font-medium text-slate-300 hover:text-white transition-colors"
          >
            {isAllSelected ? 'Deselect All' : 'Select All'}
          </button>

          <button
            onClick={() => handleDownloadZip(Array.from(selectedPhotoIds), 'Selected Photos ZIP')}
            disabled={zipping}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-lg shadow-brand-500/30 transition-all disabled:opacity-50"
          >
            {zipping ? (
              <>
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                Generating ZIP...
              </>
            ) : (
              <>
                <FolderArchive className="w-3.5 h-3.5" />
                Download Selected as ZIP
              </>
            )}
          </button>

          <button
            onClick={() => setSelectedPhotoIds(new Set())}
            className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
            title="Clear Selection"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

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
            <span className="text-xs px-2.5 py-1 rounded-full bg-white/5 border border-white/10 text-slate-400">
              Guest Portal
            </span>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-5xl w-full mx-auto px-4 py-10 flex flex-col items-center pb-28">
        {/* Event Welcome Banner */}
        <div className="space-y-3 mb-8 text-center max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand-500/10 border border-brand-500/20 text-xs font-medium text-brand-300">
            <Calendar className="w-3.5 h-3.5" />
            {event.eventDate} • {event.totalPhotos} Photos
          </div>
          <h1 className="text-3xl sm:text-5xl font-extrabold text-white tracking-tight">
            {event.name}
          </h1>
          {event.description && (
            <p className="text-sm text-slate-300 max-w-lg mx-auto">{event.description}</p>
          )}
        </div>

        {/* Tab Toggle Navigation */}
        <div className="inline-flex p-1.5 rounded-2xl bg-white/[0.04] border border-white/10 mb-8 backdrop-blur-md">
          <button
            onClick={() => setActiveTab('search')}
            className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'search'
                ? 'bg-brand-600 text-white shadow-lg shadow-brand-500/30'
                : 'text-slate-400 hover:text-white hover:bg-white/5'
            }`}
          >
            <Sparkles className="w-4 h-4" />
            Find My Photos (AI)
          </button>
          <button
            onClick={() => setActiveTab('gallery')}
            className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'gallery'
                ? 'bg-brand-600 text-white shadow-lg shadow-brand-500/30'
                : 'text-slate-400 hover:text-white hover:bg-white/5'
            }`}
          >
            <ImageIcon className="w-4 h-4" />
            Browse Event Gallery
            {event.totalPhotos > 0 && (
              <span className="px-1.5 py-0.5 rounded-full bg-white/15 text-[10px] font-mono">
                {event.totalPhotos}
              </span>
            )}
          </button>
        </div>

        {/* TAB 1: AI Face Search View */}
        {activeTab === 'search' && (
          <div className="w-full max-w-2xl flex flex-col items-center">
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
                    <div className="w-40 h-40 sm:w-48 sm:h-48 rounded-full overflow-hidden border-4 border-brand-500 shadow-2xl">
                      <img
                        src={selfiePreview}
                        alt="Your Selfie"
                        className="w-full h-full object-cover"
                      />
                    </div>
                    <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 px-3 py-0.5 rounded-full bg-emerald-500 text-white text-[11px] font-bold shadow-lg">
                      Selfie Ready
                    </div>
                  </div>

                  <div className="flex flex-col sm:flex-row items-center gap-3">
                    <button
                      onClick={() => {
                        setSelfieBlob(null);
                        setSelfiePreview(null);
                      }}
                      className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:text-white bg-white/5 hover:bg-white/10 transition-colors"
                    >
                      Change Photo
                    </button>
                    <button
                      onClick={handleSearch}
                      disabled={searching}
                      className="flex items-center gap-2.5 px-8 py-3.5 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-brand-600 via-brand-500 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-xl shadow-brand-500/25 transition-all hover:scale-105 disabled:opacity-50"
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
                    <h3 className="text-lg font-bold text-white">Choose Your Selfie</h3>
                    <p className="text-xs text-slate-400 max-w-sm mx-auto">
                      Take a clear selfie or upload one from your device. Our AI will search this event for matching photographs.
                    </p>
                    <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/[0.04] border border-white/5 text-[11px] text-slate-400 mt-1">
                      <ShieldCheck className="w-3.5 h-3.5 text-brand-400 shrink-0" />
                      <span>Privacy Protected: Your selfie is processed in-memory solely for matching and is never stored.</span>
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
                      className="glass-card p-6 rounded-2xl flex flex-col items-center gap-3 border border-white/10 hover:border-brand-500 transition-all group"
                    >
                      <div className="w-12 h-12 rounded-xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400 group-hover:scale-110 transition-transform">
                        <Camera className="w-6 h-6" />
                      </div>
                      <span className="text-sm font-semibold text-white">Use Camera</span>
                      <span className="text-[11px] text-slate-400">Snap a quick selfie</span>
                    </button>

                    <button
                      onClick={() => fileInputRef.current?.click()}
                      className="glass-card p-6 rounded-2xl flex flex-col items-center gap-3 border border-white/10 hover:border-indigo-500 transition-all group"
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

            {/* Privacy Notice Card */}
            <div className="mt-8 p-4 rounded-2xl bg-white/[0.02] border border-white/5 max-w-lg text-left flex items-start gap-3">
              <ShieldCheck className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
              <div className="space-y-1">
                <h5 className="text-xs font-semibold text-slate-200">Biometric Privacy Guarantee</h5>
                <p className="text-[11px] text-slate-400 leading-relaxed">
                  Your selfie is processed ephemerally in memory by AWS Rekognition only to find your photos for this event. We never sell biometric data or store guest selfies permanently.
                </p>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: Full Event Gallery Preview & Multi-Select ZIP View */}
        {activeTab === 'gallery' && (
          <div className="w-full space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 glass-panel p-4 rounded-2xl border border-white/10">
              <div className="flex items-center gap-3">
                <span className="text-sm font-bold text-white tracking-tight">Event Photographs</span>
                <span className="text-xs text-slate-400">({galleryPhotos.length} photos)</span>
              </div>

              <div className="flex items-center gap-3">
                {galleryPhotos.length > 0 && (
                  <>
                    <button
                      onClick={handleSelectAllToggle}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-white/5 hover:bg-white/10 text-slate-300 hover:text-white border border-white/10 transition-colors"
                    >
                      {isAllSelected ? (
                        <>
                          <CheckSquare className="w-3.5 h-3.5 text-brand-400" />
                          Deselect All
                        </>
                      ) : (
                        <>
                          <Square className="w-3.5 h-3.5 text-slate-400" />
                          Select All
                        </>
                      )}
                    </button>

                    <button
                      onClick={() => handleDownloadZip(galleryPhotos.map((p) => p.id), 'All Photos ZIP')}
                      disabled={zipping}
                      className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-semibold text-white bg-brand-600 hover:bg-brand-500 transition-colors shadow-lg shadow-brand-500/25 disabled:opacity-50"
                    >
                      {zipping ? (
                        <>
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          Preparing...
                        </>
                      ) : (
                        <>
                          <FolderArchive className="w-3.5 h-3.5" />
                          Download All as ZIP
                        </>
                      )}
                    </button>
                  </>
                )}
              </div>
            </div>

            {loadingGallery ? (
              <div className="py-20 flex flex-col items-center justify-center text-slate-400 gap-3">
                <Loader2 className="w-8 h-8 animate-spin text-brand-500" />
                <p className="text-sm">Loading event photos...</p>
              </div>
            ) : galleryPhotos.length === 0 ? (
              <div className="glass-panel rounded-3xl p-12 text-center border border-white/10 space-y-3 max-w-md mx-auto">
                <div className="w-14 h-14 mx-auto rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center text-slate-400">
                  <ImageIcon className="w-7 h-7" />
                </div>
                <h3 className="text-base font-bold text-white">No Photos Uploaded Yet</h3>
                <p className="text-xs text-slate-400 leading-relaxed">
                  The photographer has not uploaded photos for this event yet, or they are currently being processed.
                </p>
              </div>
            ) : (
              /* Photo Grid */
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
                {galleryPhotos.map((photo, index) => {
                  const isSelected = selectedPhotoIds.has(photo.id);

                  return (
                    <div
                      key={photo.id}
                      onClick={() => handleOpenLightbox(index)}
                      className={`glass-card rounded-2xl overflow-hidden border group flex flex-col justify-between relative transition-all duration-200 cursor-pointer ${
                        isSelected
                          ? 'border-brand-500 ring-2 ring-brand-500/70 bg-brand-500/[0.04]'
                          : 'border-white/10 hover:border-white/20'
                      }`}
                    >
                      {/* Selection Checkbox */}
                      <button
                        type="button"
                        onClick={(e) => handleToggleSelect(photo.id, e)}
                        className={`absolute top-2 right-2 p-1.5 rounded-lg z-20 transition-all ${
                          isSelected
                            ? 'bg-brand-600 text-white shadow-lg shadow-brand-500/40 ring-2 ring-white/30 opacity-100'
                            : selectedPhotoIds.size > 0
                            ? 'bg-black/60 text-slate-300 opacity-90 backdrop-blur-md'
                            : 'bg-black/60 text-slate-300 hover:text-white opacity-0 group-hover:opacity-100 backdrop-blur-md'
                        }`}
                        title={isSelected ? 'Deselect photo' : 'Select photo for ZIP download'}
                      >
                        {isSelected ? (
                          <CheckSquare className="w-4 h-4 text-white" />
                        ) : (
                          <Square className="w-4 h-4" />
                        )}
                      </button>

                      <div className="aspect-square bg-dark-900 relative overflow-hidden">
                        {photo.thumbnailUrl ? (
                          <img
                            src={photo.thumbnailUrl}
                            alt={photo.originalFilename}
                            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                            loading="lazy"
                          />
                        ) : (
                          <div className="w-full h-full flex flex-col items-center justify-center text-slate-500 text-xs p-2 text-center">
                            <ImageIcon className="w-6 h-6 mb-1 text-slate-600" />
                            <span className="text-[10px]">Processing...</span>
                          </div>
                        )}

                        {/* Preview overlay on hover */}
                        <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center pointer-events-none">
                          <div className="px-2.5 py-1 rounded-full bg-black/60 backdrop-blur-md text-white text-[11px] font-medium flex items-center gap-1">
                            <Eye className="w-3.5 h-3.5" />
                            Preview
                          </div>
                        </div>

                        {/* Faces badge */}
                        {photo.facesFound > 0 && (
                          <div className="absolute top-2 left-2 px-2 py-0.5 rounded-full bg-black/70 backdrop-blur-md border border-white/10 text-[10px] font-medium text-emerald-300 flex items-center gap-1 z-10">
                            <Users className="w-3 h-3 text-emerald-400" />
                            {photo.facesFound}
                          </div>
                        )}
                      </div>

                      <div className="p-3 bg-dark-900/80 border-t border-white/5 flex items-center justify-between gap-2">
                        <div className="truncate text-[11px] text-slate-300 font-medium" title={photo.originalFilename}>
                          {photo.originalFilename}
                        </div>
                        {photo.downloadUrl && (
                          <button
                            onClick={(e) => handleDownloadSingle(photo, e)}
                            disabled={downloadingPhotoId === photo.id}
                            className="p-1.5 rounded-lg bg-white/5 hover:bg-brand-600 text-slate-300 hover:text-white transition-colors shrink-0"
                            title="Download HD Original"
                          >
                            {downloadingPhotoId === photo.id ? (
                              <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            ) : (
                              <Download className="w-3.5 h-3.5" />
                            )}
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
};
