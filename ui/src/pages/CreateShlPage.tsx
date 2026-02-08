import { useState } from 'react';
import { Link } from 'react-router';
import type { CreateShlResponse } from '../types/shl';
import CreateForm from '../components/dashboard/CreateForm';
import CreateSuccessCard from '../components/dashboard/CreateSuccessCard';

export default function CreateShlPage() {
  const [result, setResult] = useState<CreateShlResponse | null>(null);

  return (
    <div>
      <div className="flex items-center gap-4 mb-6">
        <Link to="/dashboard" className="text-sm text-gray-500 hover:text-gray-700">&larr; Back</Link>
        <h2 className="text-xl font-semibold">Create SMART Health Link</h2>
      </div>

      {result ? (
        <CreateSuccessCard result={result} />
      ) : (
        <div className="max-w-2xl">
          <CreateForm onSuccess={setResult} />
        </div>
      )}
    </div>
  );
}
