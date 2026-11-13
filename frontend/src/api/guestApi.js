import api from './client';

export const guestApi = {
  getPublicEvent: (publicId) => {
    return api.get(`/public/events/${publicId}`);
  },

  getEventPhotos: (publicId) => {
    return api.get(`/public/events/${publicId}/photos`);
  },

  searchBySelfie: (publicId, selfieBlob, threshold = 80) => {
    const formData = new FormData();
    formData.append('selfie', selfieBlob, 'selfie.jpg');
    if (threshold) {
      formData.append('threshold', threshold);
    }
    return api.post(`/public/events/${publicId}/search`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  getPhotoDownloadUrl: (publicId, photoId) => {
    return api.get(`/public/events/${publicId}/photos/${photoId}/download`);
  },

  downloadZipBlob: (publicId, photoIds) => {
    return api.post(
      `/public/events/${publicId}/photos/zip`,
      { photoIds },
      { responseType: 'blob' }
    );
  },
};
