import { useState } from 'react';

interface Props {
  label?: string;
  onSubmit: (passcode: string) => void;
  error?: string | null;
  loading?: boolean;
}

export default function PasscodeForm({ label, onSubmit, error, loading }: Props) {
  const [passcode, setPasscode] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (passcode.trim()) onSubmit(passcode.trim());
  };

  return (
    <div className="max-w-sm mx-auto mt-20">
      <div className="border rounded-lg p-6">
        <h2 className="text-lg font-semibold mb-1">Passcode Required</h2>
        {label && <p className="text-sm text-gray-500 mb-4">{label}</p>}
        <form onSubmit={handleSubmit} className="space-y-4">
          <input
            type="password"
            value={passcode}
            onChange={(e) => setPasscode(e.target.value)}
            placeholder="Enter passcode"
            autoFocus
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
          />
          {error && <p className="text-sm text-red-600">{error}</p>}
          <button
            type="submit"
            disabled={!passcode.trim() || loading}
            className="w-full py-2 text-sm font-medium bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:opacity-50"
          >
            {loading ? 'Verifying...' : 'Submit'}
          </button>
        </form>
      </div>
    </div>
  );
}
