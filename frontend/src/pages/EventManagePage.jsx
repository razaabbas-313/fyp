import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  UploadCloud,
  QrCode,
  Share2,
  Calendar,
  Layers,
  ArrowLeft,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Download,
  Users,
  RefreshCw,
  ExternalLink,
  Copy,
  Check
} from 'lucide-react';
import { eventApi } from '../api/eventApi';
import { photoApi } from '../api/photoApi';
import { Navbar } from '../components/Navbar';
import { UploadModal } from '../components/UploadModal';
import { QrCodeModal } from '../components/QrCodeModal';
import { Toast } from '../components/Toast';

export const EventManagePage = () => {
  const { id } = useParams();
  const [event, setEvent] = useState(null);
  const [photos, setPhotos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [qrModalOpen, setQrModalOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [toast, setToast] = useState({ message: '', type: 'info' });

  useEffect(() => {
    fetchEventAndPhotos();
  }, [id]);

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
    setToast({ message: 'Public guest link copied to clipboard!', type: 'success' });
    setTimeout(() => setCopied(false), 2000);
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-dark-950 flex flex-col items-center justify-center text-slate-400 gap-3">
        <Loader2 className="w-8 h-8 animate-spin text-brand-500" />
        <p className="text-sm">Loading event gallery...</p>
      </div>
    );
  }

  if (!event) {
    return (
      <div className="min-h-screen bg-dark-950 flex flex-col items-center justify-center p-4">
        <p className="text-lg text-white mb-4">Event not found or unauthorized</p>
        <Link to="/dashboard" className="text-brand-400 text-sm hover:underline">
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

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 pt-28 pb-20">
        {/* Back button */}
        <Link
          to="/dashboard"
          className="inline-flex items-center gap-2 text-xs font-semibold text-slate-400 hover:text-white transition-colors mb-6"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Dashboard
        </Link>

        {/* Event Header Banner */}
        <div className="glass-panel rounded-3xl p-6 sm:p-8 border border-white/10 space-y-6">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
            <div className="space-y-2 max-w-2xl">
              <div className="flex items-center gap-3">
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-brand-500/10 border border-brand-500/20 text-brand-300 font-mono">
                  ID: {event.publicId}
                </span>
                <span className="text-xs text-slate-400 flex items-center gap-1.5">
                  <Calendar className="w-3.5 h-3.5 text-brand-400" />
                  {event.eventDate}
                </span>
              </div>
              <h1 className="text-3xl font-extrabold text-white tracking-tight">{event.name}</h1>
              {event.description && (
                <p className="text-sm text-slate-400 leading-relaxed">{event.description}</p>
              )}
            </div>

            {/* Action Buttons */}
            <div className="flex flex-wrap items-center gap-3">
              <button
                onClick={() => setQrModalOpen(true)}
                className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold bg-white/5 hover:bg-white/10 text-white border border-white/10 transition-colors"
              >
                <QrCode className="w-4 h-4 text-brand-400" />
                QR Code
              </button>

              <button
                onClick={handleCopyPublicLink}
                className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold bg-white/5 hover:bg-white/10 text-white border border-white/10 transition-colors"
              >
                {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                Copy Guest Link
              </button>

              <a
                href={`/guest/${event.publicId}`}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold bg-white/5 hover:bg-white/10 text-slate-300 hover:text-white border border-white/10 transition-colors"
              >
                Guest View <ExternalLink className="w-3.5 h-3.5" />
              </a>

              <button
                onClick={() => setUploadModalOpen(true)}
                className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-lg shadow-brand-500/25 transition-all"
              >
                <UploadCloud className="w-4 h-4" />
                Upload Photos
              </button>
            </div>
          </div>

          {/* Stats Badges */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 pt-6 border-t border-white/5">
            <div className="bg-white/[0.02] p-4 rounded-xl border border-white/5">
              <span className="text-[11px] uppercase tracking-wider text-slate-400">Total Uploaded</span>
              <p className="text-xl font-bold text-white mt-1">{photos.length}</p>
            </div>
            <div className="bg-emerald-500/[0.04] p-4 rounded-xl border border-emerald-500/10">
              <span className="text-[11px] uppercase tracking-wider text-emerald-400">AI Indexed</span>
              <p className="text-xl font-bold text-emerald-400 mt-1">{indexedCount}</p>
            </div>
            <div className="bg-amber-500/[0.04] p-4 rounded-xl border border-amber-500/10">
              <span className="text-[11px] uppercase tracking-wider text-amber-400">Processing</span>
              <p className="text-xl font-bold text-amber-400 mt-1">{pendingCount}</p>
            </div>
            <div className="bg-rose-500/[0.04] p-4 rounded-xl border border-rose-500/10">
              <span className="text-[11px] uppercase tracking-wider text-rose-400">Failed</span>
              <p className="text-xl font-bold text-rose-400 mt-1">{failedCount}</p>
            </div>
          </div>
        </div>

        {/* Gallery Section */}
        <div className="mt-10 space-y-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <h2 className="text-xl font-bold text-white tracking-tight">Event Gallery</h2>
              <span className="text-xs text-slate-400">({photos.length} photos)</span>
            </div>
            <button
              onClick={fetchEventAndPhotos}
              className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-white transition-colors"
            >
              <RefreshCw className="w-3.5 h-3.5" /> Refresh
            </button>
          </div>

          {photos.length === 0 ? (
            /* Empty gallery */
            <div className="glass-panel rounded-3xl p-12 text-center border border-white/10 space-y-4 max-w-lg mx-auto">
              <div className="w-16 h-16 mx-auto rounded-2xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400">
                <UploadCloud className="w-8 h-8" />
              </div>
              <h3 className="text-lg font-bold text-white">No Photos Uploaded Yet</h3>
              <p className="text-xs text-slate-400 leading-relaxed max-w-sm mx-auto">
                Upload your high-resolution event photographs. FotoFetch will generate lightweight thumbnails and index all faces with AWS Rekognition.
              </p>
              <button
                onClick={() => setUploadModalOpen(true)}
                className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl text-sm font-semibold text-white bg-brand-600 hover:bg-brand-500 transition-colors shadow-lg shadow-brand-500/25"
              >
                <UploadCloud className="w-4 h-4" />
                Upload Photos Now
              </button>
            </div>
          ) : (
            /* Photos Grid */
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
              {photos.map((photo) => (
                <div
                  key={photo.id}
                  className="glass-card rounded-xl overflow-hidden border border-white/10 group flex flex-col relative"
                >
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
                        <Loader2 className="w-5 h-5 animate-spin mb-1 text-brand-400" />
                        Generating thumbnail...
                      </div>
                    )}

                    {/* Faces Badge */}
                    {photo.facesFound > 0 && (
                      <div className="absolute top-2 left-2 px-2 py-0.5 rounded-full bg-black/70 backdrop-blur-md border border-white/10 text-[10px] font-medium text-emerald-300 flex items-center gap-1">
                        <Users className="w-3 h-3 text-emerald-400" />
                        {photo.facesFound} {photo.facesFound === 1 ? 'face' : 'faces'}
                      </div>
                    )}

                    {/* Download Button on Hover */}
                    {photo.downloadUrl && (
                      <a
                        href={photo.downloadUrl}
                        download={photo.originalFilename}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="absolute bottom-2 right-2 p-2 rounded-lg bg-black/75 hover:bg-black text-white backdrop-blur-md opacity-0 group-hover:opacity-100 transition-opacity"
                        title="Download HD Original"
                      >
                        <Download className="w-4 h-4" />
                      </a>
                    )}
                  </div>

                  <div className="p-2.5 bg-dark-900/80 border-t border-white/5 space-y-1">
                    <p className="text-[11px] font-medium text-slate-300 truncate" title={photo.originalFilename}>
                      {photo.originalFilename}
                    </p>
                    <div className="flex items-center justify-between text-[10px] text-slate-500">
                      <span>{photo.fileSize ? `${(photo.fileSize / (1024 * 1024)).toFixed(1)} MB` : ''}</span>
                      <span className={`font-semibold ${
                        photo.indexingStatus === 'COMPLETED' ? 'text-emerald-400' :
                        photo.indexingStatus === 'INDEXING' ? 'text-amber-400' :
                        photo.indexingStatus === 'NO_FACES_DETECTED' ? 'text-slate-400' : 'text-rose-400'
                      }`}>
                        {photo.indexingStatus === 'COMPLETED' ? 'Indexed' :
                         photo.indexingStatus === 'INDEXING' ? 'Indexing...' :
                         photo.indexingStatus === 'NO_FACES_DETECTED' ? 'No faces' : 'Pending'}
                      </span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
};
