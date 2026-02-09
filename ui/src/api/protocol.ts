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
 * SHL spec: U-flagged links use GET to the same `url` with ?recipient= query param.
 * Response is raw JWE with Content-Type: application/jose.
 * We also handle JSON ManifestResponse for compatibility with other implementations.
 */
export async function fetchDirect(
  absoluteUrl: string,
  recipient: string,
  contentType?: string,
): Promise<ManifestResponse> {
  const url = new URL(absoluteUrl);
  url.searchParams.set('recipient', recipient);

  const res = await fetch(url.toString());

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`Direct access failed (${res.status}): ${text}`);
  }

  const resContentType = res.headers.get('Content-Type') ?? '';
  if (resContentType.includes('application/jose')) {
    const jwe = await res.text();
    return {
      status: 'finalized',
      files: [{ contentType: contentType ?? 'application/fhir+json', embedded: jwe }],
    };
  }

  return res.json() as Promise<ManifestResponse>;
}

export async function fetchFileContent(absoluteUrl: string): Promise<string> {
  const res = await fetch(absoluteUrl);
  if (!res.ok) throw new Error(`File download failed (${res.status})`);
  return res.text();
}
