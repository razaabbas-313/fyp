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
    QRCode.toDataURL(fullUrl, {
      width: 512,
      margin: 2,
      errorCorrectionLevel: 'H',
      color: {
        dark: '#0f172a',
        light: '#ffffff',
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
      title="Guest Access & QR Code"
      maxWidth="max-w-md"
    >
      <div className="flex flex-col items-center space-y-5 text-center">
        <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
          Display this QR code at your event or share the link. Guests can scan with their phone camera to find their photos.
        </p>

        {/* QR Code Container */}
        <div className="p-4 bg-white rounded-2xl shadow-xl flex flex-col items-center border border-white/20">
          {generating ? (
            <div className="w-44 h-44 sm:w-52 sm:h-52 flex flex-col items-center justify-center text-slate-500 gap-2">
              <Loader2 className="w-7 h-7 animate-spin text-brand-500" />
              <span className="text-xs">Generating QR...</span>
            </div>
          ) : qrDataUrl ? (
            <img
              src={qrDataUrl}
              alt={`QR code for ${eventName}`}
              className="w-44 h-44 sm:w-52 sm:h-52 rounded-lg"
            />
          ) : (
            <div className="w-44 h-44 sm:w-52 sm:h-52 flex items-center justify-center text-xs text-rose-500">
              Could not generate QR code
            </div>
          )}

          <div className="mt-2.5 flex items-center gap-1.5 text-xs text-slate-700 font-semibold">
            <QrCode className="w-3.5 h-3.5 text-brand-600" />
            <span>Scan with phone camera</span>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-col sm:flex-row items-center gap-2.5 w-full">
          <button
            onClick={handleDownloadQr}
            disabled={!qrDataUrl}
            className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold bg-white/10 hover:bg-white/15 text-white border border-white/10 transition-colors disabled:opacity-50"
          >
            <Download className="w-3.5 h-3.5 text-brand-400" />
            Download QR (PNG)
          </button>
        </div>

        {/* URL Copy Input Box */}
        <div className="w-full flex items-center gap-2 bg-dark-900 border border-white/10 rounded-xl px-3 py-2">
          <input
            type="text"
            readOnly
            value={fullUrl}
            className="bg-transparent text-xs text-slate-300 flex-1 outline-none font-mono select-all truncate"
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
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-brand-400 hover:text-brand-300 transition-colors pt-1"
        >
          Open guest view in new tab <ExternalLink className="w-3.5 h-3.5" />
        </a>
      </div>
    </Modal>
  );
};

