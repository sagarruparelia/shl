import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router';
import { getShlDetail } from '../api/shl';
import type { ShlDetailResponse } from '../types/shl';
import { formatDate } from '../lib/format';
import StatusBadge from '../components/shared/StatusBadge';
import FlagBadges from '../components/shared/FlagBadges';
import CopyButton from '../components/shared/CopyButton';
import LoadingSpinner from '../components/shared/LoadingSpinner';
import ErrorAlert from '../components/shared/ErrorAlert';
import QrCodeDisplay from '../components/dashboard/QrCodeDisplay';
import ContentList from '../components/dashboard/ContentList';
import AccessLogTable from '../components/dashboard/AccessLogTable';
import DeactivateButton from '../components/dashboard/DeactivateButton';

export default function ShlDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [detail, setDetail] = useState<ShlDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getShlDetail(id)
      .then(setDetail)
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorAlert message={error} />;
  if (!detail) return null;

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <Link to="/dashboard" className="text-sm text-gray-500 hover:text-gray-700">&larr; Back</Link>
        <div className="mt-2 flex items-start justify-between">
          <div>
            <h2 className="text-xl font-semibold">{detail.label || 'Untitled SHL'}</h2>
            <div className="mt-2 flex items-center gap-3">
              <StatusBadge active={detail.active} expiresAt={detail.expiresAt} />
              <FlagBadges flags={detail.flags} />
            </div>
            <div className="mt-2 text-sm text-gray-500 space-x-4">
              <span>Created: {formatDate(detail.createdAt)}</span>
              {detail.updatedAt && <span>Updated: {formatDate(detail.updatedAt)}</span>}
              {detail.expiresAt && <span>Expires: {formatDate(detail.expiresAt)}</span>}
            </div>
          </div>
          <DeactivateButton shlId={detail.id} active={detail.active} />
        </div>
      </div>

      {/* QR + Link */}
      <div className="border rounded-lg p-6 flex flex-col sm:flex-row gap-6">
        <QrCodeDisplay dataUri={detail.qrCode} label={detail.label} />
        <div className="flex-1 min-w-0">
          <label className="block text-xs font-medium text-gray-500 mb-1">SHLink URL</label>
          <div className="flex gap-2 items-start">
            <code className="text-xs bg-gray-50 border rounded p-2 break-all flex-1 block">
              {detail.shlinkUrl}
            </code>
            <CopyButton text={detail.shlinkUrl} />
          </div>
          <p className="mt-3 text-sm text-gray-500">
            Total accesses: {detail.totalAccesses}
          </p>
        </div>
      </div>

      {/* Contents */}
      <div>
        <h3 className="text-lg font-medium mb-3">Contents</h3>
        <ContentList contents={detail.contents} />
      </div>

      {/* Access Logs */}
      <div>
        <h3 className="text-lg font-medium mb-3">Access Logs</h3>
        <AccessLogTable shlId={detail.id} />
      </div>
    </div>
  );
}
