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

export async function fetchFileContent(absoluteUrl: string): Promise<string> {
  const res = await fetch(absoluteUrl);
  if (!res.ok) throw new Error(`File download failed (${res.status})`);
  return res.text();
}
