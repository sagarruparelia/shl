import { Link } from 'react-router';
import type { CreateShlResponse } from '../../types/shl';
import QrCodeDisplay from './QrCodeDisplay';
import CopyButton from '../shared/CopyButton';

interface Props {
  result: CreateShlResponse;
}

export default function CreateSuccessCard({ result }: Props) {
  return (
    <div className="border rounded-lg p-6 bg-green-50 border-green-200">
      <h3 className="text-lg font-semibold text-green-800 mb-4">SHL Created Successfully</h3>

      <div className="flex flex-col sm:flex-row gap-6">
        <QrCodeDisplay dataUri={result.qrCode} label={result.label} />

        <div className="flex-1 min-w-0 space-y-4">
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">SHLink URL</label>
            <div className="flex gap-2 items-start">
              <code className="text-xs bg-white border rounded p-2 break-all flex-1 block">
                {result.shlinkUrl}
              </code>
              <CopyButton text={result.shlinkUrl} />
            </div>
          </div>

          <div className="flex gap-3">
            <Link
              to={`/dashboard/${result.id}`}
              className="text-sm text-indigo-600 hover:underline"
            >
              View Details
            </Link>
            <Link
              to="/dashboard/create"
              onClick={() => window.location.reload()}
              className="text-sm text-gray-600 hover:underline"
            >
              Create Another
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
