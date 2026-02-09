# SHL Data Architecture

## Data Sources

| Source | What it provides |
|--------|-----------------|
| **Client API** | JSON content or file uploads submitted via `POST /api/shl` |
| **AWS HealthLake** | FHIR healthcare bundles (Immunizations, Conditions, Medications, Allergies, Lab Results, Procedures) fetched per patient + category |

At least one source is required per SHL — either direct content or HealthLake categories+patientId, or both.

## Storage Backends

### 1. MongoDB (Reactive) — metadata & access control

**Connection**: `MONGODB_URI` (default: `mongodb://localhost:27017/shl`)

| Collection | Document Class | What it stores |
|---|---|---|
| **shlinks** | `ShlDocument` | Core SHL metadata — `manifestId`, `encryptionKey`, `label`, flags (`L`/`P`/`U`), `passcodeHash`, expiry, active state, timestamps |
| **shl_contents** | `ShlContentDocument` | Content references — `shlId` foreign key, `contentType` (MIME), `s3Key` pointer, `originalFileName`, `contentLength` |
| **access_logs** | `AccessLogDocument` | Audit trail — action (MANIFEST_REQUEST, PASSCODE_FAILURE, FILE_DOWNLOAD, DIRECT_ACCESS), recipient, IP, user-agent, success/failure |
| **file_download_tokens** | `FileDownloadToken` | Temporary download tokens — token ID, `contentId`, `shlId`, `expiresAt` (TTL-indexed), `consumed` flag |

### 2. AWS S3 — encrypted payload storage

```
s3://{S3_BUCKET}/payloads/{shlId}/{contentId}.jwe
```

- Stores **JWE-encrypted** content (algorithm: `dir` + `A256GCM` with DEFLATE compression)
- Each SHL gets a unique 256-bit AES key; the key is stored in MongoDB and embedded in the shareable URL fragment
- Content-Type: `application/jose`

### 3. AWS HealthLake — external read-only source

- Queried via SigV4-signed HTTP GET requests
- Returns FHIR Bundle JSON per resource type per patient
- Data is fetched, encrypted, and stored into S3 — HealthLake is never accessed by recipients

## Data Flow

### Creation (`POST /api/shl`)

```
Client / HealthLake  -->  encrypt (JWE)  -->  S3 (payload)
                                          -->  MongoDB (metadata + content ref)
                                          -->  Generate SHL URL + QR code
```

1. Client submits JSON content or file upload (optionally with HealthLake categories + patientId)
2. `ShlDocument` saved to MongoDB with generated `manifestId` and `encryptionKey`
3. HealthLake bundles fetched if categories provided (SigV4-signed HTTP GET)
4. Each content item encrypted with JWE (`A256GCM`) using the SHL's encryption key
5. Encrypted payload uploaded to S3 at `payloads/{shlId}/{contentId}.jwe`
6. `ShlContentDocument` saved to MongoDB referencing the S3 key
7. SHL URL, management URL, and QR code returned to caller

### Manifest Fetch (`POST /api/shl/manifest/{manifestId}`)

```
Recipient  -->  validate passcode/expiry  -->  fetch from S3  -->  return manifest
           -->  log access in MongoDB
```

1. Recipient requests manifest with optional passcode
2. Validate: active, not expired, passcode correct (BCrypt + atomic attempt decrement)
3. Access logged to `access_logs` (success or failure)
4. For each `shl_contents` entry: download encrypted JWE from S3
   - Small files: embedded directly in manifest response
   - Large files: `FileDownloadToken` created, download URL returned
5. If `U` flag (single-use): SHL deactivated after first successful fetch

### File Download (`GET /api/shl/file/{tokenId}`)

1. Token atomically marked as consumed
2. Token expiry validated
3. Encrypted JWE downloaded from S3
4. Access logged to `access_logs`
5. JWE returned with `Content-Type: application/jose`

## Encryption & Security

| Aspect | Detail |
|--------|--------|
| **Algorithm** | JWE with Direct Encryption (`dir`) + `A256GCM` |
| **Compression** | DEFLATE |
| **Key size** | 256-bit AES (32 bytes, base64url-encoded) |
| **Key storage** | MongoDB (`ShlDocument.encryptionKey`) |
| **Key distribution** | Embedded in URL fragment (client-side only, never sent to server) |
| **Passcode** | BCrypt hashed, configurable retry limit (default: 10) |
| **Token TTL** | File download tokens auto-expire via MongoDB TTL index (default: 60 min) |

## Flags

| Flag | Meaning | Effect |
|------|---------|--------|
| `L` | Long-term | Manifest status: `can-change` (content may be updated) |
| `P` | Passcode-protected | Requires passcode in manifest request |
| `U` | Single-use / Direct access | Deactivated after first successful fetch; enables direct access endpoint |

## Configuration

```yaml
app:
  base-url: ${APP_BASE_URL:http://localhost:8080}
  default-passcode-attempts: 10
  file-token-ttl-minutes: 60
  qr-code-default-size: 300
  s3:
    bucket: ${S3_BUCKET:shl-data}
    region: ${AWS_REGION:us-east-1}
    endpoint: ${S3_ENDPOINT:}          # set for LocalStack/MinIO; leave empty for real AWS
    payload-prefix: payloads/
  healthlake:
    datastore-endpoint: ${HEALTHLAKE_ENDPOINT:}
    region: ${HEALTHLAKE_REGION:us-east-1}

spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/shl}
```

## API Endpoints

### Management (Admin)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/shl` | Create SHL |
| `GET` | `/api/shl` | List SHLs (paginated) |
| `GET` | `/api/shl/{id}` | SHL detail |
| `DELETE` | `/api/shl/{id}` | Deactivate SHL |
| `GET` | `/api/shl/{id}/access-log` | Audit log |

### Protocol (Recipient)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/shl/manifest/{manifestId}` | Fetch manifest (validates passcode, returns files) |
| `GET` | `/api/shl/manifest/{manifestId}?recipient=` | Direct access — U flag (returns raw JWE, `application/jose`) |
| `GET` | `/api/shl/file/{tokenId}` | Download encrypted file |
