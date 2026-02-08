function base64UrlToBytes(str: string): Uint8Array<ArrayBuffer> {
  const base64 = str.replace(/-/g, '+').replace(/_/g, '/') +
    '='.repeat((4 - (str.length % 4)) % 4);
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function decompress(data: Uint8Array<ArrayBuffer>): Promise<Uint8Array<ArrayBuffer>> {
  const ds = new DecompressionStream('deflate-raw');
  const writer = ds.writable.getWriter();
  void writer.write(data);
  void writer.close();

  const reader = ds.readable.getReader();
  const chunks: Uint8Array<ArrayBuffer>[] = [];
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(new Uint8Array(value.buffer as ArrayBuffer));
  }

  const totalLength = chunks.reduce((sum, c) => sum + c.length, 0);
  const result = new Uint8Array(totalLength);
  let offset = 0;
  for (const chunk of chunks) {
    result.set(chunk, offset);
    offset += chunk.length;
  }
  return result;
}

export async function importKey(keyBase64Url: string): Promise<CryptoKey> {
  const keyBytes = base64UrlToBytes(keyBase64Url);
  return crypto.subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['decrypt']);
}

export async function decryptJwe(compact: string, cryptoKey: CryptoKey): Promise<string> {
  const parts = compact.split('.');
  if (parts.length !== 5) throw new Error('Invalid JWE compact serialization');

  const [headerB64, , ivB64, ciphertextB64, tagB64] = parts as [string, string, string, string, string];
  const header: { zip?: string } = JSON.parse(
    atob(headerB64.replace(/-/g, '+').replace(/_/g, '/')),
  );

  const iv = base64UrlToBytes(ivB64!);
  const ciphertext = base64UrlToBytes(ciphertextB64!);
  const tag = base64UrlToBytes(tagB64!);

  // AES-GCM: ciphertext + auth tag concatenated
  const combined = new Uint8Array(ciphertext.length + tag.length);
  combined.set(ciphertext);
  combined.set(tag, ciphertext.length);

  // AAD = raw base64url header as ASCII bytes
  const aad = new TextEncoder().encode(headerB64);

  const decrypted = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv, additionalData: aad, tagLength: 128 },
    cryptoKey,
    combined,
  );

  let result = new Uint8Array(decrypted);

  if (header.zip === 'DEF') {
    result = await decompress(result);
  }

  return new TextDecoder().decode(result);
}
