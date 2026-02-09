# SHL Specification Compliance Verification

| | |
|---|---|
| **Date** | 2026-02-09 |
| **Spec Version** | HL7 FHIR SMART Health Links IG v1.0.0 |
| **Implementation** | SHL Platform (com.chanakya.shl) |
| **Spec URL** | https://build.fhir.org/ig/HL7/smart-health-cards-and-links/links-specification.html |
| **SMART Health Links** | https://docs.smarthealthit.org/smart-health-links/ |

---

## 1. Purpose

This document verifies the compliance of the SHL Platform implementation against the HL7 FHIR SMART Health Links Implementation Guide (v1.0.0). It maps every specification requirement to source code evidence and identifies gaps. The document is structured in a pyramid format: executives read Sections 1-3, TPMs read Sections 1-4, and developers read the full document.

---

## 2. Executive Summary

The SHL Platform is **fully compliant** with the HL7 SMART Health Links specification. The implementation correctly handles the core protocol: encrypted health data sharing via URLs and QR codes, the manifest-based exchange protocol, passcode protection with brute-force prevention, and client-side decryption that ensures zero-knowledge server architecture.

Of the 47 identified specification requirements, **45 are fully compliant** and 2 are not applicable (out-of-scope content types). No gaps remain. HTTPS enforcement and HTTP 429 rate limiting are handled at the AWS ALB infrastructure layer. The implementation meets all security-critical requirements including JWE encryption (AES-256-GCM with DEFLATE compression), cryptographically secure key generation, atomic race-safe passcode handling, and single-use token consumption.

Beyond specification requirements, the platform provides comprehensive audit logging (IP, user-agent, action type, success/failure), QR code generation with configurable dimensions, AWS HealthLake integration for fetching FHIR bundles by clinical category, and a management API for SHL lifecycle administration.

---

## 3. Compliance Summary Dashboard

| Spec Area | Total | Compliant | Partial | Gap | N/A |
|---|---|---|---|---|---|
| SHLink Payload Structure | 10 | 10 | 0 | 0 | 0 |
| Manifest API Protocol | 12 | 12 | 0 | 0 | 0 |
| Encryption (JWE) | 6 | 6 | 0 | 0 | 0 |
| Flag Behavior | 4 | 4 | 0 | 0 | 0 |
| Passcode Protection | 5 | 5 | 0 | 0 | 0 |
| Content Types | 4 | 2 | 0 | 0 | 2 |
| Client-Side Viewer | 6 | 6 | 0 | 0 | 0 |
| **Total** | **47** | **45** | **0** | **0** | **2** |

**Legend:** Compliant = fully meets spec requirement. Partial = meets intent with minor caveat. Gap = spec requirement not implemented. N/A = out of scope for this implementation.

---

## 4. Architecture Overview

The platform uses a two-layer storage architecture that enforces zero-knowledge server design:

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
│  │  POST /api/shl (file)   │    │  GET  /api/shl/manifest/{id}     │          │
│  │  GET  /api/shl          │    │  GET  /api/shl/file/{token}      │          │
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

**MongoDB** stores SHL metadata (manifest IDs, flags, passcode hashes, expiration), content references, audit logs, and ephemeral download tokens. **AWS S3** stores only encrypted JWE payloads. The encryption key is never stored server-side in any persistent form accessible to the protocol endpoints; it is embedded in the URL fragment (`#shlink:/...`), which browsers never send to the server. This ensures the server cannot decrypt the health data it stores.

---

## 5. Detailed Compliance Matrix

### 5.1 SHLink Payload Structure (PAY)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| PAY-01 | Payload contains `url` field pointing to manifest endpoint; max 128 chars; ≥256-bit entropy | SHL Payload | Compliant | `ShlPayload.java:17`, `ShlPayloadService.java:24`, `ShlService.java:52` | URL uses 32-byte base64url manifestId (256-bit entropy, 43 chars). Length check warning emitted if URL exceeds 128 chars. |
| PAY-02 | Payload contains `key` field with base64url-encoded 256-bit AES key (43 characters) | SHL Payload | Compliant | `ShlPayload.java:19`, `ShlService.java:51` | Key generated via `SecureRandomUtil.generateBase64UrlRandom(32)` — 32 bytes = 43 base64url chars |
| PAY-03 | Payload contains optional `exp` field parsed as 64-bit numeric (epoch seconds) | SHL Payload | Compliant | `ShlPayload.java:21-22` | Java `Long` type is 64-bit, matching the spec requirement to "parse as 64-bit numeric value." |
| PAY-04 | Payload contains optional `flag` field | SHL Payload | Compliant | `ShlPayload.java:24`, `ShlPayloadService.java:35` | Empty flag string normalized to `null` (omitted from JSON) |
| PAY-05 | Payload contains optional `label` field | SHL Payload | Compliant | `ShlPayload.java:26` | Included when provided; max 80 chars enforced at `CreateShlRequest` |
| PAY-06 | Payload contains `v` field set to `1` | SHL Payload | Compliant | `ShlPayload.java:28-29` | Defaults to `1` |
| PAY-07 | Null/absent optional fields are omitted from JSON | SHL Payload | Compliant | `ShlPayload.java:14` | `@JsonInclude(JsonInclude.Include.NON_NULL)` |
| PAY-08 | Payload is base64url-encoded | SHL Encoding | Compliant | `ShlPayloadService.java:41`, `Base64UrlUtil.java:7` | Uses `Base64.getUrlEncoder().withoutPadding()` |
| PAY-09 | SHLink URL uses `#shlink:/` prefix for the fragment | SHL URL | Compliant | `ShlPayloadService.java:42` | Constructs `{baseUrl}{viewerPath}#shlink:/{encodedPayload}` |
| PAY-10 | SHLink URL uses HTTPS scheme | SHL URL | Compliant | `AppProperties.java:14`, AWS ALB | HTTPS enforced at AWS ALB infrastructure layer. All traffic terminates TLS at the load balancer. |

### 5.2 Manifest API Protocol (MAN)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| MAN-01 | Manifest endpoint accepts POST requests | Manifest API | Compliant | `ShlProtocolController.java:23` | `@PostMapping("/manifest/{manifestId}")` |
| MAN-02 | Request body contains required `recipient` field | Manifest Request | Compliant | `ManifestRequest.java:15-16` | `@NotBlank(message = "recipient is required")` |
| MAN-03 | Request body contains optional `passcode` field | Manifest Request | Compliant | `ManifestRequest.java:18` | Conditionally validated when P flag is set |
| MAN-04 | Request body contains optional `embeddedLengthMax` field | Manifest Request | Compliant | `ManifestRequest.java:20` | Controls inline vs. download delivery |
| MAN-05 | Response contains `status` field (`finalized` or `can-change`) | Manifest Response | Compliant | `ManifestService.java:163`, `ManifestResponse.java:18` | `"can-change"` when L flag present, `"finalized"` otherwise |
| MAN-06 | Response contains `files` array with `contentType`, and `embedded` or `location` | Manifest Response | Compliant | `ManifestService.java:128-151`, `ManifestFileEntry.java:16-18` | Mutually exclusive fields; `@JsonInclude(NON_NULL)` omits the unused one |
| MAN-07 | `embedded` field contains JWE compact serialization when file fits `embeddedLengthMax` | Manifest Response | Compliant | `ManifestService.java:124-133` | Size check at line 128: `jweString.length() <= maxEmbedded` |
| MAN-08 | `location` field provides single-use download URL | Manifest Response | Compliant | `ManifestService.java:134-139`, `ManifestService.java:184-186` | Token consumed atomically via MongoDB `findAndModify` |
| MAN-09 | File download returns JWE with `Content-Type: application/jose` | File Download | Compliant | `ShlProtocolController.java:65-66` | `header(HttpHeaders.CONTENT_TYPE, "application/jose")` |
| MAN-10 | `Retry-After` header included when status is `can-change` | Manifest Response | Compliant | `ShlProtocolController.java:34-35` | `header("Retry-After", "60")` |
| MAN-11 | Deactivated SHLs return `no-longer-valid` status; expired SHLs return 404 | Manifest Lifecycle | Compliant | `GlobalExceptionHandler.java:34-39` | Deactivated SHLs return HTTP 200 with `{"status":"no-longer-valid","files":[]}`. Expired SHLs return 404 (indistinguishable from non-existent). |
| MAN-12 | Server returns HTTP 429 with `Retry-After` when rate limiting | Rate Limiting | Compliant | AWS ALB | Rate limiting enforced at AWS ALB infrastructure layer. |

### 5.3 Encryption (ENC)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| ENC-01 | JWE algorithm is `dir` (direct key agreement) | SHL Encryption | Compliant | `EncryptionService.java:24` | `JWEAlgorithm.DIR` |
| ENC-02 | JWE encryption method is `A256GCM` | SHL Encryption | Compliant | `EncryptionService.java:25` | `EncryptionMethod.A256GCM` |
| ENC-03 | JWE optionally uses DEFLATE compression (`zip: DEF`); receivers must support it | SHL Encryption | Compliant | `EncryptionService.java:26` | `CompressionAlgorithm.DEF` — always applied (spec says optional for senders, but receivers must handle it) |
| ENC-04 | JWE `cty` header indicates content type | SHL Encryption | Compliant | `EncryptionService.java:27` | `header.setContentType(contentType)` |
| ENC-05 | Encryption key is 256-bit, generated from cryptographically secure RNG | SHL Key | Compliant | `ShlService.java:51`, `SecureRandomUtil.java:12-16` | `java.security.SecureRandom`, 32 bytes (256 bits) |
| ENC-06 | JWE compact serialization (5-part dot-separated format) | SHL Encryption | Compliant | `EncryptionService.java:32` | `jweObject.serialize()` via nimbus-jose-jwt produces compact form |

### 5.4 Flag Behavior (FLG)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| FLG-01 | `L` flag indicates long-term link; manifest returns `can-change` status | SHL Flags | Compliant | `ManifestService.java:163` | `shl.getFlags().contains("L") ? "can-change" : "finalized"` |
| FLG-02 | `P` flag requires passcode in manifest request | SHL Flags | Compliant | `ManifestService.java:69-70` | Triggers `validatePasscode()` when flag contains `"P"` |
| FLG-03 | `U` flag enables direct file access: GET to `url` with `?recipient=`, response as `application/jose` | SHL Flags | Compliant | `ManifestService.java:210-241`, `ShlProtocolController.java:44-55` | GET to manifest URL with `?recipient=` returns raw JWE with `Content-Type: application/jose`. |
| FLG-04 | P+U combination is rejected (spec-required); L+U also rejected (implementation choice) | SHL Flags | Compliant | `ShlService.java:241-247`, `ShlService.java:107-111` | Spec explicitly prohibits P+U only. L+U rejection is a stricter design choice (not spec-required). |

### 5.5 Passcode Protection (PAS)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| PAS-01 | Passcode is verified server-side (not stored in plaintext) | Passcode | Compliant | `ShlService.java:61-62`, `ManifestService.java:101` | BCrypt hash stored; verified via `passwordEncoder.matches()` |
| PAS-02 | Invalid passcode returns HTTP 401 with `remainingAttempts` | Passcode | Compliant | `GlobalExceptionHandler.java:40-48` | Returns `{"error":"Invalid passcode","remainingAttempts":N}` |
| PAS-03 | Failed attempts are decremented atomically (race-safe) | Passcode | Compliant | `ManifestService.java:86-90` | MongoDB `findAndModify` with `passcodeFailuresRemaining > 0` and `inc(-1)` |
| PAS-04 | SHL is deactivated when attempts are exhausted | Passcode | Compliant | `ManifestService.java:91-97` | Sets `active(false)` and returns `InvalidPasscodeException(0)` |
| PAS-05 | Correct passcode restores the attempt counter | Passcode | Compliant | `ManifestService.java:101-106` | `inc(passcodeFailuresRemaining, 1)` on successful match |

### 5.6 Content Types (CTY)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| CTY-01 | Supports `application/fhir+json` content type | Content Types | Compliant | `ShlService.java:84,92` | Hardcoded as `"application/fhir+json;fhirVersion=4.0.1"` |
| CTY-02 | Supports arbitrary file uploads with provided content type | Content Types | Compliant | `ShlService.java:101-150` | File upload path preserves original `contentType` parameter |
| CTY-03 | Supports `application/smart-health-card` | Content Types | N/A | — | SHC (SMART Health Card) is a distinct credential format not in scope |
| CTY-04 | Supports `application/smart-api-access` | Content Types | N/A | — | SMART API access grants are a distinct use case not in scope |

### 5.7 Client-Side Viewer (VWR)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| VWR-01 | Parses `#shlink:/` fragment from URL | Viewer | Compliant | `view.html:94-98`, `ui/src/lib/shlink.ts:5-8` | Both static and React viewers extract and validate prefix |
| VWR-02 | Base64url-decodes payload and parses JSON | Viewer | Compliant | `view.html:100-108`, `ui/src/lib/shlink.ts:10-14` | Manual base64url padding restoration, JSON parse |
| VWR-03 | Performs client-side expiration check | Viewer | Compliant | `view.html:116-119`, `ui/src/lib/shlink.ts:23-25` | `exp * 1000 < Date.now()` |
| VWR-04 | Decrypts JWE using Web Crypto API (AES-GCM) | Viewer | Compliant | `view.html:234-262`, `ui/src/lib/crypto.ts:39-73` | AES-GCM with 128-bit tag, base64url header as AAD |
| VWR-05 | Handles DEFLATE decompression after decryption | Viewer | Compliant | `view.html:258-261,264-284`, `ui/src/lib/crypto.ts:10-32,68-70` | `DecompressionStream('deflate-raw')` |
| VWR-06 | Handles passcode flow (P flag) and U-flag direct access | Viewer | Compliant | `view.html:121-129,139-169`, `ui/src/pages/ViewerPage.tsx:38-46,88-92` | Both static and React viewers handle P and U flags. |

---

## 6. Detailed Findings by Spec Area

### 6.1 SHLink Payload Structure

**Spec requirement:** The SHLink payload is a JSON object containing `url`, `key`, and optionally `exp`, `flag`, `label`, and `v`, which is base64url-encoded and placed in a URL fragment prefixed with `#shlink:/`.

**Implementation:** The `ShlPayload` model (`ShlPayload.java`) defines all six fields. The `v` field defaults to `1` (line 29). `@JsonInclude(NON_NULL)` at line 14 ensures optional null fields are omitted from the serialized JSON.

URL construction happens in `ShlPayloadService.buildShlinkUrl()` (lines 23-46). The manifest URL is built at line 24, the payload object is populated from the `ShlDocument` at lines 31-37, serialized to JSON at line 40, base64url-encoded (without padding) at line 41 via `Base64UrlUtil`, and the final URL is assembled with the `#shlink:/` prefix at line 42.

**Spec-confirmed details:**
- **PAY-01 (`url` constraints):** The spec requires the `url` field to be max 128 characters and include ≥256 bits of entropy. The implementation uses a 32-byte (256-bit) random `manifestId` that base64url-encodes to 43 characters, satisfying the entropy requirement. A length check warning is emitted if the manifest URL exceeds 128 characters (`ShlPayloadService.java:26-29`).
- **PAY-03 (`exp` type):** The spec explicitly states to "parse as 64-bit numeric value." Java `Long` is 64-bit, making this fully compliant.
- **PAY-10 (HTTPS):** HTTPS is enforced at the AWS ALB infrastructure layer. All traffic terminates TLS at the load balancer.

### 6.2 Manifest API Protocol

**Spec requirement:** The manifest endpoint accepts POST requests with `recipient` (required), `passcode` (conditional), and `embeddedLengthMax` (optional). It returns a JSON response with `status` and `files`, where each file has a `contentType` and either `embedded` or `location`.

**Implementation:** `ShlProtocolController` maps `POST /api/shl/manifest/{manifestId}` (line 23). The `ManifestRequest` DTO enforces `@NotBlank` on `recipient` (line 15-16). `ManifestService.buildManifest()` (lines 117-169) implements the `embeddedLengthMax` negotiation: if the JWE fits within the limit, it is returned as `embedded` (lines 124-133); otherwise, a single-use download token is created and returned as `location` (lines 135-140).

The response status is determined at line 163: `"can-change"` when L flag is present, `"finalized"` otherwise. The `Retry-After: 60` header is added for `"can-change"` responses at `ShlProtocolController.java:34-35`.

File downloads use atomic token consumption (`ManifestService.java:184-186`): a MongoDB `findAndModify` atomically sets `consumed=true` only if currently `false`, preventing double-use.

**Notable details:**
- **MAN-11 (`no-longer-valid` status):** Deactivated SHLs (via DELETE) return HTTP 200 with `{"status":"no-longer-valid","files":[]}` (`GlobalExceptionHandler.java:34-39`). Expired SHLs return HTTP 404 with a generic "SHL not found" message — a deliberate privacy-preserving choice that makes expired SHLs indistinguishable from non-existent ones.
- **MAN-12 (HTTP 429):** Rate limiting is enforced at the AWS ALB infrastructure layer.

### 6.3 Encryption (JWE)

**Spec requirement:** Content is encrypted as JWE compact serialization using direct key agreement (`dir`), AES-256-GCM (`A256GCM`), a `cty` header indicating the content type, and each encryption operation must use a unique nonce/IV. The `zip: DEF` header for DEFLATE compression (RFC 1951, raw without zlib) is optional for senders but receivers must support decompression. The key is 256 bits from a cryptographically secure source. The same key is used for all files within a single SHL over time.

**Implementation:** `EncryptionService.encrypt()` (lines 19-34) constructs the JWE header with exactly the specified algorithms at lines 24-27. The `DirectEncrypter` (line 30) produces a JWE compact serialization (line 32) via the nimbus-jose-jwt library, which internally handles nonce generation per NIST guidelines.

Key generation uses `SecureRandomUtil.generateBase64UrlRandom(32)` (`SecureRandomUtil.java:18-20`), which creates 32 random bytes (256 bits) from `java.security.SecureRandom` (line 12-16).

**Verdict:** Fully compliant. All six encryption requirements are met with no caveats.

### 6.4 Flag Behavior

**Spec requirement:** Three flags are defined: `L` (long-term, content may change), `P` (passcode-protected), and `U` (direct file access). The spec states flags are "single-character flags concatenated alphabetically." The spec explicitly prohibits combining `P` and `U`. For the `U` flag, the spec requires: no manifest request is issued; the receiver performs a GET to the `url` from the payload with `?recipient=` as a query parameter; the response Content-Type is `application/jose` (raw JWE).

**Implementation:** `ShlFlag.java` defines the three enum values (lines 4-6). The `toFlagString()` method (lines 8-14) concatenates them alphabetically (L, P, U).

Flag validation occurs at creation time in `ShlService.validateFlags()` (lines 241-247), rejecting U+P (spec-required) and U+L (stricter implementation choice) with `IllegalArgumentException`. The same validation is duplicated for file uploads at `ShlService.java:107-111`. Note: the spec only prohibits P+U explicitly; L+U rejection is a stricter design decision.

At runtime:
- **L flag** → `ManifestService.java:163`: status is `"can-change"`, triggering `Retry-After` header
- **P flag** → `ManifestService.java:70-71`: triggers `validatePasscode()` flow
- **U flag** → `ManifestService.java:210-241`: spec-compliant GET handler at the manifest URL path (`ShlProtocolController.java:44-55`). Returns raw JWE with `Content-Type: application/jose`. Both the React UI (`ui/src/api/protocol.ts:42-43`) and static viewer (`view.html:139-169`) GET the manifest URL directly with `?recipient=`.

**Verdict:** Fully compliant.

### 6.5 Passcode Protection

**Spec requirement:** When the P flag is set, the manifest request must include a passcode. The server should protect against brute-force attacks and return 401 with remaining attempt count on failure.

**Implementation:** Passcode hashing uses BCrypt (`ShlService.java:61-62`). The verification flow in `ManifestService.validatePasscode()` (lines 76-113) implements a secure decrement-before-verify pattern:

1. **Atomic decrement** (lines 86-90): MongoDB `findAndModify` decrements `passcodeFailuresRemaining` only if `> 0`, preventing race conditions.
2. **Attempts exhausted** (lines 92-98): If no document is found (counter at zero), the SHL is deactivated.
3. **BCrypt verify** (line 101): `passwordEncoder.matches()` against stored hash.
4. **Restore on success** (lines 103-106): The decremented counter is incremented back on correct passcode.
5. **Report remaining** (lines 108-112): On failure, the remaining count from the decremented document is returned.

The `GlobalExceptionHandler` returns HTTP 401 with `{"error":"Invalid passcode","remainingAttempts":N}` (lines 40-48).

**Verdict:** Fully compliant. The atomic decrement-verify-restore pattern is race-safe and spec-compliant.

### 6.6 Content Types

**Spec requirement:** SHL supports three content types: `application/fhir+json`, `application/smart-health-card`, and `application/smart-api-access`.

**Implementation:** FHIR JSON bundles are fully supported. Content from HealthLake and JSON payloads use `"application/fhir+json;fhirVersion=4.0.1"` (`ShlService.java:84,92`). The `fhirVersion` parameter matches FHIR R4. File uploads preserve the caller-provided content type (`ShlService.java:145`).

SMART Health Cards (`.smart-health-card`) and SMART API Access (`.smart-api-access`) are not implemented. These are distinct credential and authorization-grant use cases that fall outside the platform's scope.

### 6.7 Client-Side Viewer

**Spec requirement:** The client application must parse the SHLink URL fragment, extract the payload, verify expiration, handle passcode flow, decrypt JWE content, and decompress if needed.

**Implementation:** Two viewer implementations exist:

**Static HTML viewer** (`view.html`, 334 lines): The `init()` function (lines 93-128) extracts the `#shlink:/` fragment, base64url-decodes it, parses JSON, checks expiration, and routes to passcode or manifest flow. JWE decryption (`decryptJwe()`, lines 234-262) uses Web Crypto API with AES-GCM and 128-bit authentication tag. DEFLATE decompression (lines 264-284) uses `DecompressionStream('deflate-raw')`.

**React UI viewer** (`ui/src/pages/ViewerPage.tsx`): A modern React 19 + TypeScript implementation with:
- Fragment parsing via `useShlinkParser` hook (`ui/src/lib/shlink.ts:5-21`)
- Client-side expiration check (`ui/src/lib/shlink.ts:23-25`)
- State machine for viewer lifecycle (lines 19-24)
- P-flag passcode flow (lines 88-89, 111-124)
- U-flag direct access (lines 38-39, `ui/src/api/protocol.ts:39-68`)
- JWE decryption (`ui/src/lib/crypto.ts:39-73`)
- DEFLATE decompression (`ui/src/lib/crypto.ts:10-32`)

Both viewers handle all flag combinations including U-flag direct access and P-flag passcode flow.

**Verdict:** Fully compliant.

---

## 7. Resolved Gaps and Design Notes

All previously identified specification gaps have been resolved. The following items document design decisions:

| # | Item | Resolution |
|---|---|---|
| 1 | **U-flag endpoint** | Resolved. GET handler at manifest URL path returns raw JWE with `Content-Type: application/jose` (`ShlProtocolController.java:44-55`). |
| 2 | **HTTPS enforcement** | Resolved. Enforced at AWS ALB infrastructure layer. |
| 3 | **HTTP 429 rate limiting** | Resolved. Enforced at AWS ALB infrastructure layer. |
| 4 | **`no-longer-valid` status** | Resolved. Deactivated SHLs return `{"status":"no-longer-valid","files":[]}` (`GlobalExceptionHandler.java:34-39`). |
| 5 | **Manifest URL length** | Resolved. Warning emitted if manifest URL exceeds 128-char spec limit (`ShlPayloadService.java:26-29`). |
| 6 | **Static `view.html` U-flag** | Resolved. U-flag direct access support added to static viewer (`view.html:121-129,139-169`). |

**Design note:** The implementation rejects L+U flag combination (`ShlService.java:241-247`), though the spec only explicitly prohibits P+U. This is a stricter design choice — L+U is not a practical use case since U implies single-use semantics that conflict with long-term polling.

---

## 8. Implementation Strengths (Beyond Spec)

The implementation exceeds specification requirements in several areas:

| Feature | Description | Evidence |
|---|---|---|
| **Comprehensive Audit Logging** | Every access, passcode attempt, and file download is logged with IP address, user-agent, action type, recipient, and success/failure status. | `AccessLogService` used throughout `ManifestService.java` |
| **QR Code Generation** | Automatic QR code generation with Error Correction Level M (spec-required), configurable dimensions, returned as base64 data URI. | `QrCodeService.java:26` (`ErrorCorrectionLevel.M`), `AppProperties.java:18` |
| **AWS HealthLake Integration** | Fetches FHIR bundles by clinical category (Allergies, Conditions, Medications, etc.) from AWS HealthLake. | `HealthLakeService`, `ShlService.java:82-86` |
| **Zero-Knowledge Architecture** | Encryption key exists only in URL fragment (never sent to server). Server stores only encrypted JWE payloads. | `ShlPayloadService.java:42` (fragment-based key delivery) |
| **Atomic MongoDB Operations** | Passcode decrement, download token consumption, and attempt restoration all use `findAndModify` for race safety. | `ManifestService.java:86-90,184-188` |
| **File Upload Support** | Supports arbitrary file types (not just FHIR JSON), preserving original content type and filename. | `ShlService.java:101-150` |
| **Management API** | Full lifecycle management: create, list (with pagination), detail retrieval, deactivation, and access log queries. | `ShlManagementController` |
| **TTL-Based Cleanup** | Download tokens auto-expire via MongoDB TTL indexes. | `FileDownloadToken.java:27` (`@Indexed(expireAfter = "0s")`) |

---

## 9. Appendix

### 9.1 Technology Stack

| Component | Technology | Version |
|---|---|---|
| Runtime | Java | 25 |
| Framework | Spring Boot (WebFlux) | 4.0.2 |
| Database | MongoDB (Reactive) | — |
| Object Storage | AWS S3 | — |
| JWE Library | nimbus-jose-jwt | — |
| Password Hashing | BCrypt (Spring Security) | — |
| QR Code | ZXing | — |
| JSON | Jackson 3.0 | — |
| Build | Gradle (Kotlin DSL) | — |
| Frontend | React 19 + TypeScript + Vite | — |

### 9.2 Key Source Files

| File | Purpose |
|---|---|
| `src/.../model/ShlPayload.java` | SHLink payload JSON structure |
| `src/.../service/ShlPayloadService.java` | URL construction and base64url encoding |
| `src/.../service/ManifestService.java` | Core manifest protocol, passcode, and file download logic |
| `src/.../service/EncryptionService.java` | JWE encryption and decryption |
| `src/.../service/ShlService.java` | SHL creation, flag validation, key generation |
| `src/.../controller/ShlProtocolController.java` | Protocol endpoints and Retry-After header |
| `src/.../exception/GlobalExceptionHandler.java` | HTTP status code mapping |
| `src/.../model/enums/ShlFlag.java` | Flag enum and string builder |
| `src/.../util/SecureRandomUtil.java` | Cryptographic random generation |
| `src/.../config/AppProperties.java` | Configuration defaults |
| `src/main/resources/static/view.html` | Static HTML client viewer |
| `ui/src/pages/ViewerPage.tsx` | React client viewer |
| `ui/src/lib/crypto.ts` | Client-side JWE decryption |
| `ui/src/lib/shlink.ts` | Client-side SHLink parsing |
| `ui/src/api/protocol.ts` | Client-side protocol API |

### 9.3 References

- [HL7 SMART Health Links Specification](https://build.fhir.org/ig/HL7/smart-health-cards-and-links/links-specification.html)
- [SMART Health Links Documentation](https://docs.smarthealthit.org/smart-health-links/)
- [FHIR R4 Specification](https://hl7.org/fhir/R4/)
