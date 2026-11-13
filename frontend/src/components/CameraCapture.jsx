import React, { useRef, useState, useEffect } from 'react';
import { Camera, RefreshCw, Check, AlertCircle, X } from 'lucide-react';

export const CameraCapture = ({ onCapture, onCancel }) => {
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const [photoPreview, setPhotoPreview] = useState(null);
  const [photoBlob, setPhotoBlob] = useState(null);
  const [cameraError, setCameraError] = useState(null);
  const [startingCamera, setStartingCamera] = useState(true);

  useEffect(() => {
    startCamera();
    return () => {
      stopCamera();
    };
  }, []);

  const startCamera = async () => {
    setStartingCamera(true);
    setCameraError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 720 }, height: { ideal: 720 } },
        audio: false,
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
      setStartingCamera(false);
    } catch (err) {
      console.error('Camera access error:', err);
      setCameraError('Unable to access camera. Please allow camera permissions or upload an existing photo.');
      setStartingCamera(false);
    }
  };

  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
  };

  const capturePhoto = () => {
    if (!videoRef.current) return;
    const video = videoRef.current;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;

    const ctx = canvas.getContext('2d');
    // Mirror the selfie horizontally for natural feel
    ctx.translate(canvas.width, 0);
    ctx.scale(-1, 1);
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    canvas.toBlob(
      (blob) => {
        if (blob) {
          setPhotoBlob(blob);
          setPhotoPreview(URL.createObjectURL(blob));
          stopCamera();
        }
      },
      'image/jpeg',
      0.9
    );
  };

  const retakePhoto = () => {
    if (photoPreview) {
      URL.revokeObjectURL(photoPreview);
    }
    setPhotoPreview(null);
    setPhotoBlob(null);
    startCamera();
  };

  const confirmPhoto = () => {
    if (photoBlob) {
      onCapture(photoBlob, photoPreview);
    }
  };

  return (
    <div className="flex flex-col items-center w-full">
      {cameraError ? (
        <div className="p-6 text-center space-y-4">
          <div className="w-12 h-12 mx-auto rounded-full bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400">
            <AlertCircle className="w-6 h-6" />
          </div>
          <p className="text-sm text-slate-300">{cameraError}</p>
          <div className="flex justify-center gap-3">
            <button
              onClick={startCamera}
              className="px-4 py-2 rounded-xl text-xs font-semibold bg-white/10 hover:bg-white/20 text-white"
            >
              Try Again
            </button>
            <button
              onClick={onCancel}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:text-white"
            >
              Cancel
            </button>
          </div>
        </div>
      ) : photoPreview ? (
        <div className="flex flex-col items-center w-full space-y-5">
          <div className="relative w-64 h-64 sm:w-80 sm:h-80 rounded-2xl overflow-hidden border-2 border-brand-500 shadow-2xl">
            <img
              src={photoPreview}
              alt="Selfie preview"
              className="w-full h-full object-cover"
            />
          </div>
          <div className="flex gap-4">
            <button
              onClick={retakePhoto}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold text-slate-300 bg-white/5 hover:bg-white/10 border border-white/10 transition-colors"
            >
              <RefreshCw className="w-4 h-4" />
              Retake
            </button>
            <button
              onClick={confirmPhoto}
              className="flex items-center gap-2 px-6 py-2.5 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-lg shadow-brand-500/25 transition-all"
            >
              <Check className="w-4 h-4" />
              Use This Selfie
            </button>
          </div>
        </div>
      ) : (
        <div className="flex flex-col items-center w-full space-y-5">
          <div className="relative w-64 h-64 sm:w-80 sm:h-80 rounded-2xl overflow-hidden bg-black border border-white/10 shadow-inner">
            {startingCamera && (
              <div className="absolute inset-0 flex items-center justify-center text-xs text-slate-400">
                Starting camera...
              </div>
            )}
            <video
              ref={videoRef}
              autoPlay
              playsInline
              muted
              className="w-full h-full object-cover scale-x-[-1]"
            />
            {/* Target Face Oval Guide */}
            <div className="absolute inset-0 border-2 border-dashed border-white/30 rounded-full m-8 pointer-events-none" />
          </div>

          <div className="flex items-center gap-4">
            <button
              onClick={onCancel}
              className="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-400 hover:text-white"
            >
              Cancel
            </button>
            <button
              onClick={capturePhoto}
              disabled={startingCamera}
              className="flex items-center gap-2 px-6 py-2.5 rounded-xl text-sm font-semibold text-white bg-brand-600 hover:bg-brand-500 shadow-lg shadow-brand-500/25 disabled:opacity-50 transition-all hover:scale-105"
            >
              <Camera className="w-4 h-4" />
              Capture Photo
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
