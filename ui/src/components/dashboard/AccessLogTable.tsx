import { useState, useEffect, useCallback } from 'react';
import { getAccessLogs } from '../../api/shl';
import type { AccessLogEntry, PaginatedResponse } from '../../types/shl';
import { usePagination } from '../../hooks/usePagination';
import Pagination from '../shared/Pagination';
import LoadingSpinner from '../shared/LoadingSpinner';
import { formatDate } from '../../lib/format';

interface Props {
  shlId: string;
}

export default function AccessLogTable({ shlId }: Props) {
  const { page, size, next, prev } = usePagination(50);
  const [data, setData] = useState<PaginatedResponse<AccessLogEntry> | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      setData(await getAccessLogs(shlId, { page, size }));
    } finally {
      setLoading(false);
    }
  }, [shlId, page, size]);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  if (loading) return <LoadingSpinner message="Loading access logs..." />;
  if (!data || data.content.length === 0) {
    return <p className="text-sm text-gray-500">No access logs yet.</p>;
  }

  return (
    <div>
      <div className="overflow-x-auto border rounded-lg">
        <table className="w-full">
          <thead>
            <tr className="bg-gray-50 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
              <th className="px-4 py-3">Action</th>
              <th className="px-4 py-3">Recipient</th>
              <th className="px-4 py-3">IP</th>
              <th className="px-4 py-3">Success</th>
              <th className="px-4 py-3">Time</th>
            </tr>
          </thead>
          <tbody>
            {data.content.map((log) => (
              <tr key={log.id} className="border-t">
                <td className="px-4 py-3 text-sm">{log.action.replace(/_/g, ' ')}</td>
                <td className="px-4 py-3 text-sm">{log.recipient}</td>
                <td className="px-4 py-3 text-sm text-gray-500 font-mono">{log.ipAddress}</td>
                <td className="px-4 py-3 text-sm">
                  {log.success ? (
                    <span className="text-green-600">Yes</span>
                  ) : (
                    <span className="text-red-600" title={log.failureReason}>No</span>
                  )}
                </td>
                <td className="px-4 py-3 text-sm text-gray-500">{formatDate(log.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page} size={size} totalElements={data.totalElements} onNext={next} onPrev={prev} />
    </div>
  );
}
