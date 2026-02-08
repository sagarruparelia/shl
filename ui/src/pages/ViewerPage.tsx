import { useState, useCallback, useEffect, useRef } from 'react';
import { useShlinkParser } from '../hooks/useShlinkParser';
import { fetchManifest, fetchDirect, fetchFileContent, PasscodeError } from '../api/protocol';
import { importKey, decryptJwe } from '../lib/crypto';
import type { ShlPayload, ManifestResponse } from '../types/shl';
import { hasFlag } from '../lib/shlink';
import ExpirationBanner from '../components/viewer/ExpirationBanner';
import PasscodeForm from '../components/viewer/PasscodeForm';
import ViewerStatus from '../components/viewer/ViewerStatus';
import DecryptedContent from '../components/viewer/DecryptedContent';
import FileDownloadCard from '../components/viewer/FileDownloadCard';
import ErrorAlert from '../components/shared/ErrorAlert';

interface DecryptedFile {
  content: string;
  contentType: string;
}

type ViewerState =
  | { step: 'passcode_required' }
  | { step: 'loading' }
  | { step: 'decrypting' }
  | { step: 'success'; files: DecryptedFile[]; manifestStatus?: string }
  | { step: 'error'; message: string };

export default function ViewerPage() {
  const { payload, error: parseError, expired } = useShlinkParser(window.location.hash);
  const [state, setState] = useState<ViewerState | null>(null);
  const [passcodeError, setPasscodeError] = useState<string | null>(null);
  const [passcodeLoading, setPasscodeLoading] = useState(false);
  const startedRef = useRef(false);

  const loadContent = useCallback(async (shl: ShlPayload, passcode?: string) => {
    setState({ step: 'loading' });
    try {
      let manifest: ManifestResponse;

      if (hasFlag(shl, 'U')) {
        manifest = await fetchDirect(shl.url, 'SHL Viewer (Web)');
      } else {
        manifest = await fetchManifest(shl.url, {
          recipient: 'SHL Viewer (Web)',
          passcode,
          embeddedLengthMax: 10485760,
        });
      }

      if (manifest.status === 'no-longer-valid') {
        setState({ step: 'error', message: 'This health data is no longer considered valid by the publisher.' });
        return;
      }

      setState({ step: 'decrypting' });

      const cryptoKey = await importKey(shl.key);
      const decrypted: DecryptedFile[] = [];

      for (const file of manifest.files) {
        let jwe: string;
        if (file.embedded) {
          jwe = file.embedded;
        } else if (file.location) {
          jwe = await fetchFileContent(file.location);
        } else {
          continue;
        }
        const content = await decryptJwe(jwe, cryptoKey);
        decrypted.push({ content, contentType: file.contentType });
      }

      setState({ step: 'success', files: decrypted, manifestStatus: manifest.status });
    } catch (e) {
      if (e instanceof PasscodeError) {
        setPasscodeError(`Invalid passcode. ${e.remainingAttempts} attempts remaining.`);
        setState({ step: 'passcode_required' });
        setPasscodeLoading(false);
        return;
      }
      setState({ step: 'error', message: e instanceof Error ? e.message : 'Failed to load content' });
    }
  }, []);

  // Auto-start: runs once on mount, guarded by ref to prevent StrictMode double-fire
  useEffect(() => {
    if (!payload || expired || parseError || startedRef.current) return;
    startedRef.current = true;

    if (hasFlag(payload, 'P')) {
      setState({ step: 'passcode_required' });
    } else {
      loadContent(payload);
    }
  }, [payload, expired, parseError, loadContent]);

  if (parseError) {
    return (
      <div className="max-w-xl mx-auto mt-20 px-4">
        <ErrorAlert message={parseError} />
      </div>
    );
  }

  if (expired) {
    return <ExpirationBanner label={payload?.label} />;
  }

  if (!payload || !state) {
    return null;
  }

  if (state.step === 'passcode_required') {
    return (
      <PasscodeForm
        label={payload.label}
        error={passcodeError}
        loading={passcodeLoading}
        onSubmit={(pc) => {
          setPasscodeError(null);
          setPasscodeLoading(true);
          loadContent(payload, pc);
        }}
      />
    );
  }

  if (state.step === 'loading') return <ViewerStatus status="loading" />;
  if (state.step === 'decrypting') return <ViewerStatus status="decrypting" />;

  if (state.step === 'error') {
    return (
      <div className="max-w-xl mx-auto mt-20 px-4">
        <ErrorAlert message={state.message} />
      </div>
    );
  }

  if (state.step === 'success') {
    const isJson = (ct: string) => ct.includes('json');

    return (
      <div className="max-w-4xl mx-auto py-8 px-4">
        <div className="mb-6">
          <h1 className="text-xl font-semibold">SMART Health Link</h1>
          {payload.label && <p className="text-sm text-gray-500 mt-1">{payload.label}</p>}
        </div>

        {state.manifestStatus === 'can-change' && (
          <div className="mb-4 rounded-md bg-blue-50 border border-blue-200 px-4 py-3">
            <p className="text-sm text-blue-700">
              This is a long-term link. The content may be updated by the publisher.
            </p>
          </div>
        )}

        <div className="space-y-4">
          {state.files.map((file, i) =>
            isJson(file.contentType) ? (
              <DecryptedContent key={i} content={file.content} contentType={file.contentType} index={i} />
            ) : (
              <FileDownloadCard key={i} content={file.content} contentType={file.contentType} index={i} />
            ),
          )}
        </div>

        {state.files.length === 0 && (
          <p className="text-sm text-gray-500 text-center py-12">No files found in this link.</p>
        )}
      </div>
    );
  }

  return null;
}
