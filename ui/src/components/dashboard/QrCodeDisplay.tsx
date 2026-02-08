interface Props {
  dataUri: string;
  label?: string;
}

export default function QrCodeDisplay({ dataUri, label }: Props) {
  const handleDownload = () => {
    const a = document.createElement('a');
    a.href = dataUri;
    a.download = `shl-qr${label ? '-' + label.replace(/\s+/g, '_') : ''}.png`;
    a.click();
  };

  return (
    <div className="flex flex-col items-center gap-3">
      <img src={dataUri} alt="QR Code" className="w-48 h-48 border rounded-lg" />
      <button
        onClick={handleDownload}
        className="text-sm text-indigo-600 hover:underline"
      >
        Download QR
      </button>
    </div>
  );
}
