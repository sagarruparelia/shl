import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router';
import { listShls } from '../api/shl';
import type { ShlSummaryResponse, PaginatedResponse } from '../types/shl';
import ActiveFilter, { type FilterValue } from '../components/dashboard/ActiveFilter';
import ShlTable from '../components/dashboard/ShlTable';
import Pagination from '../components/shared/Pagination';
import LoadingSpinner from '../components/shared/LoadingSpinner';
import ErrorAlert from '../components/shared/ErrorAlert';
import { usePagination } from '../hooks/usePagination';

export default function DashboardPage() {
  const [filter, setFilter] = useState<FilterValue>('all');
  const { page, size, next, prev, reset } = usePagination(20);
  const [data, setData] = useState<PaginatedResponse<ShlSummaryResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const active = filter === 'all' ? undefined : filter === 'active';
      const result = await listShls({ active, page, size });
      setData(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load SHL links');
    } finally {
      setLoading(false);
    }
  }, [filter, page, size]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleFilterChange = (val: FilterValue) => {
    setFilter(val);
    reset();
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold">SMART Health Links</h2>
        <Link
          to="/dashboard/create"
          className="px-4 py-2 text-sm font-medium bg-indigo-600 text-white rounded-md hover:bg-indigo-700"
        >
          Create New
        </Link>
      </div>

      <div className="mb-4">
        <ActiveFilter value={filter} onChange={handleFilterChange} />
      </div>

      {loading && <LoadingSpinner />}
      {error && <ErrorAlert message={error} onRetry={fetchData} />}
      {!loading && !error && data && (
        <>
          <ShlTable items={data.content} />
          <Pagination
            page={page}
            size={size}
            totalElements={data.totalElements}
            onNext={next}
            onPrev={prev}
          />
        </>
      )}
    </div>
  );
}
