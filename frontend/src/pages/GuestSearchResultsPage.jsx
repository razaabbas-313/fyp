import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  Camera,
  Download,
  FolderArchive,
  ArrowLeft,
  Sparkles,
  Loader2,
  AlertCircle,
  Share2,
  CheckCircle2,
  Users,
  CheckSquare,
  Square,
  Eye,
  Check,
  X
} from 'lucide-react';
import { guestApi } from '../api/guestApi';
import { Toast } from '../components/Toast';
import { ImageGalleryLightbox } from '../components/ImageGalleryLightbox';

export const GuestSearchResultsPage = () => {
  const { publicId } = useParams();
  const navigate = useNavigate();

  const [matches, setMatches] = useState([]);
  const [selfieUrl, setSelfieUrl] = useState('');
  const [event, setEvent] = useState(null);
  const [loadingEvent, setLoadingEvent] = useState(true);
  const [zipping, setZipping] = useState(false);
  const [downloadingPhotoId, setDownloadingPhotoId] = useState(null);
  const [toast, setToast] = useState({ message: '', type: 'info' });

  // Selection state
  const [selectedPhotoIds, setSelectedPhotoIds] = useState(new Set());

  // Lightbox preview state
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [lightboxIndex, setLightboxIndex] = useState(0);

  useEffect(() => {
    // Load cached matches
    const storedMatches = sessionStorage.getItem(`fotofetch_${publicId}_matches`);
    const storedSelfie = sessionStorage.getItem(`fotofetch_${publicId}_selfie`);

    if (storedMatches) {
      try {
        setMatches(JSON.parse(storedMatches));
      } catch (e) {
        console.error('Error parsing stored matches', e);
      }
    }
    if (storedSelfie) {
      setSelfieUrl(storedSelfie);
    }

    // Load event name
    guestApi.getPublicEvent(publicId)
      .then((res) => setEvent(res.data))
      .catch((err) => console.error(err))
      .finally(() => setLoadingEvent(false));
  }, [publicId]);

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

  const isAllSelected = matches.length > 0 && selectedPhotoIds.size === matches.length;

  const handleSelectAllToggle = () => {
    if (isAllSelected) {
      setSelectedPhotoIds(new Set());
    } else {
      setSelectedPhotoIds(new Set(matches.map((m) => m.photoId)));
    }
  };

  // Open Lightbox
  const handleOpenLightbox = (index) => {
    setLightboxIndex(index);
    setLightboxOpen(true);
  };

  const handleDownloadSingle = async (match, e) => {
    e?.stopPropagation();
    setDownloadingPhotoId(match.photoId);
    try {
      let downloadUrl = match.downloadUrl;
      if (!downloadUrl) {
        const res = await guestApi.getPhotoDownloadUrl(publicId, match.photoId);
        downloadUrl = res.data.downloadUrl;
      }
      // Create temporary anchor to trigger native download
      const link = document.createElement('a');
      link.href = downloadUrl;
      link.download = match.originalFilename || 'photo.jpg';
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

  const handleDownloadZip = async (photoIdsToDownload, label = 'ZIP') => {
    if (!photoIdsToDownload || photoIdsToDownload.length === 0 || zipping) return;

    setZipping(true);
    setToast({ message: `Preparing your ${label} archive (${photoIdsToDownload.length} photos)...`, type: 'info' });

    try {
      const blob = await guestApi.downloadZipBlob(publicId, photoIdsToDownload);

      // Trigger browser download of ZIP
      const url = window.URL.createObjectURL(new Blob([blob], { type: 'application/zip' }));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `fotofetch-${publicId}-my-photos.zip`);
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

  const lightboxPhotos = matches.map((m) => ({
    ...m,
    id: m.photoId,
  }));

  return (
    <div className="min-h-screen flex flex-col bg-dark-950 text-slate-100">
      <Toast
        message={toast.message}
        type={toast.type}
        onClose={() => setToast({ message: '', type: 'info' })}
      />

      {/* Sliding Image Gallery Lightbox */}
      <ImageGalleryLightbox
        isOpen={lightboxOpen}
        photos={lightboxPhotos}
        currentIndex={lightboxIndex}
        onClose={() => setLightboxOpen(false)}
        onNavigate={(newIdx) => setLightboxIndex(newIdx)}
      />

      {/* Top Header */}
      <header className="border-b border-white/5 py-4 px-6 bg-dark-950/80 backdrop-blur-md sticky top-0 z-30">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <Link to={`/guest/${publicId}`} className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center">
              <Camera className="w-4 h-4 text-white" />
            </div>
            <span className="font-bold text-white text-base tracking-tight">
              FotoFetch <span className="text-brand-400 text-xs font-mono">AI</span>
            </span>
          </Link>

          <Link
            to={`/guest/${publicId}`}
            className="flex items-center gap-1.5 text-xs font-semibold text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 px-3 py-1.5 rounded-lg border border-white/10 transition-colors"
          >
            <Camera className="w-3.5 h-3.5 text-brand-400" />
            Take Another Selfie
          </Link>
        </div>
      </header>

      {/* Floating Selection Action Bar */}
      {selectedPhotoIds.size > 0 && (
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

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8 pb-28">
        {/* Results Banner */}
        <div className="glass-panel rounded-3xl p-6 sm:p-8 border border-white/10 flex flex-col sm:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-5 text-center sm:text-left">
            {selfieUrl && (
              <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-full overflow-hidden border-2 border-brand-500 shadow-xl shrink-0">
                <img src={selfieUrl} alt="Your selfie" className="w-full h-full object-cover" />
              </div>
            )}
            <div>
              <div className="inline-flex items-center gap-1.5 text-xs text-brand-400 font-semibold mb-1">
                <Sparkles className="w-3.5 h-3.5" />
                Facial Recognition Complete
              </div>
              <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
                {matches.length > 0 ? (
                  <>We Found {matches.length} {matches.length === 1 ? 'Photo' : 'Photos'} of You!</>
                ) : (
                  <>No Matching Photos Found</>
                )}
              </h1>
              <p className="text-xs text-slate-400 mt-0.5">
                {event?.name ? `Event: ${event.name}` : ''}
              </p>
            </div>
          </div>

          {matches.length > 0 && (
            <div className="flex items-center gap-3 shrink-0">
              <button
                onClick={() => handleDownloadZip(matches.map((m) => m.photoId), 'All Photos ZIP')}
                disabled={zipping}
                className="flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-xl shadow-brand-500/25 transition-all hover:scale-105 disabled:opacity-50"
              >
                {zipping ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Generating ZIP...
                  </>
                ) : (
                  <>
                    <FolderArchive className="w-4 h-4" />
                    Download All as ZIP
                  </>
                )}
              </button>
            </div>
          )}
        </div>

        {/* Action / Selection Header */}
        {matches.length > 0 && (
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold text-white">Your Matched Photos</span>
              <span className="text-xs text-slate-400">({matches.length})</span>
            </div>

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
          </div>
        )}

        {/* Matches Grid or Empty State */}
        {matches.length === 0 ? (
          /* Empty State */
          <div className="glass-panel rounded-3xl p-12 text-center border border-white/10 space-y-4 max-w-md mx-auto my-12">
            <div className="w-16 h-16 mx-auto rounded-2xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400">
              <AlertCircle className="w-8 h-8" />
            </div>
            <h3 className="text-lg font-bold text-white">No Matches Found in This Event</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              We couldn't detect a matching face with sufficient confidence. Try snapping another selfie with:
            </p>
            <ul className="text-xs text-slate-300 text-left list-disc list-inside space-y-1 bg-white/[0.02] p-4 rounded-xl border border-white/5">
              <li>Better front-facing lighting</li>
              <li>Neutral expression or slight smile</li>
              <li>Removing sunglasses or heavy face coverings</li>
            </ul>
            <div className="pt-2">
              <Link
                to={`/guest/${publicId}`}
                className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl text-sm font-semibold text-white bg-brand-600 hover:bg-brand-500 transition-colors shadow-lg shadow-brand-500/25"
              >
                <Camera className="w-4 h-4" />
                Try Another Selfie
              </Link>
            </div>
          </div>
        ) : (
          /* Photo Grid */
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            {matches.map((match, index) => {
              const isSelected = selectedPhotoIds.has(match.photoId);

              return (
                <div
                  key={match.photoId}
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
                    onClick={(e) => handleToggleSelect(match.photoId, e)}
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
                    <img
                      src={match.thumbnailUrl}
                      alt={match.originalFilename}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                      loading="lazy"
                    />

                    {/* Similarity Badge */}
                    {match.similarity && (
                      <div className="absolute top-2 left-2 px-2.5 py-0.5 rounded-full bg-black/75 backdrop-blur-md border border-emerald-500/30 text-[10px] font-bold text-emerald-400 z-10 flex items-center gap-1">
                        <Sparkles className="w-3 h-3 text-emerald-400" />
                        {Math.round(match.similarity)}% Match
                      </div>
                    )}

                    {/* Preview overlay on hover */}
                    <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center pointer-events-none">
                      <div className="px-2.5 py-1 rounded-full bg-black/60 backdrop-blur-md text-white text-[11px] font-medium flex items-center gap-1">
                        <Eye className="w-3.5 h-3.5" />
                        Preview
                      </div>
                    </div>
                  </div>

                  <div className="p-3 bg-dark-900/80 border-t border-white/5 flex items-center justify-between gap-2">
                    <div className="truncate text-[11px] text-slate-300 font-medium" title={match.originalFilename}>
                      {match.originalFilename}
                    </div>
                    <button
                      onClick={(e) => handleDownloadSingle(match, e)}
                      disabled={downloadingPhotoId === match.photoId}
                      className="p-1.5 rounded-lg bg-white/5 hover:bg-brand-600 text-slate-300 hover:text-white transition-colors shrink-0"
                      title="Download HD Original"
                    >
                      {downloadingPhotoId === match.photoId ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <Download className="w-3.5 h-3.5" />
                      )}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </main>
    </div>
  );
};
