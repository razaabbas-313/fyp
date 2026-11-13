import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Plus,
  Calendar,
  Image as ImageIcon,
  Layers,
  ArrowRight,
  Loader2,
  Share2,
  Trash2,
  ExternalLink,
  Sparkles,
  QrCode
} from 'lucide-react';
import { eventApi } from '../api/eventApi';
import { Navbar } from '../components/Navbar';
import { Modal } from '../components/Modal';
import { Toast } from '../components/Toast';
import { QrCodeModal } from '../components/QrCodeModal';

export const DashboardPage = () => {
  const [events, setEvents] = useState([]);
  const [stats, setStats] = useState({ totalEvents: 0, totalPhotos: 0 });
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [qrModalData, setQrModalData] = useState(null); // { publicId, eventName }
  const [toast, setToast] = useState({ message: '', type: 'info' });

  // Create event form state
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [eventDate, setEventDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [creating, setCreating] = useState(false);

  const navigate = useNavigate();

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const fetchDashboardData = async () => {
    setLoading(true);
    try {
      const [eventsRes, statsRes] = await Promise.all([
        eventApi.getMyEvents(),
        eventApi.getDashboardStats(),
      ]);
      setEvents(eventsRes.data || []);
      setStats(statsRes.data || { totalEvents: 0, totalPhotos: 0 });
    } catch (err) {
      setToast({ message: 'Failed to load dashboard data: ' + err.message, type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const handleCreateEvent = async (e) => {
    e.preventDefault();
    if (!name || !eventDate) {
      setToast({ message: 'Event name and date are required', type: 'error' });
      return;
    }

    setCreating(true);
    try {
      const res = await eventApi.createEvent({ name, description, eventDate });
      setToast({ message: 'Event created successfully!', type: 'success' });
      setCreateModalOpen(false);
      setName('');
      setDescription('');
      // Navigate to event management page
      navigate(`/events/${res.data.id}`);
    } catch (err) {
      setToast({ message: 'Failed to create event: ' + err.message, type: 'error' });
    } finally {
      setCreating(false);
    }
  };

  const handleDeleteEvent = async (id, e) => {
    e.stopPropagation();
    if (!window.confirm('Are you sure you want to delete this event and its Rekognition collection?')) {
      return;
    }
    try {
      await eventApi.deleteEvent(id);
      setToast({ message: 'Event deleted', type: 'info' });
      fetchDashboardData();
    } catch (err) {
      setToast({ message: 'Delete failed: ' + err.message, type: 'error' });
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-dark-950 text-slate-100">
      <Navbar />

      <Toast
        message={toast.message}
        type={toast.type}
        onClose={() => setToast({ message: '', type: 'info' })}
      />

      {/* QR Code Modal */}
      {qrModalData && (
        <QrCodeModal
          isOpen={!!qrModalData}
          onClose={() => setQrModalData(null)}
          eventName={qrModalData.eventName}
          publicId={qrModalData.publicId}
        />
      )}

      {/* Create Event Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Create New Event Gallery"
      >
        <form onSubmit={handleCreateEvent} className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">Event Name</label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Sarah & Michael's Wedding 2026"
              className="w-full px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-brand-500 transition-colors"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">Event Date</label>
            <input
              type="date"
              required
              value={eventDate}
              onChange={(e) => setEventDate(e.target.value)}
              className="w-full px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-sm text-white focus:outline-none focus:border-brand-500 transition-colors"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">Description (Optional)</label>
            <textarea
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Brief welcome message or details for guests..."
              className="w-full px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-brand-500 transition-colors resize-none"
            />
          </div>

          <div className="pt-3 border-t border-white/10 flex justify-end gap-3">
            <button
              type="button"
              onClick={() => setCreateModalOpen(false)}
              className="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-400 hover:text-white"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={creating}
              className="flex items-center gap-2 px-6 py-2.5 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-lg shadow-brand-500/25 transition-all disabled:opacity-50"
            >
              {creating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
              Create Event
            </button>
          </div>
        </form>
      </Modal>

      {/* Main Content */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 pt-28 pb-16">
        {/* Top Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-8 border-b border-white/5">
          <div>
            <h1 className="text-3xl font-extrabold text-white tracking-tight">Photographer Studio</h1>
            <p className="text-sm text-slate-400 mt-1">Manage events, upload photos, and monitor facial indexing</p>
          </div>
          <button
            onClick={() => setCreateModalOpen(true)}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-lg shadow-brand-500/25 hover:scale-[1.02] active:scale-[0.98] transition-all self-start sm:self-auto"
          >
            <Plus className="w-4 h-4" />
            Create Event
          </button>
        </div>

        {/* Stats Row */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 my-8">
          <div className="glass-panel p-6 rounded-2xl border border-white/10 flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400">
              <Layers className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs font-medium text-slate-400 uppercase tracking-wider">Total Events</p>
              <h3 className="text-2xl font-bold text-white mt-0.5">{stats.totalEvents}</h3>
            </div>
          </div>

          <div className="glass-panel p-6 rounded-2xl border border-white/10 flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
              <ImageIcon className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs font-medium text-slate-400 uppercase tracking-wider">Total Photographs</p>
              <h3 className="text-2xl font-bold text-white mt-0.5">{stats.totalPhotos}</h3>
            </div>
          </div>

          <div className="glass-panel p-6 rounded-2xl border border-white/10 flex items-center gap-4 sm:col-span-2 lg:col-span-1">
            <div className="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
              <Sparkles className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs font-medium text-slate-400 uppercase tracking-wider">AWS Rekognition</p>
              <h3 className="text-sm font-semibold text-emerald-400 mt-0.5 flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" /> Active & Ready
              </h3>
            </div>
          </div>
        </div>

        {/* Events List Section */}
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-bold text-white tracking-tight">Your Events</h2>
            <span className="text-xs text-slate-400">{events.length} event(s) registered</span>
          </div>

          {loading ? (
            <div className="py-20 flex flex-col items-center justify-center gap-3 text-slate-400">
              <Loader2 className="w-7 h-7 animate-spin text-brand-500" />
              <p className="text-sm">Loading your galleries...</p>
            </div>
          ) : events.length === 0 ? (
            /* Empty State */
            <div className="glass-panel rounded-3xl p-12 text-center border border-white/10 space-y-4 max-w-xl mx-auto my-12">
              <div className="w-16 h-16 mx-auto rounded-2xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400">
                <Layers className="w-8 h-8" />
              </div>
              <h3 className="text-lg font-bold text-white">No Events Created Yet</h3>
              <p className="text-xs text-slate-400 leading-relaxed max-w-sm mx-auto">
                Create your first event gallery, upload event photos, and get an instant guest QR code with facial recognition.
              </p>
              <button
                onClick={() => setCreateModalOpen(true)}
                className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl text-sm font-semibold text-white bg-brand-600 hover:bg-brand-500 transition-colors shadow-lg shadow-brand-500/25"
              >
                <Plus className="w-4 h-4" />
                Create First Event
              </button>
            </div>
          ) : (
            /* Events Grid */
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {events.map((event) => (
                <div
                  key={event.id}
                  onClick={() => navigate(`/events/${event.id}`)}
                  className="glass-card rounded-2xl p-6 border border-white/10 cursor-pointer flex flex-col justify-between group space-y-4"
                >
                  <div className="space-y-3">
                    <div className="flex items-start justify-between gap-3">
                      <h3 className="text-lg font-bold text-white group-hover:text-brand-300 transition-colors line-clamp-1">
                        {event.name}
                      </h3>
                      <button
                        onClick={(e) => handleDeleteEvent(event.id, e)}
                        title="Delete event"
                        className="p-1.5 rounded-lg text-slate-500 hover:text-rose-400 hover:bg-white/5 transition-colors"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>

                    {event.description && (
                      <p className="text-xs text-slate-400 line-clamp-2 leading-relaxed">
                        {event.description}
                      </p>
                    )}

                    <div className="flex items-center gap-4 text-xs text-slate-400 pt-2 border-t border-white/5">
                      <span className="flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5 text-brand-400" />
                        {event.eventDate}
                      </span>
                      <span className="flex items-center gap-1.5">
                        <ImageIcon className="w-3.5 h-3.5 text-indigo-400" />
                        {event.totalPhotos} Photos
                      </span>
                    </div>
                  </div>

                  {/* Actions Bar */}
                  <div className="flex items-center justify-between pt-3 border-t border-white/5 text-xs">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        setQrModalData({ publicId: event.publicId, eventName: event.name });
                      }}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 hover:text-white transition-colors"
                    >
                      <QrCode className="w-3.5 h-3.5 text-brand-400" />
                      QR Code
                    </button>

                    <div className="flex items-center gap-1 text-brand-400 font-semibold group-hover:translate-x-1 transition-transform">
                      Manage <ArrowRight className="w-3.5 h-3.5" />
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
