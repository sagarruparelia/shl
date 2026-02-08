import type { ShlPayload } from '../types/shl';

const SHLINK_PREFIX = '#shlink:/';

export function parseShlinkHash(hash: string): ShlPayload {
  if (!hash.startsWith(SHLINK_PREFIX)) {
    throw new Error('Invalid SHL link format');
  }

  const encoded = hash.substring(SHLINK_PREFIX.length);
  const base64 = encoded.replace(/-/g, '+').replace(/_/g, '/') +
    '='.repeat((4 - (encoded.length % 4)) % 4);
  const json = atob(base64);
  const payload = JSON.parse(json) as ShlPayload;

  if (!payload.url || !payload.key) {
    throw new Error('SHL payload missing required fields');
  }

  return payload;
}

export function isExpired(payload: ShlPayload): boolean {
  return payload.exp !== undefined && payload.exp * 1000 < Date.now();
}

export function hasFlag(payload: ShlPayload, flag: string): boolean {
  return payload.flag?.includes(flag) ?? false;
}
