import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  UploadCloud,
  QrCode,
  Calendar,
  ArrowLeft,
  AlertCircle,
  Loader2,
  Download,
  Users,
  RefreshCw,
  ExternalLink,
  Copy,
  Check,
  Trash2,
  CheckSquare,
  Square,
  Eye,
  AlertTriangle,
  X
} from 'lucide-react';
import { eventApi } from '../api/eventApi';
import { photoApi } from '../api/photoApi';
import { Navbar } from '../components/Navbar';
import { UploadModal } from '../components/UploadModal';
import { QrCodeModal } from '../components/QrCodeModal';
import { Toast } from '../components/Toast';
import { ImageGalleryLightbox } from '../components/ImageGalleryLightbox';

export const EventManagePage = () => {
  const { id } = useParams();
  const [event, setEvent] = useState(null);
  const [photos, setPhotos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [qrModalOpen, setQrModalOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [toast, setToast] = useState({ message: '', type: 'info' });

  // Bulk selection state
  const [selectedPhotoIds, setSelectedPhotoIds] = useState(new Set());

  // Sliding gallery / Lightbox state
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [lightboxIndex, setLightboxIndex] = useState(0);

  // Deletion modal state
  const [deleteModal, setDeleteModal] = useState({
    isOpen: false,
    type: 'single', // 'single' | 'bulk'
    photo: null,
    count: 0,
  });
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    fetchEventAndPhotos();
  }, [id]);

  // Auto-poll while photos are being processed asynchronously
  useEffect(() => {
    const isProcessing = photos.some(
      (p) =>
        p.uploadStatus === 'UPLOADED' &&
        (p.thumbnailStatus === 'PENDING' ||
          p.thumbnailStatus === 'PROCESSING' ||
          p.indexingStatus === 'PENDING' ||
          p.indexingStatus === 'INDEXING')
    );

    if (!isProcessing) return;

    const interval = setInterval(async () => {
      try {
        const [eventRes, photosRes] = await Promise.all([
          eventApi.getEvent(id),
          photoApi.getEventPhotos(id),
        ]);
        setEvent(eventRes.data);
        setPhotos(photosRes.data || []);
      } catch (err) {
        console.error('Silent polling error:', err);
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [id, photos]);

  const fetchEventAndPhotos = async () => {
    try {
      const [eventRes, photosRes] = await Promise.all([
        eventApi.getEvent(id),
        photoApi.getEventPhotos(id),
      ]);
      setEvent(eventRes.data);
      setPhotos(photosRes.data || []);
    } catch (err) {
      setToast({ message: 'Failed to load event: ' + err.message, type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const handleCopyPublicLink = () => {
    if (!event) return;
    const url = `${window.location.origin}/guest/${event.publicId}`;
    navigator.clipboard.writeText(url);
    setCopied(true);
    setToast({ message: 'Guest link copied!', type: 'success' });
    setTimeout(() => setCopied(false), 2000);
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

  const isAllSelected = photos.length > 0 && selectedPhotoIds.size === photos.length;

  const handleSelectAllToggle = () => {
    if (isAllSelected) {
      setSelectedPhotoIds(new Set());
    } else {
      setSelectedPhotoIds(new Set(photos.map((p) => p.id)));
    }
  };

  // Lightbox handler
  const handleOpenLightbox = (index) => {
    setLightboxIndex(index);
    setLightboxOpen(true);
  };

  // Delete modal prompt handlers
  const promptDeleteSingle = (photo, e) => {
    e?.stopPropagation();
    setDeleteModal({
      isOpen: true,
      type: 'single',
      photo,
      count: 1,
    });
  };

  const promptDeleteBulk = () => {
    if (selectedPhotoIds.size === 0) return;
    setDeleteModal({
      isOpen: true,
      type: 'bulk',
      photo: null,
      count: selectedPhotoIds.size,
    });
  };

  // Execute deletion
  const executeDelete = async () => {
    setIsDeleting(true);
    try {
      if (deleteModal.type === 'single' && deleteModal.photo) {
        const photoToDelete = deleteModal.photo;
        await photoApi.deletePhoto(id, photoToDelete.id);

        setPhotos((prev) => prev.filter((p) => p.id !== photoToDelete.id));
        setSelectedPhotoIds((prev) => {
          const next = new Set(prev);
          next.delete(photoToDelete.id);
          return next;
        });

        if (lightboxOpen) {
          if (photos.length <= 1) {
            setLightboxOpen(false);
          } else if (lightboxIndex >= photos.length - 1) {
            setLightboxIndex(Math.max(0, photos.length - 2));
          }
        }

        setToast({
          message: `Photo deleted successfully`,
          type: 'success',
        });
      } else if (deleteModal.type === 'bulk') {
        const idsToDelete = Array.from(selectedPhotoIds);
        await photoApi.bulkDeletePhotos(id, idsToDelete);

        setPhotos((prev) => prev.filter((p) => !selectedPhotoIds.has(p.id)));
        setSelectedPhotoIds(new Set());

        if (lightboxOpen) {
          setLightboxOpen(false);
        }

        setToast({
          message: `${idsToDelete.length} photos deleted`,
          type: 'success',
        });
      }
    } catch (err) {
      setToast({
        message: 'Delete failed: ' + (err.response?.data?.message || err.message),
        type: 'error',
      });
    } finally {
      setIsDeleting(false);
      setDeleteModal({ isOpen: false, type: 'single', photo: null, count: 0 });
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-dark-950 flex flex-col items-center justify-center text-slate-400 gap-2.5">
        <Loader2 className="w-8 h-8 animate-spin text-brand-500" />
        <p className="text-xs sm:text-sm">Loading event gallery...</p>
      </div>
    );
  }

  if (!event) {
    return (
      <div className="min-h-screen bg-dark-950 flex flex-col items-center justify-center p-4">
        <p className="text-base text-white mb-3">Event not found or unauthorized</p>
        <Link to="/dashboard" className="text-brand-400 text-xs sm:text-sm hover:underline">
          Return to Dashboard
        </Link>
      </div>
    );
  }

  const indexedCount = photos.filter((p) => p.indexingStatus === 'COMPLETED').length;
  const pendingCount = photos.filter((p) => p.indexingStatus === 'INDEXING' || p.indexingStatus === 'PENDING').length;
  const failedCount = photos.filter((p) => p.indexingStatus === 'FAILED').length;

  return (
    <div className="min-h-screen flex flex-col bg-dark-950 text-slate-100">
      <Navbar />

      <Toast
        message={toast.message}
        type={toast.type}
        onClose={() => setToast({ message: '', type: 'info' })}
      />

      {/* Upload Modal */}
      <UploadModal
        isOpen={uploadModalOpen}
        onClose={() => setUploadModalOpen(false)}
        eventId={id}
        onUploadComplete={fetchEventAndPhotos}
      />

      {/* QR Code Modal */}
      <QrCodeModal
        isOpen={qrModalOpen}
        onClose={() => setQrModalOpen(false)}
        eventName={event.name}
        publicId={event.publicId}
      />

      {/* Sliding Image Gallery Lightbox */}
      <ImageGalleryLightbox
        isOpen={lightboxOpen}
        photos={photos}
        currentIndex={lightboxIndex}
        onClose={() => setLightboxOpen(false)}
        onNavigate={(newIdx) => setLightboxIndex(newIdx)}
        onDelete={(photo) => promptDeleteSingle(photo)}
      />

      {/* Deletion Confirmation Modal */}
      {deleteModal.isOpen && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-fade-in">
          <div className="glass-panel border border-white/10 rounded-2xl p-5 sm:p-6 max-w-sm w-full shadow-2xl space-y-4 animate-scale-up">
            <div className="flex items-center gap-3 text-rose-400">
              <div className="p-2.5 rounded-xl bg-rose-500/10 border border-rose-500/20">
                <AlertTriangle className="w-5 h-5 text-rose-400" />
              </div>
              <div>
                <h3 className="text-sm sm:text-base font-bold text-white">
                  {deleteModal.type === 'bulk' ? `Delete ${deleteModal.count} Photos?` : 'Delete Photo?'}
                </h3>
                <p className="text-xs text-slate-400">This action cannot be undone.</p>
              </div>
            </div>

            <p className="text-xs text-slate-300 leading-relaxed">
              {deleteModal.type === 'bulk' ? (
                <>Permanently delete {deleteModal.count} selected photos from this event?</>
              ) : (
                <>Permanently delete <strong className="text-white">{deleteModal.photo?.originalFilename}</strong> from this event?</>
              )}
            </p>

            <div className="flex items-center justify-end gap-2.5 pt-2">
              <button
                disabled={isDeleting}
                onClick={() => setDeleteModal({ isOpen: false, type: 'single', photo: null, count: 0 })}
                className="px-3.5 py-2 rounded-xl text-xs font-semibold text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 transition-colors disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                disabled={isDeleting}
                onClick={executeDelete}
                className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-semibold text-white bg-rose-600 hover:bg-rose-500 transition-colors shadow-md shadow-rose-600/30 disabled:opacity-50"
              >
                {isDeleting ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    Deleting...
                  </>
                ) : (
                  <>
                    <Trash2 className="w-3.5 h-3.5" />
                    Delete
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

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
            onClick={promptDeleteBulk}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold text-white bg-rose-600 hover:bg-rose-500 transition-colors shadow-md shadow-rose-600/30 whitespace-nowrap"
          >
            <Trash2 className="w-3.5 h-3.5" />
            Delete
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

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 pt-24 sm:pt-28 pb-20">
        {/* Back button */}
        <Link
          to="/dashboard"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-400 hover:text-white transition-colors mb-5"
        >
          <ArrowLeft className="w-3.5 h-3.5" /> Back to Dashboard
        </Link>

        {/* Event Header Banner */}
        <div className="glass-panel rounded-2xl p-5 sm:p-7 border border-white/10 space-y-5">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-5">
            <div className="space-y-1.5 max-w-2xl">
              <div className="flex items-center gap-2.5">
                <span className="text-[11px] text-slate-400 flex items-center gap-1.5 font-medium">
                  <Calendar className="w-3.5 h-3.5 text-brand-400" />
                  {event.eventDate}
                </span>
              </div>
              <h1 className="text-2xl sm:text-3xl font-bold text-white tracking-tight">{event.name}</h1>
              {event.description && (
                <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">{event.description}</p>
              )}
            </div>

            {/* Action Buttons */}
            <div className="flex flex-wrap items-center gap-2 sm:gap-2.5">
              <button
                onClick={() => setQrModalOpen(true)}
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-semibold bg-white/5 hover:bg-white/10 text-white border border-white/10 transition-colors"
              >
                <QrCode className="w-3.5 h-3.5 text-brand-400" />
                QR Code
              </button>

              <button
                onClick={handleCopyPublicLink}
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-semibold bg-white/5 hover:bg-white/10 text-white border border-white/10 transition-colors"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                Copy Link
              </button>

              <a
                href={`/guest/${event.publicId}`}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-semibold bg-white/5 hover:bg-white/10 text-slate-300 hover:text-white border border-white/10 transition-colors"
              >
                Guest View <ExternalLink className="w-3 h-3" />
              </a>

              <button
                onClick={() => setUploadModalOpen(true)}
                className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-md shadow-brand-500/20 transition-all"
              >
                <UploadCloud className="w-4 h-4" />
                Upload Photos
              </button>
            </div>
          </div>

          {/* Stats Badges */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-4 border-t border-white/5">
            <div className="bg-white/[0.02] p-3.5 rounded-xl border border-white/5">
              <span className="text-[10px] uppercase tracking-wider text-slate-400 font-medium">Total Photos</span>
              <p className="text-lg sm:text-xl font-bold text-white mt-0.5">{photos.length}</p>
            </div>
            <div className="bg-emerald-500/[0.04] p-3.5 rounded-xl border border-emerald-500/10">
              <span className="text-[10px] uppercase tracking-wider text-emerald-400 font-medium">Ready</span>
              <p className="text-lg sm:text-xl font-bold text-emerald-400 mt-0.5">{indexedCount}</p>
            </div>
            <div className="bg-amber-500/[0.04] p-3.5 rounded-xl border border-amber-500/10">
              <span className="text-[10px] uppercase tracking-wider text-amber-400 font-medium">Processing</span>
              <p className="text-lg sm:text-xl font-bold text-amber-400 mt-0.5">{pendingCount}</p>
            </div>
            <div className="bg-rose-500/[0.04] p-3.5 rounded-xl border border-rose-500/10">
              <span className="text-[10px] uppercase tracking-wider text-rose-400 font-medium">Failed</span>
              <p className="text-lg sm:text-xl font-bold text-rose-400 mt-0.5">{failedCount}</p>
            </div>
          </div>
        </div>

        {/* Gallery Section */}
        <div className="mt-8 space-y-4 sm:space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <h2 className="text-lg sm:text-xl font-bold text-white tracking-tight">Event Photos</h2>
              <span className="text-xs text-slate-400">({photos.length})</span>
            </div>

            <div className="flex items-center gap-2.5">
              {photos.length > 0 && (
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
              )}

              <button
                onClick={fetchEventAndPhotos}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs text-slate-400 hover:text-white transition-colors"
              >
                <RefreshCw className="w-3.5 h-3.5" /> Refresh
              </button>
            </div>
          </div>

          {photos.length === 0 ? (
            /* Empty gallery */
            <div className="glass-panel rounded-2xl p-8 sm:p-12 text-center border border-white/10 space-y-4 max-w-md mx-auto my-6">
              <div className="w-14 h-14 mx-auto rounded-2xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400">
                <UploadCloud className="w-7 h-7" />
              </div>
              <div>
                <h3 className="text-base sm:text-lg font-bold text-white">No Photos Uploaded</h3>
                <p className="text-xs text-slate-400 leading-relaxed mt-1 max-w-xs mx-auto">
                  Upload event photos to enable facial recognition search for your guests.
                </p>
              </div>
              <button
                onClick={() => setUploadModalOpen(true)}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold text-white bg-brand-600 hover:bg-brand-500 transition-colors shadow-md shadow-brand-500/20"
              >
                <UploadCloud className="w-4 h-4" />
                Upload Photos Now
              </button>
            </div>
          ) : (
            /* Responsive Photos Grid */
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3 sm:gap-4">
              {photos.map((photo, index) => {
                const isSelected = selectedPhotoIds.has(photo.id);

                return (
                  <div
                    key={photo.id}
                    className={`glass-card rounded-xl overflow-hidden border group flex flex-col relative transition-all duration-200 cursor-pointer ${
                      isSelected
                        ? 'border-brand-500 ring-2 ring-brand-500/60 bg-brand-500/[0.04]'
                        : 'border-white/10 hover:border-white/20'
                    }`}
                    onClick={() => handleOpenLightbox(index)}
                  >
                    {/* Checkbox Button */}
                    <button
                      type="button"
                      onClick={(e) => handleToggleSelect(photo.id, e)}
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
                      {photo.thumbnailUrl ? (
                        <img
                          src={photo.thumbnailUrl}
                          alt={photo.originalFilename}
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                          loading="lazy"
                        />
                      ) : photo.thumbnailStatus === 'FAILED' ? (
                        <div className="w-full h-full flex flex-col items-center justify-center text-slate-500 text-xs p-2 text-center bg-white/[0.02]">
                          <AlertCircle className="w-5 h-5 mb-1 text-rose-400" />
                          <span className="text-[10px] text-slate-400">Failed</span>
                        </div>
                      ) : (
                        <div className="w-full h-full flex flex-col items-center justify-center text-slate-500 text-xs p-2 text-center">
                          <Loader2 className="w-5 h-5 animate-spin mb-1 text-brand-400" />
                          <span className="text-[10px] text-slate-400">Processing...</span>
                        </div>
                      )}

                      {/* Click overlay hint on hover */}
                      <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center pointer-events-none">
                        <div className="px-2.5 py-1 rounded-full bg-black/60 backdrop-blur-md text-white text-[11px] font-medium flex items-center gap-1">
                          <Eye className="w-3.5 h-3.5" />
                          Preview
                        </div>
                      </div>

                      {/* Faces Badge */}
                      {photo.facesFound > 0 && (
                        <div className="absolute top-2 left-2 px-2 py-0.5 rounded-full bg-black/70 backdrop-blur-md border border-white/10 text-[10px] font-medium text-emerald-300 flex items-center gap-1 z-10">
                          <Users className="w-3 h-3 text-emerald-400" />
                          {photo.facesFound}
                        </div>
                      )}

                      {/* Action buttons on Hover */}
                      <div className="absolute bottom-2 right-2 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity z-10">
                        {photo.downloadUrl && (
                          <a
                            href={photo.downloadUrl}
                            download={photo.originalFilename}
                            target="_blank"
                            rel="noopener noreferrer"
                            onClick={(e) => e.stopPropagation()}
                            className="p-1.5 rounded-lg bg-black/75 hover:bg-black text-white backdrop-blur-md transition-colors"
                            title="Download HD"
                          >
                            <Download className="w-3.5 h-3.5" />
                          </a>
                        )}

                        <button
                          type="button"
                          onClick={(e) => promptDeleteSingle(photo, e)}
                          className="p-1.5 rounded-lg bg-black/75 hover:bg-rose-600 text-slate-300 hover:text-white backdrop-blur-md transition-colors"
                          title="Delete Photo"
                          aria-label="Delete photo"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>

                    <div className="p-2 bg-dark-900/80 border-t border-white/5 space-y-0.5">
                      <p className="text-[11px] font-medium text-slate-300 truncate" title={photo.originalFilename}>
                        {photo.originalFilename}
                      </p>
                      <div className="flex items-center justify-between text-[10px] text-slate-500">
                        <span>{photo.fileSize ? `${(photo.fileSize / (1024 * 1024)).toFixed(1)} MB` : ''}</span>
                        <span className={`font-semibold ${
                          photo.indexingStatus === 'COMPLETED' ? 'text-emerald-400' :
                          photo.indexingStatus === 'INDEXING' ? 'text-amber-400' :
                          photo.indexingStatus === 'NO_FACES_DETECTED' ? 'text-slate-400' :
                          photo.indexingStatus === 'FAILED' ? 'text-rose-400' : 'text-indigo-400'
                        }`}>
                          {photo.indexingStatus === 'COMPLETED' ? 'Ready' :
                           photo.indexingStatus === 'INDEXING' ? 'Processing' :
                           photo.indexingStatus === 'NO_FACES_DETECTED' ? 'No faces' :
                           photo.indexingStatus === 'FAILED' ? 'Failed' : 'Uploaded'}
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </main>
    </div>
  );
};

