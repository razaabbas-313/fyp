import React, { useState, useRef } from 'react';
import { UploadCloud, CheckCircle2, AlertCircle, X, Loader2, Image as ImageIcon, AlertTriangle, RefreshCw } from 'lucide-react';
import { Modal } from './Modal';
import { photoApi } from '../api/photoApi';

const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_FILE_SIZE = 25 * 1024 * 1024; // 25MB

export const UploadModal = ({ isOpen, onClose, eventId, onUploadComplete }) => {
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [uploadProgress, setUploadProgress] = useState({}); // { [photoId]: { progress: 0-100, status: 'queued'|'uploading'|'completed'|'failed' } }
  const [isUploading, setIsUploading] = useState(false);
  const [overallStatus, setOverallStatus] = useState('');
  const [validationWarning, setValidationWarning] = useState('');
  const fileInputRef = useRef(null);

  const resetState = () => {
    setSelectedFiles([]);
    setUploadProgress({});
    setIsUploading(false);
    setOverallStatus('');
    setValidationWarning('');
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

  const validateAndAddFiles = (fileList) => {
    setValidationWarning('');
    const newFiles = Array.from(fileList);
    const valid = [];
    const errors = [];

    for (const file of newFiles) {
      const isAllowedType = ALLOWED_MIME_TYPES.includes(file.type) ||
        /\.(jpe?g|png|webp)$/i.test(file.name);

      if (!isAllowedType) {
        errors.push(`"${file.name}": Unsupported format (must be JPG, PNG, or WebP)`);
        continue;
      }

      if (file.size > MAX_FILE_SIZE) {
        errors.push(`"${file.name}": Exceeds 25MB maximum limit (${(file.size / (1024 * 1024)).toFixed(1)}MB)`);
        continue;
      }

      valid.push(file);
    }

    if (errors.length > 0) {
      setValidationWarning(errors.join(' • '));
    }

    if (valid.length > 0) {
      setSelectedFiles((prev) => [...prev, ...valid]);
    }
  };

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files.length > 0) {
      validateAndAddFiles(e.target.files);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      validateAndAddFiles(e.dataTransfer.files);
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
      const presignedUploads = presignRes?.data?.uploads || presignRes?.uploads || [];

      if (!presignedUploads || presignedUploads.length === 0) {
        throw new Error('No presigned upload URLs returned from backend.');
      }

      // Initialize progress state
      const initialProgress = {};
      presignedUploads.forEach((u) => {
        initialProgress[u.photoId] = { progress: 0, status: 'queued', filename: u.originalFilename, error: null };
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
            let reason = 'Upload failed';
            if (err.message === 'Network Error' || !err.response) {
              reason = 'CORS error / Network blocked';
            } else if (err.response?.status === 403) {
              reason = 'AWS 403 Forbidden (Check IAM / Bucket permissions)';
            } else if (err.response?.status === 404) {
              reason = 'S3 Bucket not found';
            } else if (typeof err.response?.data === 'string' && err.response.data.includes('<Code>')) {
              const match = err.response.data.match(/<Code>(.*?)<\/Code>/);
              if (match) reason = match[1];
            }

            setUploadProgress((prev) => ({
              ...prev,
              [item.photoId]: { ...prev[item.photoId], status: 'failed', error: reason },
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

        if (successfulPhotoIds.length === presignedUploads.length) {
          setOverallStatus(`Successfully uploaded all ${successfulPhotoIds.length} photos!`);
          setTimeout(() => {
            setIsUploading(false);
            onUploadComplete();
            resetState();
            onClose();
          }, 1500);
        } else {
          setOverallStatus(`Partially completed: ${successfulPhotoIds.length} uploaded, ${presignedUploads.length - successfulPhotoIds.length} failed.`);
          setIsUploading(false);
          onUploadComplete();
        }
      } else {
        setOverallStatus('Upload failed: All photos failed to upload to AWS S3.');
        setIsUploading(false);
      }
    } catch (err) {
      console.error('Upload process failed:', err);
      const msg = err.response?.data?.message || err.message || 'Upload process failed';
      setOverallStatus(`Upload failed: ${msg}`);
      setIsUploading(false);
    }
  };

  const totalUploaded = Object.values(uploadProgress).filter((p) => p.status === 'completed').length;
  const failedItems = Object.values(uploadProgress).filter((p) => p.status === 'failed');
  const hasFailures = failedItems.length > 0;
  const hasUploadProgress = Object.keys(uploadProgress).length > 0;
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
        {/* Validation Warning Alert */}
        {validationWarning && (
          <div className="p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-xs text-amber-300 flex items-start gap-2.5">
            <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
            <div className="space-y-0.5 leading-relaxed">
              <span className="font-semibold text-white">Notice: </span>
              {validationWarning}
            </div>
          </div>
        )}

        {/* Dropzone - shown when not started yet */}
        {!isUploading && !hasUploadProgress && (
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
              accept="image/jpeg,image/png,image/webp"
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

        {/* Selected files preview list - before upload */}
        {selectedFiles.length > 0 && !hasUploadProgress && (
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

        {/* Upload Progress & Results UI */}
        {hasUploadProgress && (
          <div className="space-y-4 py-2">
            <div className="flex items-center justify-between text-xs font-medium">
              <span className={`flex items-center gap-2 ${hasFailures && !isUploading ? 'text-rose-400' : 'text-brand-400'}`}>
                {isUploading ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : hasFailures ? (
                  <AlertCircle className="w-3.5 h-3.5 text-rose-400" />
                ) : (
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                )}
                {overallStatus}
              </span>
              <span className="text-slate-300">
                {totalUploaded} / {selectedFiles.length} ({overallPercentage}%)
              </span>
            </div>

            {/* Overall Progress Bar */}
            <div className="w-full h-2 rounded-full bg-white/10 overflow-hidden">
              <div
                className={`h-full transition-all duration-300 ${
                  hasFailures && !isUploading
                    ? 'bg-gradient-to-r from-rose-500 to-amber-500'
                    : 'bg-gradient-to-r from-brand-600 to-indigo-500'
                }`}
                style={{ width: `${overallPercentage}%` }}
              />
            </div>

            {/* Diagnostic Alert Box on failure */}
            {hasFailures && !isUploading && (
              <div className="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/20 text-xs space-y-2">
                <div className="flex items-center gap-2 font-semibold text-rose-300">
                  <AlertTriangle className="w-4 h-4 shrink-0 text-rose-400" />
                  <span>Direct S3 Upload Issue Detected ({failedItems.length} photo{failedItems.length > 1 ? 's' : ''} failed)</span>
                </div>
                <p className="text-slate-300 leading-relaxed">
                  {failedItems.some((f) => f.error?.includes('CORS'))
                    ? 'AWS S3 blocked the upload from your browser due to CORS policy. Ensure your S3 bucket has CORS enabled with AllowedOrigins: ["*"] and AllowedMethods: ["GET", "PUT", "POST", "HEAD"].'
                    : failedItems.some((f) => f.error?.includes('403'))
                    ? 'AWS returned 403 Forbidden. Verify that AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY are added to your Render environment variables.'
                    : 'Direct upload to AWS S3 failed. Check browser Developer Tools Console for network details.'}
                </p>
                <div className="pt-1 text-[11px] text-slate-400">
                  💡 Follow <span className="font-mono text-brand-300">DEPLOYMENT.md</span> Section 3 to apply S3 CORS in AWS Console.
                </div>
              </div>
            )}

            {/* Individual upload progress list */}
            <div className="max-h-52 overflow-y-auto space-y-2 pr-1">
              {Object.entries(uploadProgress).map(([id, info]) => (
                <div
                  key={id}
                  className="flex items-center justify-between px-3 py-2 rounded-xl bg-white/5 border border-white/5 text-xs"
                >
                  <div className="truncate max-w-[60%] text-slate-300">
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
                      <div className="flex items-center gap-1.5 text-right">
                        {info.error && (
                          <span className="text-[10px] text-rose-300/80 max-w-[160px] truncate" title={info.error}>
                            {info.error}
                          </span>
                        )}
                        <span className="flex items-center gap-1 text-rose-400 shrink-0 font-medium">
                          <AlertCircle className="w-3.5 h-3.5" /> Failed
                        </span>
                      </div>
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
            {hasFailures && !isUploading ? 'Close' : 'Cancel'}
          </button>

          {hasFailures && !isUploading && (
            <button
              onClick={resetState}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold text-slate-200 bg-white/10 hover:bg-white/15 transition-all"
            >
              <RefreshCw className="w-4 h-4" />
              Try Again
            </button>
          )}

          {!hasUploadProgress && !isUploading && (
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
