import api from './client';
import axios from 'axios';

export const photoApi = {
  getPresignedUrls: (eventId, files) => {
    const payload = {
      files: files.map((f) => ({
        filename: f.name,
        contentType: f.type || 'image/jpeg',
        fileSize: f.size,
      })),
    };
    return api.post(`/events/${eventId}/photos/presign`, payload);
  },

  confirmUploads: (eventId, photoIds) => {
    return api.post(`/events/${eventId}/photos/confirm`, { photoIds });
  },

  getEventPhotos: (eventId) => {
    return api.get(`/events/${eventId}/photos`);
  },

  deletePhoto: (eventId, photoId) => {
    return api.delete(`/events/${eventId}/photos/${photoId}`);
  },

  bulkDeletePhotos: (eventId, photoIds) => {
    return api.post(`/events/${eventId}/photos/bulk-delete`, { photoIds });
  },

  // Direct S3 upload without passing through backend
  uploadToS3: (presignedUrl, file, onProgress) => {
    return axios.put(presignedUrl, file, {
      headers: {
        'Content-Type': file.type || 'image/jpeg',
      },
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(percentCompleted);
        }
      },
    });
  },
};
