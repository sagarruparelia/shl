interface Props {
  content: string;
  contentType: string;
  index: number;
}

export default function FileDownloadCard({ content, contentType, index }: Props) {
  const handleDownload = () => {
    const blob = new Blob([content], { type: contentType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `file-${index + 1}`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="border rounded-lg p-4 flex items-center justify-between">
      <div>
        <p className="text-sm font-medium">File {index + 1}</p>
        <p className="text-xs text-gray-500">{contentType}</p>
      </div>
      <button
        onClick={handleDownload}
        className="px-3 py-1.5 text-sm border rounded-md hover:bg-gray-50"
      >
        Download
      </button>
    </div>
  );
}
