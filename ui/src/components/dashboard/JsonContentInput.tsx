import { useState } from 'react';

interface Props {
  value: string;
  onChange: (value: string) => void;
}

export default function JsonContentInput({ value, onChange }: Props) {
  const [error, setError] = useState<string | null>(null);

  const handleChange = (raw: string) => {
    onChange(raw);
    if (!raw.trim()) {
      setError(null);
      return;
    }
    try {
      JSON.parse(raw);
      setError(null);
    } catch {
      setError('Invalid JSON');
    }
  };

  return (
    <div>
      <label className="block text-sm font-medium mb-1">FHIR Content (JSON)</label>
      <textarea
        value={value}
        onChange={(e) => handleChange(e.target.value)}
        rows={10}
        className={`w-full font-mono text-sm border rounded-md p-3 focus:outline-none focus:ring-2 ${
          error ? 'border-red-300 focus:ring-red-400' : 'border-gray-300 focus:ring-indigo-400'
        }`}
        placeholder='{"resourceType": "Bundle", ...}'
      />
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  );
}
