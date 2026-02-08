import type { ManifestRequest, ManifestResponse, PasscodeErrorResponse } from '../types/shl';

export class PasscodeError extends Error {
  constructor(public remainingAttempts: number) {
    super('Invalid passcode');
  }
}

export async function fetchManifest(
  absoluteUrl: string,
  req: ManifestRequest,
): Promise<ManifestResponse> {
  const res = await fetch(absoluteUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });

  if (res.status === 401) {
    const body = (await res.json()) as PasscodeErrorResponse;
    throw new PasscodeError(body.remainingAttempts);
  }

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`Manifest request failed (${res.status}): ${text}`);
  }

  return res.json() as Promise<ManifestResponse>;
}

/**
 * SHL spec: U-flagged links use GET with ?recipient= query param.
 * The server may return either:
 *   - Raw JWE (Content-Type: application/jose) per strict spec
 *   - ManifestResponse JSON (some implementations)
 * We handle both.
 */
export async function fetchDirect(
  absoluteUrl: string,
  recipient: string,
  contentType?: string,
): Promise<ManifestResponse> {
  const url = new URL(absoluteUrl);
  // Adjust path: /manifest/ → /direct/ for our backend
  url.pathname = url.pathname.replace('/manifest/', '/direct/');
  url.searchParams.set('recipient', recipient);

  const res = await fetch(url.toString());

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`Direct access failed (${res.status}): ${text}`);
  }

  const resContentType = res.headers.get('Content-Type') ?? '';
  if (resContentType.includes('application/jose')) {
    // Strict spec: raw JWE response
    const jwe = await res.text();
    return {
      status: 'finalized',
      files: [{ contentType: contentType ?? 'application/fhir+json', embedded: jwe }],
    };
  }

  // Our backend returns ManifestResponse JSON
  return res.json() as Promise<ManifestResponse>;
}

export async function fetchFileContent(absoluteUrl: string): Promise<string> {
  const res = await fetch(absoluteUrl);
  if (!res.ok) throw new Error(`File download failed (${res.status})`);
  return res.text();
}
