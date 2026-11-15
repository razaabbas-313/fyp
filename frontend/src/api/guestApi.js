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
    if (threshold && threshold >= 70 && threshold <= 99) {
      formData.append('threshold', threshold);
    }
    return api.post(`/public/events/${publicId}/search`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  getPhotoDownloadUrl: (publicId, photoId, sessionToken) => {
    const headers = {};
    if (sessionToken) {
      headers['X-Guest-Session'] = sessionToken;
      headers['Authorization'] = `Bearer ${sessionToken}`;
    }
    return api.get(`/public/events/${publicId}/photos/${photoId}/download`, { headers });
  },

  downloadZipBlob: (publicId, photoIds, sessionToken) => {
    const headers = {};
    if (sessionToken) {
      headers['X-Guest-Session'] = sessionToken;
      headers['Authorization'] = `Bearer ${sessionToken}`;
    }
    return api.post(
      `/public/events/${publicId}/photos/zip`,
      { photoIds },
      { responseType: 'blob', headers }
    );
  },

  registerGuestTag: (publicId, name, sessionToken) => {
    const headers = {};
    if (sessionToken) {
      headers['X-Guest-Session'] = sessionToken;
      headers['Authorization'] = `Bearer ${sessionToken}`;
    }
    return api.post(
      `/public/events/${publicId}/tag-name`,
      { name, sessionToken },
      { headers }
    );
  },

  searchByName: (publicId, query) => {
    return api.get(`/public/events/${publicId}/search-by-name`, {
      params: { query },
    });
  },

  listIdentities: (publicId) => {
    return api.get(`/public/events/${publicId}/identities`);
  },
};
