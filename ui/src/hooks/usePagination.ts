import { useState, useCallback } from 'react';

export function usePagination(initialSize = 20) {
  const [page, setPage] = useState(0);
  const [size] = useState(initialSize);

  const next = useCallback(() => setPage((p) => p + 1), []);
  const prev = useCallback(() => setPage((p) => Math.max(0, p - 1)), []);
  const reset = useCallback(() => setPage(0), []);

  return { page, size, next, prev, reset, setPage };
}
