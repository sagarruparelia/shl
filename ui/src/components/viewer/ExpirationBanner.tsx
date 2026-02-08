interface Props {
  label?: string;
}

export default function ExpirationBanner({ label }: Props) {
  return (
    <div className="max-w-xl mx-auto mt-20 text-center">
      <div className="rounded-lg bg-red-50 border border-red-200 p-8">
        <h2 className="text-lg font-semibold text-red-800">Link Expired</h2>
        {label && <p className="text-sm text-red-600 mt-1">{label}</p>}
        <p className="mt-3 text-sm text-gray-600">
          This SMART Health Link has expired and is no longer available.
        </p>
      </div>
    </div>
  );
}
