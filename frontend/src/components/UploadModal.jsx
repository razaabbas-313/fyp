import React, { useState, useRef } from 'react';
import { UploadCloud, CheckCircle2, AlertCircle, X, Loader2, Image as ImageIcon } from 'lucide-react';
import { Modal } from './Modal';
import { photoApi } from '../api/photoApi';

export const UploadModal = ({ isOpen, onClose, eventId, onUploadComplete }) => {
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [uploadProgress, setUploadProgress] = useState({}); // { [photoId]: { progress: 0-100, status: 'queued'|'uploading'|'completed'|'failed' } }
  const [isUploading, setIsUploading] = useState(false);
  const [overallStatus, setOverallStatus] = useState('');
  const fileInputRef = useRef(null);

  const resetState = () => {
    setSelectedFiles([]);
    setUploadProgress({});
    setIsUploading(false);
    setOverallStatus('');
  };

  const handleClose = () => {
    if (isUploading) {
      if (!window.confirm('Uploads are in progress. Are you sure you want to cancel?')) {
        return;
      }
    }
    resetState();
    onClose();
  };

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files.length > 0) {
      const validFiles = Array.from(e.target.files).filter((file) => {
        return file.type.startsWith('image/');
      });
      setSelectedFiles((prev) => [...prev, ...validFiles]);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const validFiles = Array.from(e.dataTransfer.files).filter((file) => {
        return file.type.startsWith('image/');
      });
      setSelectedFiles((prev) => [...prev, ...validFiles]);
    }
  };

  const removeFile = (index) => {
    setSelectedFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const startUpload = async () => {
    if (selectedFiles.length === 0 || isUploading) return;

    setIsUploading(true);
    setOverallStatus('Requesting secure direct S3 upload URLs...');

    try {
      // 1. Get presigned URLs for all files
      const presignRes = await photoApi.getPresignedUrls(eventId, selectedFiles);
      const presignedUploads = presignRes.data.uploads; // [ { photoId, uploadUrl, s3Key, originalFilename } ]

      // Initialize progress state
      const initialProgress = {};
      presignedUploads.forEach((u) => {
        initialProgress[u.photoId] = { progress: 0, status: 'queued', filename: u.originalFilename };
      });
      setUploadProgress(initialProgress);

      setOverallStatus('Uploading photos directly to AWS S3...');

      // 2. Concurrency queue limit = 4
      const CONCURRENCY_LIMIT = 4;
      const successfulPhotoIds = [];
      let currentIndex = 0;

      const uploadWorker = async () => {
        while (currentIndex < presignedUploads.length) {
          const index = currentIndex++;
          const item = presignedUploads[index];
          const file = selectedFiles[index];

          setUploadProgress((prev) => ({
            ...prev,
            [item.photoId]: { ...prev[item.photoId], status: 'uploading' },
          }));

          try {
            await photoApi.uploadToS3(item.uploadUrl, file, (percent) => {
              setUploadProgress((prev) => ({
                ...prev,
                [item.photoId]: { ...prev[item.photoId], progress: percent },
              }));
            });

            setUploadProgress((prev) => ({
              ...prev,
              [item.photoId]: { ...prev[item.photoId], progress: 100, status: 'completed' },
            }));
            successfulPhotoIds.push(item.photoId);
          } catch (err) {
            console.error('Direct S3 upload failed for:', item.originalFilename, err);
            setUploadProgress((prev) => ({
              ...prev,
              [item.photoId]: { ...prev[item.photoId], status: 'failed' },
            }));
          }
        }
      };

      // Launch worker threads
      const workers = [];
      const numWorkers = Math.min(CONCURRENCY_LIMIT, presignedUploads.length);
      for (let i = 0; i < numWorkers; i++) {
        workers.push(uploadWorker());
      }
      await Promise.all(workers);

      // 3. Confirm uploaded photos with backend
      if (successfulPhotoIds.length > 0) {
        setOverallStatus('Confirming uploads and queueing AI facial indexing...');
        await photoApi.confirmUploads(eventId, successfulPhotoIds);
        setOverallStatus(`Successfully uploaded ${successfulPhotoIds.length} photos!`);
      } else {
        setOverallStatus('Upload completed with errors.');
      }

      setTimeout(() => {
        setIsUploading(false);
        onUploadComplete();
        resetState();
        onClose();
      }, 1500);
    } catch (err) {
      console.error('Upload process failed:', err);
      setOverallStatus(`Upload failed: ${err.message}`);
      setIsUploading(false);
    }
  };

  const totalUploaded = Object.values(uploadProgress).filter((p) => p.status === 'completed').length;
  const overallPercentage = selectedFiles.length > 0
    ? Math.round((totalUploaded / selectedFiles.length) * 100)
    : 0;

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Upload Event Photographs"
      maxWidth="max-w-2xl"
    >
      <div className="space-y-5">
        {/* Dropzone */}
        {!isUploading && (
          <div
            onDragOver={(e) => e.preventDefault()}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            className="border-2 border-dashed border-white/20 hover:border-brand-500 rounded-2xl p-8 text-center cursor-pointer transition-colors bg-white/[0.02] hover:bg-brand-500/[0.04]"
          >
            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept="image/*"
              onChange={handleFileChange}
              className="hidden"
            />
            <div className="w-14 h-14 mx-auto rounded-2xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center text-brand-400 mb-3">
              <UploadCloud className="w-7 h-7" />
            </div>
            <p className="text-sm font-semibold text-white">
              Drag & drop event photos here, or <span className="text-brand-400 underline">browse files</span>
            </p>
            <p className="text-xs text-slate-400 mt-1">
              Supports bulk upload (JPG, PNG, WebP) directly to encrypted AWS S3 storage
            </p>
          </div>
        )}

        {/* Selected files preview list */}
        {selectedFiles.length > 0 && !isUploading && (
          <div className="space-y-3">
            <div className="flex items-center justify-between text-xs text-slate-400">
              <span>{selectedFiles.length} file(s) selected</span>
              <button
                onClick={() => setSelectedFiles([])}
                className="hover:text-rose-400 transition-colors"
              >
                Clear all
              </button>
            </div>
            <div className="max-h-52 overflow-y-auto space-y-2 pr-1">
              {selectedFiles.map((file, idx) => (
                <div
                  key={idx}
                  className="flex items-center justify-between px-3 py-2 rounded-xl bg-white/5 border border-white/5 text-xs"
                >
                  <div className="flex items-center gap-2.5 truncate">
                    <ImageIcon className="w-4 h-4 text-brand-400 shrink-0" />
                    <span className="truncate text-slate-200">{file.name}</span>
                    <span className="text-slate-500 shrink-0">
                      ({(file.size / (1024 * 1024)).toFixed(2)} MB)
                    </span>
                  </div>
                  <button
                    onClick={() => removeFile(idx)}
                    className="p-1 text-slate-400 hover:text-rose-400"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Upload in progress UI */}
        {isUploading && (
          <div className="space-y-4 py-3">
            <div className="flex items-center justify-between text-xs font-medium">
              <span className="text-brand-400 flex items-center gap-2">
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                {overallStatus}
              </span>
              <span className="text-slate-300">
                {totalUploaded} / {selectedFiles.length} ({overallPercentage}%)
              </span>
            </div>

            {/* Overall Progress Bar */}
            <div className="w-full h-2.5 rounded-full bg-white/10 overflow-hidden">
              <div
                className="h-full bg-gradient-to-r from-brand-600 to-indigo-500 transition-all duration-300"
                style={{ width: `${overallPercentage}%` }}
              />
            </div>

            {/* Individual upload progress list */}
            <div className="max-h-52 overflow-y-auto space-y-2 pr-1">
              {Object.entries(uploadProgress).map(([id, info]) => (
                <div
                  key={id}
                  className="flex items-center justify-between px-3 py-2 rounded-xl bg-white/5 border border-white/5 text-xs"
                >
                  <div className="truncate max-w-[65%] text-slate-300">
                    {info.filename}
                  </div>
                  <div className="flex items-center gap-2">
                    {info.status === 'uploading' && (
                      <span className="text-brand-400 font-semibold">{info.progress}%</span>
                    )}
                    {info.status === 'completed' && (
                      <span className="flex items-center gap-1 text-emerald-400">
                        <CheckCircle2 className="w-3.5 h-3.5" /> Done
                      </span>
                    )}
                    {info.status === 'failed' && (
                      <span className="flex items-center gap-1 text-rose-400">
                        <AlertCircle className="w-3.5 h-3.5" /> Failed
                      </span>
                    )}
                    {info.status === 'queued' && (
                      <span className="text-slate-500">Queued</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Action Buttons */}
        <div className="flex items-center justify-end gap-3 pt-4 border-t border-white/10">
          <button
            onClick={handleClose}
            disabled={isUploading && overallPercentage < 100}
            className="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-400 hover:text-white transition-colors"
          >
            Cancel
          </button>
          {!isUploading && (
            <button
              onClick={startUpload}
              disabled={selectedFiles.length === 0}
              className="flex items-center gap-2 px-6 py-2.5 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-lg shadow-brand-500/25 disabled:opacity-50 transition-all"
            >
              <UploadCloud className="w-4 h-4" />
              Upload {selectedFiles.length > 0 ? `(${selectedFiles.length}) Photos` : ''}
            </button>
          )}
        </div>
      </div>
    </Modal>
  );
};
