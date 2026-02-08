import { useMemo } from 'react';
import { parseShlinkHash, isExpired } from '../lib/shlink';
import type { ShlPayload } from '../types/shl';

interface ParseResult {
  payload: ShlPayload | null;
  error: string | null;
  expired: boolean;
}

export function useShlinkParser(hash: string): ParseResult {
  return useMemo(() => {
    if (!hash || !hash.startsWith('#shlink:/')) {
      return { payload: null, error: 'No SHL link found in URL', expired: false };
    }
    try {
      const payload = parseShlinkHash(hash);
      return { payload, error: null, expired: isExpired(payload) };
    } catch (e) {
      return {
        payload: null,
        error: e instanceof Error ? e.message : 'Failed to parse SHL link',
        expired: false,
      };
    }
  }, [hash]);
}
