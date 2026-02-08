import { useState } from 'react';
import { createShlFromJson, createShlFromFile } from '../../api/shl';
import type { CreateShlResponse } from '../../types/shl';
import type { FhirCategory } from '../../types/enums';
import JsonContentInput from './JsonContentInput';
import FileUploadInput from './FileUploadInput';
import CategorySelect from './CategorySelect';
import ErrorAlert from '../shared/ErrorAlert';

type Mode = 'json' | 'file';

const EXPIRATION_OPTIONS = [
  { label: 'No expiration', value: 0 },
  { label: '1 hour', value: 3600 },
  { label: '24 hours', value: 86400 },
  { label: '7 days', value: 604800 },
  { label: '30 days', value: 2592000 },
  { label: '1 year', value: 31536000 },
];

interface Props {
  onSuccess: (result: CreateShlResponse) => void;
}

export default function CreateForm({ onSuccess }: Props) {
  const [mode, setMode] = useState<Mode>('json');
  const [jsonContent, setJsonContent] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [label, setLabel] = useState('');
  const [patientId, setPatientId] = useState('');
  const [categories, setCategories] = useState<FhirCategory[]>([]);
  const [passcode, setPasscode] = useState('');
  const [expiration, setExpiration] = useState(0);
  const [singleUse, setSingleUse] = useState(false);
  const [longTerm, setLongTerm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSubmit = mode === 'json'
    ? (jsonContent.trim() !== '' || (categories.length > 0 && patientId.trim() !== ''))
    : file !== null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      let result: CreateShlResponse;

      if (mode === 'json') {
        result = await createShlFromJson({
          content: jsonContent.trim() ? JSON.parse(jsonContent) : undefined,
          label: label || undefined,
          patientId: patientId || undefined,
          categories: categories.length > 0 ? categories : undefined,
          passcode: passcode || undefined,
          expirationInSeconds: expiration || undefined,
          singleUse,
          longTerm,
        });
      } else {
        result = await createShlFromFile(file!, {
          label: label || undefined,
          patientId: patientId || undefined,
          categories: categories.length > 0 ? categories : undefined,
          passcode: passcode || undefined,
          expirationInSeconds: expiration || undefined,
          singleUse,
          longTerm,
        });
      }

      onSuccess(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create SHL');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* Mode tabs */}
      <div className="flex border-b">
        <button
          type="button"
          onClick={() => setMode('json')}
          className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
            mode === 'json' ? 'border-indigo-600 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          JSON Content
        </button>
        <button
          type="button"
          onClick={() => setMode('file')}
          className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
            mode === 'file' ? 'border-indigo-600 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          File Upload
        </button>
      </div>

      {/* Content input */}
      {mode === 'json' ? (
        <JsonContentInput value={jsonContent} onChange={setJsonContent} />
      ) : (
        <FileUploadInput file={file} onChange={setFile} />
      )}

      {/* Label */}
      <div>
        <label className="block text-sm font-medium mb-1">Label</label>
        <input
          type="text"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          maxLength={80}
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
          placeholder="e.g. Patient COVID Records"
        />
      </div>

      {/* Patient ID + Categories */}
      <div>
        <label className="block text-sm font-medium mb-1">Patient ID</label>
        <input
          type="text"
          value={patientId}
          onChange={(e) => setPatientId(e.target.value)}
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
          placeholder="Required when using categories"
        />
      </div>

      <CategorySelect selected={categories} onChange={setCategories} />

      {/* Passcode */}
      <div>
        <label className="block text-sm font-medium mb-1">Passcode (optional)</label>
        <input
          type="text"
          value={passcode}
          onChange={(e) => setPasscode(e.target.value)}
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
          placeholder="Leave blank for no passcode"
          disabled={singleUse}
        />
        {singleUse && <p className="mt-1 text-xs text-gray-400">Passcode not available with single-use</p>}
      </div>

      {/* Expiration */}
      <div>
        <label className="block text-sm font-medium mb-1">Expiration</label>
        <select
          value={expiration}
          onChange={(e) => setExpiration(Number(e.target.value))}
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
        >
          {EXPIRATION_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
      </div>

      {/* Flags */}
      <div className="flex gap-6">
        <label className="flex items-center gap-2 text-sm cursor-pointer">
          <input
            type="checkbox"
            checked={singleUse}
            onChange={(e) => {
              setSingleUse(e.target.checked);
              if (e.target.checked) { setLongTerm(false); setPasscode(''); }
            }}
            className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
          />
          Single Use
        </label>
        <label className="flex items-center gap-2 text-sm cursor-pointer">
          <input
            type="checkbox"
            checked={longTerm}
            onChange={(e) => {
              setLongTerm(e.target.checked);
              if (e.target.checked) setSingleUse(false);
            }}
            className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
          />
          Long-term
        </label>
      </div>

      {error && <ErrorAlert message={error} />}

      <button
        type="submit"
        disabled={!canSubmit || submitting}
        className="w-full py-2.5 text-sm font-medium bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {submitting ? 'Creating...' : 'Create SHL'}
      </button>
    </form>
  );
}
