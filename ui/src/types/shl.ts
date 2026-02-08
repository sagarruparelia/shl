import type { FhirCategory } from './enums';

// --- Request types ---

export interface CreateShlJsonRequest {
  content: unknown;
  patientId?: string;
  categories?: FhirCategory[];
  label?: string;
  passcode?: string;
  expirationInSeconds?: number;
  singleUse?: boolean;
  longTerm?: boolean;
}

export interface CreateShlFileOptions {
  label?: string;
  passcode?: string;
  expirationInSeconds?: number;
  singleUse?: boolean;
  longTerm?: boolean;
  patientId?: string;
  categories?: FhirCategory[];
}

export interface ManifestRequest {
  recipient: string;
  passcode?: string;
  embeddedLengthMax?: number;
}

// --- Response types ---

export interface CreateShlResponse {
  id: string;
  shlinkUrl: string;
  qrCode: string;
  managementUrl: string;
  label: string;
  flags: string;
  expiresAt: string | null;
  singleUse: boolean;
}

export interface ShlSummaryResponse {
  id: string;
  label: string;
  flags: string;
  active: boolean;
  singleUse: boolean;
  expiresAt: string | null;
  createdAt: string | null;
  contentCount: number;
  accessCount: number;
}

export interface ContentSummary {
  id: string;
  contentType: string;
  originalFileName: string | null;
  contentLength: number;
  createdAt: string | null;
}

export interface ShlDetailResponse {
  id: string;
  label: string;
  flags: string;
  active: boolean;
  singleUse: boolean;
  expiresAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  shlinkUrl: string;
  qrCode: string;
  contents: ContentSummary[];
  totalAccesses: number;
}

export interface ManifestFileEntry {
  contentType: string;
  embedded?: string;
  location?: string;
  lastUpdated?: string;
}

export interface ManifestResponse {
  status: string;
  files: ManifestFileEntry[];
}

export interface AccessLogEntry {
  id: string;
  action: string;
  recipient: string;
  ipAddress: string;
  userAgent: string;
  success: boolean;
  failureReason: string;
  createdAt: string;
}

export interface PasscodeErrorResponse {
  error: string;
  remainingAttempts: number;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
}

// --- SHL Payload (from the #shlink:/ fragment) ---

export interface ShlPayload {
  url: string;
  key: string;
  exp?: number;
  flag?: string;
  label?: string;
  v: number;
}
