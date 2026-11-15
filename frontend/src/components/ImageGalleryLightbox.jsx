import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  X,
  ChevronLeft,
  ChevronRight,
  Download,
  Trash2,
  Users,
  Loader2,
  Maximize2,
  Minimize2,
  AlertTriangle,
  Sparkles
} from 'lucide-react';

export const ImageGalleryLightbox = ({
  isOpen,
  photos = [],
  currentIndex = 0,
  onClose,
  onNavigate,
  onDelete,
}) => {
  const [isImageLoading, setIsImageLoading] = useState(true);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const touchStartXRef = useRef(null);
  const filmstripRef = useRef(null);
  const activeThumbRef = useRef(null);

  const currentPhoto = photos[currentIndex] || null;

  // Handle keyboard navigation
  const handleKeyDown = useCallback(
    (e) => {
      if (!isOpen) return;

      if (e.key === 'Escape') {
        if (deleteConfirmOpen) {
          setDeleteConfirmOpen(false);
        } else {
          onClose();
        }
      } else if (e.key === 'ArrowLeft') {
        if (currentIndex > 0) {
          onNavigate(currentIndex - 1);
        }
      } else if (e.key === 'ArrowRight') {
        if (currentIndex < photos.length - 1) {
          onNavigate(currentIndex + 1);
        }
      }
    },
    [isOpen, deleteConfirmOpen, currentIndex, photos.length, onNavigate, onClose]
  );

  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      document.body.style.overflow = 'unset';
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, handleKeyDown]);

  // Reset loading state and scroll thumbnail into view when current index changes
  useEffect(() => {
    setIsImageLoading(true);
    setDeleteConfirmOpen(false);

    if (activeThumbRef.current && filmstripRef.current) {
      activeThumbRef.current.scrollIntoView({
        behavior: 'smooth',
        inline: 'center',
        block: 'nearest',
      });
    }
  }, [currentIndex]);

  // Touch gesture handlers
  const handleTouchStart = (e) => {
    touchStartXRef.current = e.touches[0].clientX;
  };

  const handleTouchEnd = (e) => {
    if (touchStartXRef.current === null) return;
    const touchEndX = e.changedTouches[0].clientX;
    const diff = touchStartXRef.current - touchEndX;

    // Threshold for swipe
    if (Math.abs(diff) > 50) {
      if (diff > 0 && currentIndex < photos.length - 1) {
        onNavigate(currentIndex + 1);
      } else if (diff < 0 && currentIndex > 0) {
        onNavigate(currentIndex - 1);
      }
    }
    touchStartXRef.current = null;
  };

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {});
      setIsFullscreen(true);
    } else {
      if (document.exitFullscreen) {
        document.exitFullscreen().catch(() => {});
        setIsFullscreen(false);
      }
    }
  };

  if (!isOpen || !currentPhoto) return null;

  const hasPrev = currentIndex > 0;
  const hasNext = currentIndex < photos.length - 1;

  // Use full HD downloadUrl if available, otherwise fallback to thumbnail
  const displayImageUrl = currentPhoto.downloadUrl || currentPhoto.thumbnailUrl;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/95 backdrop-blur-xl flex flex-col justify-between select-none animate-fade-in"
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      {/* Top Header Bar */}
      <div className="flex items-center justify-between px-3 sm:px-6 py-3 bg-gradient-to-b from-black/80 via-black/40 to-transparent z-20">
        <div className="flex items-center gap-2.5 min-w-0 pr-2">
          <span className="px-2.5 py-1 rounded-full bg-white/10 text-[11px] font-mono font-medium text-slate-200 shrink-0">
            {currentIndex + 1} / {photos.length}
          </span>
          <div className="truncate max-w-[130px] sm:max-w-md">
            <p className="text-xs sm:text-sm font-semibold text-white truncate" title={currentPhoto.originalFilename}>
              {currentPhoto.originalFilename}
            </p>
            <div className="flex items-center gap-2 text-[10px] sm:text-[11px] text-slate-400">
              {currentPhoto.fileSize && (
                <span>{(currentPhoto.fileSize / (1024 * 1024)).toFixed(1)} MB</span>
              )}
              {currentPhoto.facesFound > 0 && (
                <span className="flex items-center gap-1 text-emerald-400">
                  <Users className="w-3 h-3" />
                  {currentPhoto.facesFound}
                </span>
              )}
              {currentPhoto.similarity != null && (
                <span className="flex items-center gap-1 text-brand-300 font-mono font-medium">
                  <Sparkles className="w-3 h-3 text-brand-400" />
                  {Math.round(currentPhoto.similarity)}% Match
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
          {currentPhoto.downloadUrl && (
            <a
              href={currentPhoto.downloadUrl}
              download={currentPhoto.originalFilename}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-white bg-white/10 hover:bg-white/20 transition-colors"
              title="Download HD"
            >
              <Download className="w-3.5 h-3.5 text-brand-400" />
              <span className="hidden sm:inline">Download</span>
            </a>
          )}

          {onDelete && (
            <button
              onClick={() => setDeleteConfirmOpen(true)}
              aria-label="Delete photo"
              className="p-1.5 sm:p-2 rounded-lg text-slate-300 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
              title="Delete Photo"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}

          <button
            onClick={toggleFullscreen}
            aria-label="Toggle fullscreen"
            className="p-1.5 sm:p-2 rounded-lg text-slate-300 hover:text-white hover:bg-white/10 transition-colors hidden sm:block"
            title="Toggle Fullscreen"
          >
            {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
          </button>

          <button
            onClick={onClose}
            aria-label="Close preview"
            className="p-1.5 sm:p-2 rounded-lg text-slate-300 hover:text-white hover:bg-white/10 transition-colors"
            title="Close (Esc)"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Main Image Viewport */}
      <div className="relative flex-1 flex items-center justify-center p-2 sm:p-6 overflow-hidden">
        {/* Previous Button */}
        {hasPrev && (
          <button
            onClick={() => onNavigate(currentIndex - 1)}
            aria-label="Previous photo"
            className="absolute left-2 sm:left-6 z-20 p-2.5 sm:p-3.5 rounded-full bg-black/60 hover:bg-black/90 text-white/80 hover:text-white border border-white/10 backdrop-blur-md transition-all shadow-xl"
            title="Previous (Left Arrow)"
          >
            <ChevronLeft className="w-5 h-5 sm:w-6 sm:h-6" />
          </button>
        )}

        {/* Next Button */}
        {hasNext && (
          <button
            onClick={() => onNavigate(currentIndex + 1)}
            aria-label="Next photo"
            className="absolute right-2 sm:right-6 z-20 p-2.5 sm:p-3.5 rounded-full bg-black/60 hover:bg-black/90 text-white/80 hover:text-white border border-white/10 backdrop-blur-md transition-all shadow-xl"
            title="Next (Right Arrow)"
          >
            <ChevronRight className="w-5 h-5 sm:w-6 sm:h-6" />
          </button>
        )}

        {/* Active Image Container */}
        <div className="relative max-h-full max-w-full flex items-center justify-center">
          {isImageLoading && (
            <div className="absolute inset-0 flex items-center justify-center z-10">
              <Loader2 className="w-8 h-8 animate-spin text-brand-400" />
            </div>
          )}

          {displayImageUrl ? (
            <img
              key={currentPhoto.id || currentPhoto.photoId || currentIndex}
              src={displayImageUrl}
              alt={currentPhoto.originalFilename}
              className={`max-h-[68vh] sm:max-h-[75vh] max-w-[94vw] sm:max-w-[85vw] object-contain rounded-lg shadow-2xl transition-opacity duration-200 ${
                isImageLoading ? 'opacity-30' : 'opacity-100'
              }`}
              onLoad={() => setIsImageLoading(false)}
              onError={() => setIsImageLoading(false)}
            />
          ) : (
            <div className="text-center text-slate-400 p-6 glass-panel rounded-2xl">
              <p className="text-xs sm:text-sm">Preview not available yet.</p>
            </div>
          )}
        </div>
      </div>

      {/* Bottom Filmstrip Carousel */}
      <div className="px-3 py-2 bg-gradient-to-t from-black/90 via-black/60 to-transparent z-20">
        <div
          ref={filmstripRef}
          className="flex items-center gap-1.5 sm:gap-2 overflow-x-auto py-1 px-1 no-scrollbar max-w-3xl mx-auto scroll-smooth"
        >
          {photos.map((photo, index) => {
            const isActive = index === currentIndex;
            return (
              <button
                key={photo.id || photo.photoId || index}
                ref={isActive ? activeThumbRef : null}
                onClick={() => onNavigate(index)}
                aria-label={`Photo ${index + 1}`}
                className={`flex-shrink-0 w-11 h-11 sm:w-13 sm:h-13 rounded-lg overflow-hidden border-2 transition-all relative ${
                  isActive
                    ? 'border-brand-500 scale-105 shadow-md shadow-brand-500/30'
                    : 'border-transparent opacity-50 hover:opacity-100'
                }`}
                title={photo.originalFilename}
              >
                {photo.thumbnailUrl ? (
                  <img
                    src={photo.thumbnailUrl}
                    alt={photo.originalFilename}
                    className="w-full h-full object-cover"
                    loading="lazy"
                  />
                ) : (
                  <div className="w-full h-full bg-dark-900 flex items-center justify-center text-[10px] text-slate-500">
                    {index + 1}
                  </div>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Delete Confirmation Dialog */}
      {deleteConfirmOpen && (
        <div className="fixed inset-0 z-60 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="glass-panel border border-white/10 rounded-2xl p-6 max-w-sm w-full shadow-2xl space-y-4 animate-scale-up">
            <div className="flex items-center gap-3 text-rose-400">
              <div className="p-2.5 rounded-xl bg-rose-500/10 border border-rose-500/20">
                <AlertTriangle className="w-5 h-5 text-rose-400" />
              </div>
              <div>
                <h3 className="text-sm sm:text-base font-bold text-white">Delete Photo?</h3>
                <p className="text-xs text-slate-400">This action cannot be undone.</p>
              </div>
            </div>

            <p className="text-xs text-slate-300 leading-relaxed">
              Permanently delete <strong className="text-white">{currentPhoto.originalFilename}</strong> from this event gallery?
            </p>

            <div className="flex items-center justify-end gap-2.5 pt-2">
              <button
                onClick={() => setDeleteConfirmOpen(false)}
                className="px-3.5 py-2 rounded-xl text-xs font-semibold text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  setDeleteConfirmOpen(false);
                  onDelete(currentPhoto);
                }}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-white bg-rose-600 hover:bg-rose-500 transition-colors shadow-md shadow-rose-600/30"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

