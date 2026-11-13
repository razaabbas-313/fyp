import api from './client';

export const eventApi = {
  createEvent: (data) => api.post('/events', data),
  getMyEvents: () => api.get('/events'),
  getEvent: (id) => api.get(`/events/${id}`),
  deleteEvent: (id) => api.delete(`/events/${id}`),
  getDashboardStats: () => api.get('/events/dashboard-stats'),
};
