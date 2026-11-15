import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  Camera,
  Download,
  FolderArchive,
  Sparkles,
  Loader2,
  AlertCircle,
  CheckCircle2,
  CheckSquare,
  Square,
  Eye,
  X,
  Tag,
  User,
  Clock
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
  const [serviceUnavailable, setServiceUnavailable] = useState(false);
  const [zipping, setZipping] = useState(false);
  const [downloadingPhotoId, setDownloadingPhotoId] = useState(null);
  const [toast, setToast] = useState({ message: '', type: 'info' });
  const [sessionExpired, setSessionExpired] = useState(false);
  const [faceDetected, setFaceDetected] = useState(true);
  const [indexingCount, setIndexingCount] = useState(0);

  // Persistent Face-to-Name Tagging
  const [taggedUsername, setTaggedUsername] = useState('');
  const [taggedName, setTaggedName] = useState('');
  const [tagNameInput, setTagNameInput] = useState('');
  const [taggingName, setTaggingName] = useState(false);

  // Selection state
  const [selectedPhotoIds, setSelectedPhotoIds] = useState(new Set());

  // Lightbox preview state
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [lightboxIndex, setLightboxIndex] = useState(0);

  const checkIsSessionExpired = () => {
    const storedExpires = sessionStorage.getItem(`fotofetch_${publicId}_session_expires`);
    if (storedExpires) {
      const expiryTime = new Date(storedExpires).getTime();
      if (!isNaN(expiryTime) && expiryTime < Date.now()) {
        return true;
      }
    }
    return false;
  };

  useEffect(() => {
    // Load cached matches and session info
    const storedMatches = sessionStorage.getItem(`fotofetch_${publicId}_matches`);
    const storedSelfie = sessionStorage.getItem(`fotofetch_${publicId}_selfie`);
    const storedToken = sessionStorage.getItem(`fotofetch_${publicId}_session_token`);
    const storedFaceDetected = sessionStorage.getItem(`fotofetch_${publicId}_face_detected`);
    const storedIndexingCount = sessionStorage.getItem(`fotofetch_${publicId}_indexing_count`);

    if (storedMatches) {
      try {
        const parsed = JSON.parse(storedMatches);
        setMatches(parsed);
        if (parsed.length > 0 && !storedToken) {
          setSessionExpired(true);
        }
      } catch (e) {
        console.error('Error parsing stored matches', e);
      }
    }

    if (checkIsSessionExpired()) {
      setSessionExpired(true);
    }

    if (storedSelfie) {
      setSelfieUrl(storedSelfie);
    }
    if (storedFaceDetected !== null) {
      setFaceDetected(storedFaceDetected === 'true');
    }
    if (storedIndexingCount) {
      setIndexingCount(parseInt(storedIndexingCount, 10) || 0);
    }

    const storedTaggedUsername = sessionStorage.getItem(`fotofetch_${publicId}_tagged_username`);
    const storedTaggedName = sessionStorage.getItem(`fotofetch_${publicId}_tagged_name`);
    if (storedTaggedUsername) {
      setTaggedUsername(storedTaggedUsername);
    }
    if (storedTaggedName) {
      setTaggedName(storedTaggedName);
    }

    // Load event details
    guestApi.getPublicEvent(publicId)
      .then((res) => {
        setEvent(res.data);
        setServiceUnavailable(false);
      })
      .catch((err) => {
        console.error('Failed to load event:', err);
        setServiceUnavailable(true);
      })
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

    const sessionToken = sessionStorage.getItem(`fotofetch_${publicId}_session_token`);
    if (!sessionToken || checkIsSessionExpired()) {
      setSessionExpired(true);
      setToast({
        message: 'Your download session has expired. Please take a quick selfie to refresh.',
        type: 'error'
      });
      return;
    }

    setDownloadingPhotoId(match.photoId);
    try {
      const res = await guestApi.getPhotoDownloadUrl(publicId, match.photoId, sessionToken);
      const downloadUrl = res.data?.downloadUrl || res.data;

      const link = document.createElement('a');
      link.href = downloadUrl;
      link.download = match.originalFilename || 'photo.jpg';
      link.target = '_blank';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setToast({ message: 'Downloading full-quality photo...', type: 'success' });
    } catch (err) {
      if (err.response?.status === 401 || err.response?.status === 403) {
        setSessionExpired(true);
        setToast({
          message: 'Download session expired. Please take a selfie to refresh.',
          type: 'error'
        });
      } else {
        setToast({ message: 'Download failed: ' + (err.response?.data?.message || err.message), type: 'error' });
      }
    } finally {
      setDownloadingPhotoId(null);
    }
  };

  const handleDownloadZip = async (photoIdsToDownload, label = 'ZIP') => {
    if (!photoIdsToDownload || photoIdsToDownload.length === 0 || zipping) return;

    const sessionToken = sessionStorage.getItem(`fotofetch_${publicId}_session_token`);
    if (!sessionToken || checkIsSessionExpired()) {
      setSessionExpired(true);
      setToast({
        message: 'Your download session has expired. Please take a quick selfie to refresh.',
        type: 'error'
      });
      return;
    }

    setZipping(true);
    setToast({ message: `Preparing your ${label} (${photoIdsToDownload.length} photos)...`, type: 'info' });

    try {
      const blob = await guestApi.downloadZipBlob(publicId, photoIdsToDownload, sessionToken);

      const url = window.URL.createObjectURL(new Blob([blob], { type: 'application/zip' }));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `photos-${publicId}.zip`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);

      setToast({ message: `Photos downloaded successfully!`, type: 'success' });
    } catch (err) {
      if (err.response?.status === 401 || err.response?.status === 403) {
        setSessionExpired(true);
        setToast({
          message: 'Download session expired. Please take a selfie to refresh.',
          type: 'error'
        });
      } else {
        setToast({ message: 'ZIP download failed: ' + (err.response?.data?.message || err.message), type: 'error' });
      }
    } finally {
      setZipping(false);
    }
  };

  const handleTagSubmit = async (e) => {
    e.preventDefault();
    if (!tagNameInput.trim() || taggingName) return;

    const sessionToken = sessionStorage.getItem(`fotofetch_${publicId}_session_token`);
    if (!sessionToken || checkIsSessionExpired()) {
      setSessionExpired(true);
      setToast({
        message: 'Your session has expired. Please take another selfie before registering your name.',
        type: 'error'
      });
      return;
    }

    setTaggingName(true);
    try {
      const res = await guestApi.registerGuestTag(publicId, tagNameInput.trim(), sessionToken);
      const identityData = res.data || {};
      const newUsername = identityData.username;
      const newName = identityData.name;

      setTaggedUsername(newUsername);
      setTaggedName(newName);
      sessionStorage.setItem(`fotofetch_${publicId}_tagged_username`, newUsername);
      sessionStorage.setItem(`fotofetch_${publicId}_tagged_name`, newName);

      setToast({
        message: `Registered as @${newUsername}! You can now search with this name anytime.`,
        type: 'success'
      });
      setTagNameInput('');
    } catch (err) {
      setToast({
        message: 'Could not register name: ' + (err.response?.data?.message || err.message),
        type: 'error'
      });
    } finally {
      setTaggingName(false);
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
      <header className="border-b border-white/5 py-3.5 px-4 sm:px-6 bg-dark-950/85 backdrop-blur-md sticky top-0 z-30">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <Link to={`/guest/${publicId}`} className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center">
              <Camera className="w-4 h-4 text-white" />
            </div>
            <span className="font-bold text-white text-base tracking-tight">
              FotoFetch <span className="text-brand-400 text-xs font-semibold">AI</span>
            </span>
          </Link>

          <Link
            to={`/guest/${publicId}`}
            className="flex items-center gap-1.5 text-xs font-semibold text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 px-3 py-1.5 rounded-lg border border-white/10 transition-colors"
          >
            <Camera className="w-3.5 h-3.5 text-brand-400" />
            New Search
          </Link>
        </div>
      </header>

      {/* Floating Selection Action Bar */}
      {selectedPhotoIds.size > 0 && (
        <div className="fixed bottom-4 sm:bottom-6 left-1/2 -translate-x-1/2 z-40 bg-dark-900/95 backdrop-blur-xl border border-brand-500/30 px-4 py-2.5 rounded-2xl shadow-2xl flex items-center gap-3 sm:gap-4 animate-slide-up max-w-[94vw] sm:max-w-fit">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-brand-400" />
            <span className="text-xs font-semibold text-white whitespace-nowrap">
              {selectedPhotoIds.size} selected
            </span>
          </div>

          <div className="h-4 w-px bg-white/15" />

          <button
            onClick={handleSelectAllToggle}
            className="text-xs font-medium text-slate-300 hover:text-white transition-colors whitespace-nowrap"
          >
            {isAllSelected ? 'Deselect' : 'Select All'}
          </button>

          <button
            onClick={() => handleDownloadZip(Array.from(selectedPhotoIds), 'Selected Photos')}
            disabled={zipping}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-md shadow-brand-500/20 transition-all disabled:opacity-50 whitespace-nowrap"
          >
            {zipping ? (
              <>
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                Preparing ZIP...
              </>
            ) : (
              <>
                <FolderArchive className="w-3.5 h-3.5" />
                Download Selected
              </>
            )}
          </button>

          <button
            onClick={() => setSelectedPhotoIds(new Set())}
            className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors shrink-0"
            title="Clear Selection"
            aria-label="Clear selection"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6 sm:py-8 space-y-5 pb-24">
        {/* Service Unavailable Alert */}
        {serviceUnavailable && (
          <div className="p-3.5 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2.5">
              <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
              <p className="text-xs text-slate-200">
                Connection issue. Please check your network and retry.
              </p>
            </div>
            <button
              onClick={() => window.location.reload()}
              className="px-3 py-1 rounded-lg text-xs font-semibold text-white bg-rose-600 hover:bg-rose-500 transition-colors shrink-0"
            >
              Retry
            </button>
          </div>
        )}

        {/* Session Expired Banner */}
        {sessionExpired && (
          <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 flex flex-col sm:flex-row items-center justify-between gap-3">
            <div className="flex items-center gap-2.5 text-center sm:text-left">
              <Clock className="w-4 h-4 text-amber-400 shrink-0" />
              <p className="text-xs text-slate-200">
                Your download session has expired. Take a quick selfie to refresh and download photos.
              </p>
            </div>
            <Link
              to={`/guest/${publicId}`}
              className="px-3.5 py-1.5 rounded-lg text-xs font-semibold text-white bg-amber-600 hover:bg-amber-500 transition-colors shrink-0"
            >
              Take Another Selfie
            </Link>
          </div>
        )}

        {/* Processing Banner */}
        {indexingCount > 0 && (
          <div className="p-3.5 rounded-xl bg-brand-500/10 border border-brand-500/20 flex items-center gap-2.5">
            <Loader2 className="w-3.5 h-3.5 text-brand-400 animate-spin shrink-0" />
            <div className="text-xs text-slate-300">
              {indexingCount} photo{indexingCount > 1 ? 's are' : ' is'} currently being processed by the photographer. More matches may appear shortly.
            </div>
          </div>
        )}

        {/* Results Banner */}
        <div className="glass-panel rounded-2xl p-5 sm:p-7 border border-white/10 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-4 text-center sm:text-left">
            {selfieUrl && (
              <div className="w-14 h-14 sm:w-16 sm:h-16 rounded-full overflow-hidden border-2 border-brand-500 shadow-md shrink-0">
                <img src={selfieUrl} alt="Your selfie" className="w-full h-full object-cover" />
              </div>
            )}
            <div>
              <h1 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
                {matches.length > 0 ? (
                  taggedUsername ? (
                    <>Welcome, {taggedName || 'Guest'}! <span className="text-brand-400 font-mono text-base font-normal">(@{taggedUsername})</span></>
                  ) : (
                    <>Found {matches.length} {matches.length === 1 ? 'Photo' : 'Photos'} of You</>
                  )
                ) : !faceDetected ? (
                  <>No Face Detected</>
                ) : (
                  <>No Matching Photos Found</>
                )}
              </h1>
              <p className="text-xs text-slate-400 mt-0.5">
                {event?.name ? event.name : ''}
              </p>
            </div>
          </div>

          {matches.length > 0 && (
            <div className="flex items-center gap-2.5 w-full sm:w-auto justify-center">
              <button
                onClick={() => handleDownloadZip(matches.map((m) => m.photoId), 'All Photos')}
                disabled={zipping}
                className="w-full sm:w-auto flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-md shadow-brand-500/20 transition-all disabled:opacity-50"
              >
                {zipping ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    Preparing ZIP...
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

        {/* Face-to-Name Tagging Card */}
        {matches.length > 0 && !taggedUsername && (
          <div className="glass-panel rounded-2xl p-4 sm:p-5 border border-white/10 space-y-2.5">
            <div className="flex items-center gap-2">
              <Tag className="w-3.5 h-3.5 text-brand-400" />
              <span className="text-xs font-bold text-white">Remember Me for Next Time</span>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed">
              Want to retrieve your photos later without taking another selfie? Enter your name below to register your handle (e.g. <span className="font-mono text-brand-300">ali</span> or <span className="font-mono text-brand-300">ali2</span>).
            </p>
            <form onSubmit={handleTagSubmit} className="flex flex-col sm:flex-row items-center gap-2 pt-1 max-w-md">
              <div className="relative w-full sm:flex-1">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                  <User className="w-3.5 h-3.5" />
                </div>
                <input
                  type="text"
                  placeholder="Your name (e.g. Ali, Sara)"
                  value={tagNameInput}
                  onChange={(e) => setTagNameInput(e.target.value)}
                  className="w-full pl-9 pr-3 py-2 rounded-xl text-xs bg-white/[0.04] border border-white/10 text-white placeholder-slate-500 focus:outline-none focus:border-brand-500 transition-colors"
                />
              </div>
              <button
                type="submit"
                disabled={taggingName || !tagNameInput.trim()}
                className="w-full sm:w-auto px-4 py-2 rounded-xl text-xs font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 transition-all disabled:opacity-50 flex items-center justify-center gap-1.5 shrink-0 cursor-pointer"
              >
                {taggingName ? (
                  <>
                    <Loader2 className="w-3 h-3 animate-spin" />
                    Saving...
                  </>
                ) : (
                  <>
                    <Tag className="w-3 h-3" />
                    Save My Name
                  </>
                )}
              </button>
            </form>
          </div>
        )}

        {/* Successfully Tagged Badge */}
        {taggedUsername && (
          <div className="p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
              <div className="text-xs text-slate-200">
                Saved as <span className="font-mono font-bold text-emerald-300">@{taggedUsername}</span>. You can search by this name anytime!
              </div>
            </div>
            <span className="text-[10px] font-mono font-semibold bg-emerald-500/20 text-emerald-300 px-2 py-0.5 rounded-full shrink-0">
              Active
            </span>
          </div>
        )}

        {/* Action / Selection Header */}
        {matches.length > 0 && (
          <div className="flex items-center justify-between pt-1">
            <div className="flex items-center gap-2">
              <span className="text-xs sm:text-sm font-semibold text-white">Matched Photos</span>
              <span className="text-xs text-slate-400">({matches.length})</span>
            </div>

            <button
              onClick={handleSelectAllToggle}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-white/5 hover:bg-white/10 text-slate-300 hover:text-white border border-white/10 transition-colors"
            >
              {isAllSelected ? (
                <>
                  <CheckSquare className="w-3.5 h-3.5 text-brand-400" />
                  Deselect
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
          !faceDetected ? (
            /* No Face Detected */
            <div className="glass-panel rounded-2xl p-8 sm:p-12 text-center border border-white/10 space-y-4 max-w-md mx-auto my-6">
              <div className="w-14 h-14 mx-auto rounded-2xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400">
                <AlertCircle className="w-7 h-7" />
              </div>
              <h3 className="text-base sm:text-lg font-bold text-white">No Face Detected</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                Could not find a clear face in your selfie. Try again in good lighting looking directly at the camera.
              </p>
              <div className="pt-2">
                <Link
                  to={`/guest/${publicId}`}
                  className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold text-white bg-brand-600 hover:bg-brand-500 transition-colors shadow-md shadow-brand-500/20"
                >
                  <Camera className="w-4 h-4" />
                  Take Another Selfie
                </Link>
              </div>
            </div>
          ) : (
            /* Face Detected, but no matches */
            <div className="glass-panel rounded-2xl p-8 sm:p-12 text-center border border-white/10 space-y-4 max-w-md mx-auto my-6">
              <div className="w-14 h-14 mx-auto rounded-2xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400">
                <AlertCircle className="w-7 h-7" />
              </div>
              <h3 className="text-base sm:text-lg font-bold text-white">No Matches Found</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                We couldn't find matching photos in this event yet. Try taking another selfie with front lighting.
              </p>
              {indexingCount > 0 && (
                <p className="text-[11px] text-brand-300 bg-brand-500/10 p-2.5 rounded-xl border border-brand-500/20">
                  {indexingCount} photo{indexingCount > 1 ? 's are' : ' is'} still being processed. Check back soon!
                </p>
              )}
              <div className="pt-2">
                <Link
                  to={`/guest/${publicId}`}
                  className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold text-white bg-brand-600 hover:bg-brand-500 transition-colors shadow-md shadow-brand-500/20"
                >
                  <Camera className="w-4 h-4" />
                  Try Another Selfie
                </Link>
              </div>
            </div>
          )
        ) : (
          /* Responsive Photo Grid */
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3 sm:gap-4">
            {matches.map((match, index) => {
              const isSelected = selectedPhotoIds.has(match.photoId);

              return (
                <div
                  key={match.photoId}
                  onClick={() => handleOpenLightbox(index)}
                  className={`glass-card rounded-xl overflow-hidden border group flex flex-col justify-between relative transition-all duration-200 cursor-pointer ${
                    isSelected
                      ? 'border-brand-500 ring-2 ring-brand-500/70 bg-brand-500/[0.04]'
                      : 'border-white/10 hover:border-white/20'
                  }`}
                >
                  {/* Selection Checkbox */}
                  <button
                    type="button"
                    onClick={(e) => handleToggleSelect(match.photoId, e)}
                    aria-label={isSelected ? 'Deselect photo' : 'Select photo'}
                    className={`absolute top-2 right-2 p-1.5 rounded-lg z-20 transition-all ${
                      isSelected
                        ? 'bg-brand-600 text-white shadow-md shadow-brand-500/40 ring-2 ring-white/30 opacity-100'
                        : selectedPhotoIds.size > 0
                        ? 'bg-black/60 text-slate-300 opacity-90 backdrop-blur-md'
                        : 'bg-black/60 text-slate-300 hover:text-white opacity-0 group-hover:opacity-100 backdrop-blur-md'
                    }`}
                    title={isSelected ? 'Deselect photo' : 'Select photo'}
                  >
                    {isSelected ? (
                      <CheckSquare className="w-3.5 h-3.5 text-white" />
                    ) : (
                      <Square className="w-3.5 h-3.5" />
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
                      <div className="absolute top-2 left-2 px-2 py-0.5 rounded-full bg-black/75 backdrop-blur-md border border-emerald-500/30 text-[10px] font-semibold text-emerald-400 z-10 flex items-center gap-1">
                        <Sparkles className="w-3 h-3 text-emerald-400" />
                        {Math.round(match.similarity)}%
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

                  <div className="p-2.5 bg-dark-900/80 border-t border-white/5 flex items-center justify-between gap-2">
                    <div className="truncate text-[11px] text-slate-300 font-medium" title={match.originalFilename}>
                      {match.originalFilename}
                    </div>
                    <button
                      onClick={(e) => handleDownloadSingle(match, e)}
                      disabled={downloadingPhotoId === match.photoId}
                      aria-label="Download photo"
                      className="p-1.5 rounded-lg bg-white/5 hover:bg-brand-600 text-slate-300 hover:text-white transition-colors shrink-0"
                      title="Download HD"
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

