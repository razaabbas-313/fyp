import React, { useState } from 'react';
import { Copy, Check, ExternalLink, QrCode } from 'lucide-react';
import { Modal } from './Modal';

export const QrCodeModal = ({ isOpen, onClose, eventName, publicId }) => {
  const [copied, setCopied] = useState(false);

  const fullUrl = `${window.location.origin}/guest/${publicId}`;
  const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${encodeURIComponent(
    fullUrl
  )}&bgcolor=13-15-1f&color=ffffff&margin=10`;

  const handleCopy = () => {
    navigator.clipboard.writeText(fullUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
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
          Share this link or display this QR code at your venue. Guests can scan it to instantly find their photos using AI.
        </p>

        {/* QR Code Card */}
        <div className="p-4 bg-dark-900 rounded-2xl border border-white/10 shadow-2xl flex flex-col items-center">
          <img
            src={qrUrl}
            alt={`QR code for ${eventName}`}
            className="w-56 h-56 rounded-xl"
            loading="lazy"
          />
          <span className="text-xs text-brand-400 font-medium mt-3 flex items-center gap-1.5">
            <QrCode className="w-3.5 h-3.5" /> Scan to find photos
          </span>
        </div>

        {/* URL Copy Input Box */}
        <div className="w-full flex items-center gap-2 bg-dark-900 border border-white/10 rounded-xl px-3 py-2">
          <input
            type="text"
            readOnly
            value={fullUrl}
            className="bg-transparent text-xs text-slate-300 flex-1 outline-none font-mono"
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
                <Copy className="w-3.5 h-3.5" /> Copy
              </>
            )}
          </button>
        </div>

        {/* Open in new tab */}
        <a
          href={fullUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 text-xs font-semibold text-brand-400 hover:text-brand-300"
        >
          Open guest view in new tab <ExternalLink className="w-3.5 h-3.5" />
        </a>
      </div>
    </Modal>
  );
};
