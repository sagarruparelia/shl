import { apiFetch } from './client';
import type {
  CreateShlJsonRequest,
  CreateShlFileOptions,
  CreateShlResponse,
  ShlSummaryResponse,
  ShlDetailResponse,
  AccessLogEntry,
  PaginatedResponse,
} from '../types/shl';

export function createShlFromJson(req: CreateShlJsonRequest) {
  return apiFetch<CreateShlResponse>('/api/shl', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export function createShlFromFile(file: File, options: CreateShlFileOptions) {
  const form = new FormData();
  form.append('file', file);
  form.append('options', new Blob([JSON.stringify(options)], { type: 'application/json' }));
  return apiFetch<CreateShlResponse>('/api/shl', {
    method: 'POST',
    body: form,
  });
}

export function listShls(params: { active?: boolean; page?: number; size?: number }) {
  const sp = new URLSearchParams();
  if (params.active !== undefined) sp.set('active', String(params.active));
  if (params.page !== undefined) sp.set('page', String(params.page));
  if (params.size !== undefined) sp.set('size', String(params.size));
  return apiFetch<PaginatedResponse<ShlSummaryResponse>>(`/api/shl?${sp}`);
}

export function getShlDetail(id: string) {
  return apiFetch<ShlDetailResponse>(`/api/shl/${encodeURIComponent(id)}`);
}

export function deactivateShl(id: string) {
  return apiFetch<void>(`/api/shl/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function getAccessLogs(id: string, params: { page?: number; size?: number }) {
  const sp = new URLSearchParams();
  if (params.page !== undefined) sp.set('page', String(params.page));
  if (params.size !== undefined) sp.set('size', String(params.size));
  return apiFetch<PaginatedResponse<AccessLogEntry>>(
    `/api/shl/${encodeURIComponent(id)}/access-log?${sp}`,
  );
}
