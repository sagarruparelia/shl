# SMART Health Links (SHL) — Technical Documentation

## Table of Contents

- [Overview](#overview)
- [Architecture Diagram](#architecture-diagram)
- [Data Sources](#data-sources)
- [How a Secure Link is Created](#how-a-secure-link-is-created)
- [How a Secure Link is Accessed](#how-a-secure-link-is-accessed)
- [Security Model](#security-model)
- [API Reference](#api-reference)
- [Data Models](#data-models)
- [Configuration](#configuration)
- [Local Development](#local-development)

---

## Overview

This system implements the [HL7 SMART Health Links specification](https://hl7.org/fhir/uv/smart-health-cards-and-links/links-specification.html) — a standard for sharing health data through encrypted, short-lived, auditable links.

A **SMART Health Link (SHL)** is a URL (or QR code) that encodes everything a recipient needs to retrieve and decrypt shared health data — without the server ever exposing the decryption key or the plaintext data.

**Key properties of every SHL:**

| Property              | Description                                                                         |
|-----------------------|-------------------------------------------------------------------------------------|
| Encrypted at rest     | All payloads are encrypted with AES-256-GCM before storage                          |
| Zero-knowledge server | The encryption key is embedded in the link fragment (`#`), never sent to the server |
| Auditable             | Every access attempt is logged with IP, user-agent, timestamp, and recipient        |
| Expirable             | Links can auto-expire after a configured duration                                   |
| Single-use            | Links can self-deactivate after the first successful access                         |
| Passcode-protected    | Links can require a passcode with brute-force protection                            |

---

## Architecture Diagram

```
                           ┌───────────────────────────────────────────┐
                           │              Clients                      │
                           │   (Browser / QR Scanner / FHIR App)       │
                           └────────────┬─────────────┬────────────────┘
                                        │             │
                              Create /  │             │  Access link
                              Manage    │             │  (viewer page)
                                        ▼             ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                          Spring Boot (WebFlux)                                │
│                                                                               │
│  ┌─────────────────────────┐    ┌──────────────────────────────────┐          │
│  │  ShlManagementController│    │     ShlProtocolController        │          │
│  │                         │    │                                  │          │
│  │  POST /api/shl (JSON)   │    │  POST /api/shl/manifest/{id}     │          │
│  │  POST /api/shl (file)   │    │  GET  /api/shl/file/{token}      │          │
│  │  GET  /api/shl          │    │  GET  /api/shl/direct/{id}       │          │
│  │  GET  /api/shl/{id}     │    └──────────────┬───────────────────┘          │
│  │  DELETE /api/shl/{id}   │                    │                             │
│  │  GET  /api/shl/{id}/    │                    │                             │
│  │       access-log        │                    │                             │
│  └──────────┬──────────────┘                    │                             │
│             │                                   │                             │
│             ▼                                   ▼                             │
│  ┌──────────────────────────────────────────────────────────────────┐         │
│  │                         Services                                 │         │
│  │                                                                  │         │
│  │  ShlService          — Create, list, detail, deactivate SHLs     │         │
│  │  ManifestService     — SHL protocol: manifest + file download    │         │
│  │  EncryptionService   — JWE encrypt/decrypt (AES-256-GCM)         │         │
│  │  S3StorageService    — Upload/download/delete encrypted payloads │         │
│  │  QrCodeService       — Generate QR codes (ZXing, base64 PNG)     │         │
│  │  AccessLogService    — Audit trail logging and queries           │         │
│  │  ShlPayloadService   — Build shlink:/ URLs and download URLs     │         │
│  └────────────┬─────────────────────────┬───────────────────────────┘         │
│               │                         │                                     │
└───────────────┼─────────────────────────┼─────────────────────────────────────┘
                │                         │
                ▼                         ▼
   ┌─────────────────────┐     ┌─────────────────────┐
   │     MongoDB         │     │      AWS S3         │
   │                     │     │                     │
   │  shlinks            │     │  payloads/          │
   │  shl_contents       │     │    {shlId}/         │
   │  access_logs        │     │      {contentId}.jwe│
   │  file_download_     │     │                     │
   │    tokens           │     │                     │
   └─────────────────────┘     └─────────────────────┘
    (metadata, audit,           (encrypted JWE
     indexes, TTL)               payloads only)
```

**Storage split rationale:**

- **MongoDB** stores small, indexed metadata (SHL config, content references, audit logs, download tokens). Supports TTL indexes for auto-expiring download tokens and atomic `findAndModify` for race-safe passcode/token operations.
- **AWS S3** stores encrypted JWE payloads of any size. The server never stores or transmits plaintext health data — only pre-encrypted ciphertext.

---

## Data Sources

Health data enters the system through two channels:

### 1. FHIR JSON Content (inline)

The primary data source is FHIR (Fast Healthcare Interoperability Resources) JSON. A FHIR Bundle is submitted directly in the API request body:

```
POST /api/shl
Content-Type: application/json

{
  "content": {
    "resourceType": "Bundle",
    "type": "collection",
    "entry": [
      {
        "resource": {
          "resourceType": "Patient",
          "name": [{"family": "Smith", "given": ["John"]}]
        }
      },
      {
        "resource": {
          "resourceType": "Immunization",
          "vaccineCode": {"text": "COVID-19"},
          "occurrenceDateTime": "2024-01-15"
        }
      }
    ]
  },
  "label": "John's Immunization Record"
}
```

The `content` field accepts any valid JSON — typically a FHIR Bundle containing patient records, immunizations, lab results, or other clinical documents. JSON content is automatically tagged with content type `application/fhir+json;fhirVersion=4.0.1` per the SHL spec recommendation.

### 2. File Upload (multipart)

Any file type can be shared — PDFs, clinical documents, FHIR bundles stored as files, images:

```
POST /api/shl
Content-Type: multipart/form-data

file:    @patient-summary.pdf  (type: application/pdf)
options: {"label": "Patient Summary", "passcode": "1234"}
```

The file content is read as raw bytes, encrypted into a JWE, and stored in S3. The original file is never persisted in plaintext.

### Data Flow From Source to Storage

```
  Input (FHIR JSON or file bytes)
                │
                ▼
  ┌──────────────────────────┐
  │  Serialize to string     │
  │  (JSON → string, or      │
  │   file bytes → UTF-8)    │
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │  EncryptionService       │
  │                          │
  │  1. Generate random      │
  │     256-bit AES key      │
  │  2. JWE encrypt:         │
  │     alg: dir             │
  │     enc: A256GCM         │
  │     zip: DEF (deflate)   │
  │     cty: (content type)  │
  │  3. Output: compact JWE  │
  │     string (5 parts,     │
  │     base64url-encoded)   │
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐      ┌────────────────────────┐
  │  S3StorageService        │      │  MongoDB               │
  │                          │      │                        │
  │  Upload JWE string to    │      │  Save metadata:        │
  │  s3://shl-data/payloads/ │      │  - SHL config          │
  │  {shlId}/{contentId}.jwe │      │  - Content reference   │
  │                          │      │    (s3Key, MIME type,  │
  │  (plaintext is NEVER     │      │     original filename) │
  │   stored anywhere)       │      │                        │
  └──────────────────────────┘      └────────────────────────┘
```

---

## How a Secure Link is Created

### Step-by-step creation flow

```
  Creator calls POST /api/shl
                │
                ▼
  ┌──────────────────────────────────────────────────┐
  │  1. GENERATE CRYPTOGRAPHIC MATERIAL              │
  │                                                  │
  │     encryptionKey = 32 random bytes → base64url  │
  │     manifestId    = 32 random bytes → base64url  │
  │     (both 256-bit entropy, cryptographically     │
  │      secure via SecureRandom)                    │
  └──────────────────────┬───────────────────────────┘
                         │
                         ▼
  ┌──────────────────────────────────────────────────┐
  │  2. PROCESS PROTECTION OPTIONS                   │
  │                                                  │
  │     Passcode provided?                           │
  │       → BCrypt hash (never stored in plaintext)  │
  │       → Set remaining attempts = 10              │
  │       → Add "P" to flags                         │
  │                                                  │
  │     Single-use requested?                        │
  │       → Add "U" to flags                         │
  │       → Cannot combine with long-term ("L")      │
  │       → Cannot combine with passcode ("P")       │
  │                                                  │
  │     Expiration provided?                         │
  │       → Calculate expiresAt = now + seconds      │
  └──────────────────────┬───────────────────────────┘
                         │
                         ▼
  ┌──────────────────────────────────────────────────┐
  │  3. ENCRYPT CONTENT                              │
  │                                                  │
  │     JWE encryption using:                        │
  │       Algorithm:   dir (direct key agreement)    │
  │       Encryption:  A256GCM (AES-256 in GCM mode) │
  │       Compression: DEF (DEFLATE)                 │
  │       Content-Type: cty header (MIME type)       │
  │                                                  │
  │     Input:  plaintext health data                │
  │     Key:    the generated encryptionKey          │
  │     Output: compact JWE serialization            │
  │             (header.encKey.iv.ciphertext.tag)    │
  └──────────────────────┬───────────────────────────┘
                         │
                         ▼
  ┌──────────────────────────────────────────────────┐
  │  4. STORE                                        │
  │                                                  │
  │     MongoDB: SHL metadata document               │
  │       { manifestId, flags, passcodeHash,         │
  │         expiresAt, active, singleUse,            │
  │         encryptionKey (server-side only) }       │
  │                                                  │
  │     MongoDB: Content reference document          │
  │       { shlId, contentType, s3Key,               │
  │         originalFileName, contentLength }        │
  │                                                  │
  │     S3: Encrypted JWE payload                    │
  │       payloads/{shlId}/{contentId}.jwe           │
  └──────────────────────┬───────────────────────────┘
                         │
                         ▼
  ┌──────────────────────────────────────────────────┐
  │  5. BUILD SHLINK URL                             │
  │                                                  │
  │     SHL Payload JSON:                            │
  │     {                                            │
  │       "url": "https://server/api/shl/manifest/   │
  │               {manifestId}",                     │
  │       "key": "{base64url encryption key}",       │
  │       "exp": 1770403161,                         │
  │       "flag": "P",                               │
  │       "label": "My Health Record",               │
  │       "v": 1                                     │
  │     }                                            │
  │                                                  │
  │     Encode to base64url → append to viewer URL:  │
  │     https://server/view.html#shlink:/eyJ...      │
  │                                                  │
  │     The fragment (#shlink:/...) is NEVER sent    │
  │     to the server — the encryption key stays     │
  │     client-side only.                            │
  └──────────────────────┬───────────────────────────┘
                         │
                         ▼
  ┌──────────────────────────────────────────────────┐
  │  6. GENERATE QR CODE                             │
  │                                                  │
  │     Encode the shlink URL into a QR code:        │
  │     - Format: PNG image                          │
  │     - Error correction: Level M                  │
  │     - Size: 300x300px (configurable)             │
  │     - Returned as base64 data URI:               │
  │       data:image/png;base64,iVBORw0KGgo...       │
  └──────────────────────┬───────────────────────────┘
                         │
                         ▼
  ┌──────────────────────────────────────────────────┐
  │  7. RETURN RESPONSE                              │
  │                                                  │
  │     {                                            │
  │       "id": "abc123",                            │
  │       "shlinkUrl": "https://...#shlink:/eyJ...", │
  │       "qrCode": "data:image/png;base64,...",     │
  │       "managementUrl": "https://.../api/shl/abc",│
  │       "label": "My Health Record",               │
  │       "flags": "P",                              │
  │       "expiresAt": "2026-02-07T18:00:00Z",       │
  │       "singleUse": false                         │
  │     }                                            │
  └──────────────────────────────────────────────────┘
```

The creator shares the `shlinkUrl` or `qrCode` with the intended recipient through any channel (email, SMS, printed QR code, etc.). The URL contains everything needed for decryption — the server never needs to know the key.

---

## How a Secure Link is Accessed

When a recipient opens a SMART Health Link (by clicking the URL or scanning the QR code):

```
  Recipient opens: https://server/view.html#shlink:/eyJ...
                │
                ▼
  ┌──────────────────────────────────────────────────┐
  │  BROWSER (view.html)                             │
  │                                                  │
  │  1. Extract fragment: #shlink:/eyJ...            │
  │     (fragment is NOT sent to server — privacy)   │
  │                                                  │
  │  2. Base64url-decode the payload:                │
  │     { url, key, exp, flag, label, v }            │
  │                                                  │
  │  3. Check expiration locally                     │
  │                                                  │
  │  4. If flag contains "P" → prompt for passcode   │
  └──────────────────────┬───────────────────────────┘
                         │
                         ▼
  ┌──────────────────────────────────────────────────┐
  │  POST {url}  (manifest endpoint)                 │
  │  Body: {                                         │
  │    "recipient": "SHL Viewer (Web)",              │
  │    "passcode": "1234",        (if P-flag)        │
  │    "embeddedLengthMax": 10485760                 │
  │  }                                               │
  └──────────────────────┬───────────────────────────┘
                         │
                         ▼
  ┌──────────────────────────────────────────────────┐
  │  SERVER-SIDE VALIDATION                          │
  │                                                  │
  │  1. Look up SHL by manifestId                    │
  │  2. Check: active? → 404 if not                   │
  │  3. Check: expired? → 404 if yes                  │
  │  4. If P-flag:                                   │
  │     a. Atomic decrement passcode attempts        │
  │        (findAndModify, race-safe)                │
  │     b. BCrypt-verify passcode                    │
  │     c. Wrong → 401 + remaining attempts          │
  │     d. Correct → restore attempt counter         │
  │     e. 0 attempts left → deactivate SHL          │
  │  5. Log access event (IP, user-agent, recipient) │
  │  6. If single-use → deactivate after response    │
  └──────────────────────┬───────────────────────────┘
                         │
                         ▼
  ┌──────────────────────────────────────────────────┐
  │  BUILD MANIFEST RESPONSE                         │
  │                                                  │
  │  For each content file:                          │
  │                                                  │
  │  Case A — Small file (JWE ≤ embeddedLengthMax):  │
  │    Fetch JWE from S3, return inline:             │
  │    { "contentType": "application/fhir+json",     │
  │      "embedded": "eyJhbGci..." }                 │
  │                                                  │
  │  Case B — Large file:                            │
  │    Create single-use download token (1hr TTL):   │
  │    { "contentType": "application/pdf",           │
  │      "location": "/api/shl/file/{tokenId}" }     │
  └──────────────────────┬───────────────────────────┘
                         │
                         ▼
  ┌────────────────────────────────────────────────────┐
  │  CLIENT-SIDE DECRYPTION                            │
  │                                                    │
  │  For each file in manifest:                        │
  │    1. Get JWE string (embedded or via location)    │
  │    2. Parse JWE compact serialization              │
  │    3. Import key via Web Crypto API (AES-GCM)      │
  │    4. Decrypt ciphertext + verify GCM auth tag     │
  │    5. Decompress with DEFLATE (DecompressionStream)│
  │    6. Display JSON or offer file download          │
  │                                                    │
  │  The server NEVER sees the decryption key or       │
  │  the decrypted content.                            │
  └────────────────────────────────────────────────────┘
```

### File Download Token Flow (for large files)

When a file is too large to embed in the manifest response, the server issues a single-use download token:

```
  Client receives manifest with "location" URL
                │
                ▼
  GET /api/shl/file/{tokenId}
                │
                ▼
  ┌──────────────────────────────────────────────────┐
  │  1. Atomic findAndModify: find token where       │
  │     consumed=false, set consumed=true            │
  │     (prevents double-download, race-safe)        │
  │                                                  │
  │  2. Validate token not expired                   │
  │                                                  │
  │  3. Fetch encrypted JWE from S3                  │
  │                                                  │
  │  4. Stream as Content-Type: application/jose     │
  │                                                  │
  │  5. Log FILE_DOWNLOAD access event               │
  │                                                  │
  │  Token is consumed — second request returns 404  │
  │  Token auto-expires via MongoDB TTL index        │
  └──────────────────────────────────────────────────┘
```

---

## Security Model

### Encryption

| Layer              | Algorithm                                                  | Purpose                                      |
|--------------------|------------------------------------------------------------|----------------------------------------------|
| Payload encryption | JWE with `alg:dir`, `enc:A256GCM`, `zip:DEF`, `cty` header | Encrypts health data at rest in S3           |
| Key size           | 256-bit (32 bytes)                                         | Generated via `java.security.SecureRandom`   |
| Key delivery       | Embedded in URL fragment (`#shlink:/`)                     | Never transmitted to server in HTTP requests |
| Passcode hashing   | BCrypt                                                     | Protects passcodes in the database           |

### Zero-Knowledge Architecture

The encryption key is part of the URL fragment (`#shlink:/eyJ...`). Per HTTP standards, the fragment is **never sent to the server** — it stays entirely in the browser. This means:

1. The server stores encrypted JWE payloads but cannot decrypt them
2. The server never sees the encryption key during access
3. Even a database breach exposes only ciphertext (the `encryptionKey` field in `ShlDocument` is used only during creation, not during access)
4. Decryption happens exclusively in the client browser using the Web Crypto API

### Passcode Brute-Force Protection

```
  Attempt 1 (wrong): remaining = 9
  Attempt 2 (wrong): remaining = 8
  ...
  Attempt 10 (wrong): remaining = 0 → SHL DEACTIVATED
```

- Each SHL starts with 10 passcode attempts (configurable)
- Failed attempts are decremented atomically using MongoDB `findAndModify` (race-safe under concurrent requests)
- A correct passcode restores the attempt counter
- When attempts reach 0, the SHL is permanently deactivated

### Single-Use Tokens

File download tokens are:
- Generated with 256-bit random IDs
- Valid for 60 minutes (configurable)
- Consumed atomically on first use (prevents replay)
- Auto-deleted by MongoDB TTL index after expiration

---

## API Reference

### Management API

#### Create SHL from JSON

```http
POST /api/shl
Content-Type: application/json

{
  "content": { "resourceType": "Bundle", ... },
  "label": "My Health Record",
  "passcode": "1234",
  "expirationInSeconds": 3600,
  "singleUse": false,
  "longTerm": false
}
```

**Response (201):**
```json
{
  "id": "69862749ee8a95c7044d7281",
  "shlinkUrl": "http://localhost:8080/view.html#shlink:/eyJ...",
  "qrCode": "data:image/png;base64,iVBORw0KGgo...",
  "managementUrl": "http://localhost:8080/api/shl/69862749ee8a95c7044d7281",
  "label": "My Health Record",
  "flags": "P",
  "expiresAt": "2026-02-06T18:39:21.674Z",
  "singleUse": false
}
```

#### Create SHL from File Upload

```http
POST /api/shl
Content-Type: multipart/form-data

file:    (binary file data)
options: {"label": "Lab Results", "passcode": "secret"}
```

#### List SHLs

```http
GET /api/shl?active=true&page=0&size=20
```

**Response (200):**
```json
{
  "content": [
    {
      "id": "...",
      "label": "My Health Record",
      "flags": "P",
      "active": true,
      "singleUse": false,
      "expiresAt": "2026-02-06T18:39:21.674Z",
      "createdAt": "2026-02-06T17:39:21.687Z",
      "contentCount": 1,
      "accessCount": 3
    }
  ],
  "totalElements": 1,
  "page": 0,
  "size": 20
}
```

#### Get SHL Details

```http
GET /api/shl/{id}
```

Returns full details including shlink URL, base64 QR code, content metadata, and total access count.

#### Deactivate SHL

```http
DELETE /api/shl/{id}
```

**Response:** `204 No Content`

Soft-deactivates the SHL. Subsequent access attempts return `404 Not Found` (indistinguishable from a non-existent SHL, per spec).

#### Get Access Log

```http
GET /api/shl/{id}/access-log?page=0&size=50
```

**Response (200):**
```json
{
  "content": [
    {
      "id": "...",
      "action": "MANIFEST_REQUEST",
      "recipient": "Test App",
      "ipAddress": "192.168.1.1",
      "userAgent": "Mozilla/5.0...",
      "success": true,
      "failureReason": null,
      "createdAt": "2026-02-06T17:42:37.733Z"
    }
  ],
  "page": 0,
  "size": 50
}
```

### Protocol API (SHL Spec)

#### Manifest Request

```http
POST /api/shl/manifest/{manifestId}
Content-Type: application/json

{
  "recipient": "Hospital Portal",       // required
  "passcode": "1234",                   // required if P-flag
  "embeddedLengthMax": 10485760         // optional
}
```

**Response (200):**
```json
{
  "status": "finalized",
  "files": [
    {
      "contentType": "application/fhir+json;fhirVersion=4.0.1",
      "embedded": "eyJ6aXAiOiJERUYi...",
      "lastUpdated": "2026-02-07T16:16:18.494Z"
    }
  ]
}
```

The `status` field indicates whether the manifest content may change:
- `"finalized"` — content will not change (default for non-L-flag SHLs)
- `"can-change"` — content may be updated over time (L-flag SHLs); response includes `Retry-After: 60` header to guide polling interval

#### File Download

```http
GET /api/shl/file/{tokenId}
```

**Response (200):**
```
Content-Type: application/jose

eyJ6aXAiOiJERUYiLCJlbmMiOiJBMjU2R0NNIiwiYWxnIjoiZGlyIn0...
```

---

## Data Models

### MongoDB Collections

#### `shlinks` — SHL metadata

| Field                       | Type                  | Description                                           |
|-----------------------------|-----------------------|-------------------------------------------------------|
| `id`                        | String                | MongoDB ObjectId                                      |
| `manifestId`                | String (unique index) | 43-char base64url, used in manifest URL               |
| `label`                     | String                | Optional display label (max 80 chars)                 |
| `encryptionKey`             | String                | 43-char base64url 256-bit AES key                     |
| `flags`                     | String                | Sorted flag string: `""`, `"L"`, `"LP"`, `"P"`, `"U"` |
| `passcodeHash`              | String                | BCrypt hash; null if no passcode                      |
| `passcodeFailuresRemaining` | Integer               | Countdown from 10; null if no passcode                |
| `expiresAt`                 | Instant               | Optional expiration timestamp                         |
| `active`                    | boolean               | `true` = accessible, `false` = deactivated            |
| `singleUse`                 | boolean               | Auto-deactivate after first manifest fetch            |
| `createdAt`                 | Instant               | Auto-set by `@CreatedDate`                            |
| `updatedAt`                 | Instant               | Auto-set by `@LastModifiedDate`                       |

#### `shl_contents` — Content references

| Field              | Type             | Description                                            |
|--------------------|------------------|--------------------------------------------------------|
| `id`               | String           | MongoDB ObjectId                                       |
| `shlId`            | String (indexed) | FK to `shlinks`                                        |
| `contentType`      | String           | MIME type (`application/fhir+json`, `application/pdf`) |
| `s3Key`            | String           | S3 object key: `payloads/{shlId}/{contentId}.jwe`      |
| `originalFileName` | String           | Original filename for uploads; null for JSON           |
| `contentLength`    | long             | Original unencrypted size in bytes                     |
| `createdAt`        | Instant          | Timestamp                                              |

#### `access_logs` — Audit trail

| Field           | Type              | Description                                                              |
|-----------------|-------------------|--------------------------------------------------------------------------|
| `id`            | String            | MongoDB ObjectId                                                         |
| `shlId`         | String (indexed)  | FK to `shlinks`                                                          |
| `action`        | String            | `MANIFEST_REQUEST`, `FILE_DOWNLOAD`, `PASSCODE_FAILURE`, `DIRECT_ACCESS` |
| `recipient`     | String            | Self-reported recipient name                                             |
| `ipAddress`     | String            | Client IP address                                                        |
| `userAgent`     | String            | HTTP User-Agent header                                                   |
| `success`       | boolean           | Whether the action succeeded                                             |
| `failureReason` | String            | Null on success; describes failure otherwise                             |
| `createdAt`     | Instant (indexed) | Timestamp                                                                |

#### `file_download_tokens` — Short-lived download tokens

| Field       | Type    | Description                                     |
|-------------|---------|-------------------------------------------------|
| `id`        | String  | 43-char random token (used in download URL)     |
| `contentId` | String  | FK to `shl_contents`                            |
| `shlId`     | String  | FK to `shlinks`                                 |
| `expiresAt` | Instant | TTL index — MongoDB auto-deletes expired tokens |
| `consumed`  | boolean | Set atomically to `true` on first download      |

### S3 Bucket Layout

```
s3://shl-data/
└── payloads/
    ├── {shlId-1}/
    │   ├── {contentId-1}.jwe     ← Encrypted JWE compact serialization
    │   └── {contentId-2}.jwe
    └── {shlId-2}/
        └── {contentId-3}.jwe
```

All S3 objects contain JWE compact serialization strings — already encrypted, stored as plain text objects with content type `application/jose`.

---

## Configuration

### `application.yaml`

```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/shl}

app:
  base-url: ${APP_BASE_URL:http://localhost:8080}     # Public URL for link generation
  viewer-path: /view.html                              # Path to the static viewer page
  default-passcode-attempts: 10                        # Max wrong passcode attempts
  file-token-ttl-minutes: 60                           # Download token lifetime
  qr-code-default-size: 300                            # QR code dimension in pixels
  s3:
    bucket: ${S3_BUCKET:shl-data}                      # S3 bucket name
    region: ${AWS_REGION:us-east-1}                    # AWS region
    endpoint: ${S3_ENDPOINT:}                          # Override for LocalStack/MinIO
    payload-prefix: payloads/                          # S3 key prefix for payloads
```

### Environment Variables

| Variable       | Default                         | Description                               |
|----------------|---------------------------------|-------------------------------------------|
| `MONGODB_URI`  | `mongodb://localhost:27017/shl` | MongoDB connection string                 |
| `APP_BASE_URL` | `http://localhost:8080`         | Public base URL for generated links       |
| `S3_BUCKET`    | `shl-data`                      | S3 bucket for encrypted payloads          |
| `AWS_REGION`   | `us-east-1`                     | AWS region for S3                         |
| `S3_ENDPOINT`  | _(empty)_                       | S3 endpoint override (set for LocalStack) |

---

## Local Development

### Prerequisites

- Java 25
- Docker and Docker Compose

### Start Infrastructure

```bash
docker compose up -d
```

This starts:
- **MongoDB** on port `27017`
- **LocalStack** (S3 emulator) on port `4566` — auto-creates the `shl-data` bucket

### Run the Application

```bash
./gradlew bootRun
```

The app starts on `http://localhost:8080`.

### Quick Smoke Test

```bash
# Create a link
curl -s -X POST http://localhost:8080/api/shl \
  -H 'Content-Type: application/json' \
  -d '{
    "content": {"resourceType": "Bundle", "type": "collection"},
    "label": "Test Record",
    "expirationInSeconds": 3600
  }' | python3 -m json.tool

# List all links
curl -s http://localhost:8080/api/shl | python3 -m json.tool

# Open the shlinkUrl in a browser to test the viewer
```

### Tech Stack

| Component        | Technology                      | Version    |
|------------------|---------------------------------|------------|
| Framework        | Spring Boot + WebFlux           | 4.0.2      |
| Database         | MongoDB (reactive)              | 8.x        |
| Object Storage   | AWS S3 (async client)           | SDK 2.31.1 |
| Encryption       | nimbus-jose-jwt (JWE)           | 10.7       |
| QR Codes         | ZXing                           | 3.5.4      |
| Passcode Hashing | Spring Security Crypto (BCrypt) | —          |
| Language         | Java                            | 25         |
