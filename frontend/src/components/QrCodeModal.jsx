import React, { useState, useEffect } from 'react';
import { Copy, Check, ExternalLink, QrCode, Download, Loader2 } from 'lucide-react';
import QRCode from 'qrcode';
import { Modal } from './Modal';

export const QrCodeModal = ({ isOpen, onClose, eventName, publicId }) => {
  const [copied, setCopied] = useState(false);
  const [qrDataUrl, setQrDataUrl] = useState('');
  const [generating, setGenerating] = useState(true);

  const fullUrl = typeof window !== 'undefined'
    ? `${window.location.origin}/guest/${publicId}`
    : `/guest/${publicId}`;

  useEffect(() => {
    if (!isOpen || !publicId) return;

    setGenerating(true);
    // Generate high-resolution, scan-optimized QR code locally
    QRCode.toDataURL(fullUrl, {
      width: 512,
      margin: 2,
      errorCorrectionLevel: 'H',
      color: {
        dark: '#0f172a', // Deep slate for high-contrast scanning
        light: '#ffffff', // Crisp white background
      },
    })
      .then((url) => {
        setQrDataUrl(url);
      })
      .catch((err) => {
        console.error('Failed to generate QR code locally:', err);
      })
      .finally(() => {
        setGenerating(false);
      });
  }, [isOpen, publicId, fullUrl]);

  const handleCopy = () => {
    navigator.clipboard.writeText(fullUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadQr = () => {
    if (!qrDataUrl) return;
    const link = document.createElement('a');
    link.href = qrDataUrl;
    link.download = `fotofetch-qr-${publicId}.png`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Public Guest Link & QR Code"
      maxWidth="max-w-md"
    >
      <div className="flex flex-col items-center space-y-6 text-center">
        <p className="text-sm text-slate-300">
          Share this link or display this QR code at your event venue. Guests can scan it with their phone camera to instantly find their photos.
        </p>

        {/* QR Code Container */}
        <div className="p-5 bg-white rounded-3xl shadow-2xl flex flex-col items-center border-4 border-brand-500/30">
          {generating ? (
            <div className="w-56 h-56 flex flex-col items-center justify-center text-slate-500 gap-2">
              <Loader2 className="w-8 h-8 animate-spin text-brand-500" />
              <span className="text-xs">Generating QR Code...</span>
            </div>
          ) : qrDataUrl ? (
            <img
              src={qrDataUrl}
              alt={`QR code for ${eventName}`}
              className="w-56 h-56 rounded-xl"
            />
          ) : (
            <div className="w-56 h-56 flex items-center justify-center text-xs text-rose-500">
              Could not generate QR code
            </div>
          )}

          <div className="mt-3 flex items-center gap-1.5 text-xs text-slate-700 font-bold">
            <QrCode className="w-4 h-4 text-brand-600" />
            <span>Scan with phone camera</span>
          </div>
        </div>

        {/* Download QR & Copy Link Action Buttons */}
        <div className="flex items-center gap-3 w-full">
          <button
            onClick={handleDownloadQr}
            disabled={!qrDataUrl}
            className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold bg-white/10 hover:bg-white/15 text-white border border-white/10 transition-colors disabled:opacity-50"
          >
            <Download className="w-4 h-4 text-brand-400" />
            Download QR (PNG)
          </button>
        </div>

        {/* URL Copy Input Box */}
        <div className="w-full flex items-center gap-2 bg-dark-900 border border-white/10 rounded-xl px-3 py-2">
          <input
            type="text"
            readOnly
            value={fullUrl}
            className="bg-transparent text-xs text-slate-300 flex-1 outline-none font-mono select-all"
          />
          <button
            onClick={handleCopy}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-brand-600 hover:bg-brand-500 text-white transition-colors shrink-0"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5" /> Copied
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5" /> Copy Link
              </>
            )}
          </button>
        </div>

        {/* Open in new tab */}
        <a
          href={fullUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 text-xs font-semibold text-brand-400 hover:text-brand-300 transition-colors"
        >
          Open guest view in new tab <ExternalLink className="w-3.5 h-3.5" />
        </a>
      </div>
    </Modal>
  );
};
