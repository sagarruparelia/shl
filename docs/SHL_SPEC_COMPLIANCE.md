# SHL Specification Compliance Verification

| | |
|---|---|
| **Date** | 2026-02-09 |
| **Spec Version** | HL7 FHIR SMART Health Links IG v1.0.0 |
| **Implementation** | SHL Platform (com.chanakya.shl) |
| **Spec URL** | https://build.fhir.org/ig/HL7/smart-health-links/ |
| **SMART Health Links** | https://docs.smarthealthit.org/smart-health-links/ |

---

## 1. Purpose

This document verifies the compliance of the SHL Platform implementation against the HL7 FHIR SMART Health Links Implementation Guide (v1.0.0). It maps every specification requirement to source code evidence and identifies gaps. The document is structured in a pyramid format: executives read Sections 1-3, TPMs read Sections 1-4, and developers read the full document.

---

## 2. Executive Summary

The SHL Platform is **substantially compliant** with the HL7 SMART Health Links specification. The implementation correctly handles the core protocol: encrypted health data sharing via URLs and QR codes, the manifest-based exchange protocol, passcode protection with brute-force prevention, and client-side decryption that ensures zero-knowledge server architecture.

Of the 47 identified specification requirements, **41 are fully compliant**, 3 are partially compliant with minor caveats, 1 has a low-severity gap, and 2 are not applicable (out-of-scope content types). No critical or high-severity gaps were found. The implementation meets all security-critical requirements including JWE encryption (AES-256-GCM with DEFLATE compression), cryptographically secure key generation, atomic race-safe passcode handling, and single-use token consumption.

Beyond specification requirements, the platform provides comprehensive audit logging (IP, user-agent, action type, success/failure), QR code generation with configurable dimensions, AWS HealthLake integration for fetching FHIR bundles by clinical category, and a management API for SHL lifecycle administration.

The identified gap is operational in nature (rate limiting) and is addressable through infrastructure-layer controls without protocol changes.

---

## 3. Compliance Summary Dashboard

| Spec Area | Total | Compliant | Partial | Gap | N/A |
|---|---|---|---|---|---|
| SHLink Payload Structure | 10 | 8 | 2 | 0 | 0 |
| Manifest API Protocol | 12 | 10 | 1 | 1 | 0 |
| Encryption (JWE) | 6 | 6 | 0 | 0 | 0 |
| Flag Behavior | 4 | 4 | 0 | 0 | 0 |
| Passcode Protection | 5 | 5 | 0 | 0 | 0 |
| Content Types | 4 | 2 | 0 | 0 | 2 |
| Client-Side Viewer | 6 | 6 | 0 | 0 | 0 |
| **Total** | **47** | **41** | **3** | **1** | **2** |

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

**MongoDB** stores SHL metadata (manifest IDs, flags, passcode hashes, expiration), content references, audit logs, and ephemeral download tokens. **AWS S3** stores only encrypted JWE payloads. The encryption key is never stored server-side in any persistent form accessible to the protocol endpoints; it is embedded in the URL fragment (`#shlink:/...`), which browsers never send to the server. This ensures the server cannot decrypt the health data it stores.

---

## 5. Detailed Compliance Matrix

### 5.1 SHLink Payload Structure (PAY)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| PAY-01 | Payload contains `url` field pointing to manifest endpoint | SHL Payload | Compliant | `ShlPayload.java:17`, `ShlPayloadService.java:22` | URL constructed as `{baseUrl}/api/shl/manifest/{manifestId}` |
| PAY-02 | Payload contains `key` field with base64url-encoded 256-bit AES key | SHL Payload | Compliant | `ShlPayload.java:19`, `ShlService.java:51` | Key generated via `SecureRandomUtil.generateBase64UrlRandom(32)` |
| PAY-03 | Payload contains optional `exp` field as integer seconds since epoch | SHL Payload | Partial | `ShlPayload.java:21-22` | Field is Java `Long` type, which can exceed 32-bit integer max. Functions correctly but no upper bound validation. |
| PAY-04 | Payload contains optional `flag` field | SHL Payload | Compliant | `ShlPayload.java:24`, `ShlPayloadService.java:28` | Empty flag string normalized to `null` (omitted from JSON) |
| PAY-05 | Payload contains optional `label` field | SHL Payload | Compliant | `ShlPayload.java:26` | Included when provided; max 80 chars enforced at `CreateShlRequest` |
| PAY-06 | Payload contains `v` field set to `1` | SHL Payload | Compliant | `ShlPayload.java:28-29` | Defaults to `1` |
| PAY-07 | Null/absent optional fields are omitted from JSON | SHL Payload | Compliant | `ShlPayload.java:14` | `@JsonInclude(JsonInclude.Include.NON_NULL)` |
| PAY-08 | Payload is base64url-encoded | SHL Encoding | Compliant | `ShlPayloadService.java:34`, `Base64UrlUtil.java:7` | Uses `Base64.getUrlEncoder().withoutPadding()` |
| PAY-09 | SHLink URL uses `#shlink:/` prefix for the fragment | SHL URL | Compliant | `ShlPayloadService.java:35` | Constructs `{baseUrl}{viewerPath}#shlink:/{encodedPayload}` |
| PAY-10 | SHLink URL uses HTTPS scheme | SHL URL | Partial | `ShlPayloadService.java:35`, `AppProperties.java:14` | URL scheme is determined by `APP_BASE_URL` config property. HTTPS not explicitly enforced at code level. |

### 5.2 Manifest API Protocol (MAN)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| MAN-01 | Manifest endpoint accepts POST requests | Manifest API | Compliant | `ShlProtocolController.java:24` | `@PostMapping("/manifest/{manifestId}")` |
| MAN-02 | Request body contains required `recipient` field | Manifest Request | Compliant | `ManifestRequest.java:15-16` | `@NotBlank(message = "recipient is required")` |
| MAN-03 | Request body contains optional `passcode` field | Manifest Request | Compliant | `ManifestRequest.java:18` | Conditionally validated when P flag is set |
| MAN-04 | Request body contains optional `embeddedLengthMax` field | Manifest Request | Compliant | `ManifestRequest.java:20` | Controls inline vs. download delivery |
| MAN-05 | Response contains `status` field (`finalized` or `can-change`) | Manifest Response | Compliant | `ManifestService.java:162`, `ManifestResponse.java:18` | `"can-change"` when L flag present, `"finalized"` otherwise |
| MAN-06 | Response contains `files` array with `contentType`, and `embedded` or `location` | Manifest Response | Compliant | `ManifestService.java:128-151`, `ManifestFileEntry.java:16-18` | Mutually exclusive fields; `@JsonInclude(NON_NULL)` omits the unused one |
| MAN-07 | `embedded` field contains JWE compact serialization when file fits `embeddedLengthMax` | Manifest Response | Compliant | `ManifestService.java:123-132` | Size check at line 127: `jweString.length() <= maxEmbedded` |
| MAN-08 | `location` field provides single-use download URL | Manifest Response | Compliant | `ManifestService.java:134-139`, `ManifestService.java:184-186` | Token consumed atomically via MongoDB `findAndModify` |
| MAN-09 | File download returns JWE with `Content-Type: application/jose` | File Download | Compliant | `ShlProtocolController.java:65-66` | `header(HttpHeaders.CONTENT_TYPE, "application/jose")` |
| MAN-10 | `Retry-After` header included when status is `can-change` | Manifest Response | Compliant | `ShlProtocolController.java:34-35` | `header("Retry-After", "60")` |
| MAN-11 | Expired/inactive SHLs return appropriate error; spec suggests `no-longer-valid` status | Manifest Lifecycle | Partial | `GlobalExceptionHandler.java:26-37` | Returns HTTP 404 with generic "SHL not found" for both expired and inactive. Deliberate privacy choice (indistinguishable from non-existent). |
| MAN-12 | Server returns HTTP 429 when rate limiting | Rate Limiting | Gap | — | No endpoint-level rate limiting implemented. See Section 7. |

### 5.3 Encryption (ENC)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| ENC-01 | JWE algorithm is `dir` (direct key agreement) | SHL Encryption | Compliant | `EncryptionService.java:24` | `JWEAlgorithm.DIR` |
| ENC-02 | JWE encryption method is `A256GCM` | SHL Encryption | Compliant | `EncryptionService.java:25` | `EncryptionMethod.A256GCM` |
| ENC-03 | JWE uses DEFLATE compression (`zip: DEF`) | SHL Encryption | Compliant | `EncryptionService.java:26` | `CompressionAlgorithm.DEF` |
| ENC-04 | JWE `cty` header indicates content type | SHL Encryption | Compliant | `EncryptionService.java:27` | `header.setContentType(contentType)` |
| ENC-05 | Encryption key is 256-bit, generated from cryptographically secure RNG | SHL Key | Compliant | `ShlService.java:51`, `SecureRandomUtil.java:12-16` | `java.security.SecureRandom`, 32 bytes (256 bits) |
| ENC-06 | JWE compact serialization (5-part dot-separated format) | SHL Encryption | Compliant | `EncryptionService.java:32` | `jweObject.serialize()` via nimbus-jose-jwt produces compact form |

### 5.4 Flag Behavior (FLG)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| FLG-01 | `L` flag indicates long-term link; manifest returns `can-change` status | SHL Flags | Compliant | `ManifestService.java:162` | `shl.getFlags().contains("L") ? "can-change" : "finalized"` |
| FLG-02 | `P` flag requires passcode in manifest request | SHL Flags | Compliant | `ManifestService.java:69-70` | Triggers `validatePasscode()` when flag contains `"P"` |
| FLG-03 | `U` flag enables direct file access (GET-based) | SHL Flags | Compliant | `ManifestService.java:204-242`, `ShlProtocolController.java:41-56` | Separate `GET /direct/{manifestId}` endpoint; enforces U flag at line 215 |
| FLG-04 | P+U and L+U combinations are rejected | SHL Flags | Compliant | `ShlService.java:241-247`, `ShlService.java:107-111` | `IllegalArgumentException` thrown for both invalid combinations |

### 5.5 Passcode Protection (PAS)

| ID | Requirement | Spec Reference | Status | Evidence | Notes |
|---|---|---|---|---|---|
| PAS-01 | Passcode is verified server-side (not stored in plaintext) | Passcode | Compliant | `ShlService.java:61-62`, `ManifestService.java:100` | BCrypt hash stored; verified via `passwordEncoder.matches()` |
| PAS-02 | Invalid passcode returns HTTP 401 with `remainingAttempts` | Passcode | Compliant | `GlobalExceptionHandler.java:40-48` | Returns `{"error":"Invalid passcode","remainingAttempts":N}` |
| PAS-03 | Failed attempts are decremented atomically (race-safe) | Passcode | Compliant | `ManifestService.java:86-90` | MongoDB `findAndModify` with `passcodeFailuresRemaining > 0` and `inc(-1)` |
| PAS-04 | SHL is deactivated when attempts are exhausted | Passcode | Compliant | `ManifestService.java:91-97` | Sets `active(false)` and returns `InvalidPasscodeException(0)` |
| PAS-05 | Correct passcode restores the attempt counter | Passcode | Compliant | `ManifestService.java:100-105` | `inc(passcodeFailuresRemaining, 1)` on successful match |

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
| VWR-06 | Handles passcode flow (P flag) and U-flag direct access | Viewer | Compliant | `view.html:121-127,130-137`, `ui/src/pages/ViewerPage.tsx:38-46,88-92` | React UI handles both P and U flags; static `view.html` handles P only |

---

## 6. Detailed Findings by Spec Area

### 6.1 SHLink Payload Structure

**Spec requirement:** The SHLink payload is a JSON object containing `url`, `key`, and optionally `exp`, `flag`, `label`, and `v`, which is base64url-encoded and placed in a URL fragment prefixed with `#shlink:/`.

**Implementation:** The `ShlPayload` model (`ShlPayload.java`) defines all six fields. The `v` field defaults to `1` (line 29). `@JsonInclude(NON_NULL)` at line 14 ensures optional null fields are omitted from the serialized JSON.

URL construction happens in `ShlPayloadService.buildShlinkUrl()` (lines 21-39). The manifest URL is built at line 22, the payload object is populated from the `ShlDocument` at lines 24-30, serialized to JSON at line 33, base64url-encoded (without padding) at line 34 via `Base64UrlUtil`, and the final URL is assembled with the `#shlink:/` prefix at line 35.

**Partial items:**
- **PAY-03 (exp type):** The `exp` field is `Long` in Java, which permits values exceeding the 32-bit integer max (2,147,483,647, i.e., year 2038). The spec implies a standard integer. This is functionally correct for all practical expiration dates.
- **PAY-10 (HTTPS):** The URL scheme comes from `AppProperties.baseUrl` (line 14). HTTPS is expected in production but not enforced programmatically.

### 6.2 Manifest API Protocol

**Spec requirement:** The manifest endpoint accepts POST requests with `recipient` (required), `passcode` (conditional), and `embeddedLengthMax` (optional). It returns a JSON response with `status` and `files`, where each file has a `contentType` and either `embedded` or `location`.

**Implementation:** `ShlProtocolController` maps `POST /api/shl/manifest/{manifestId}` (line 24). The `ManifestRequest` DTO enforces `@NotBlank` on `recipient` (line 15-16). `ManifestService.buildManifest()` (lines 116-169) implements the `embeddedLengthMax` negotiation: if the JWE fits within the limit, it is returned as `embedded` (lines 123-132); otherwise, a single-use download token is created and returned as `location` (lines 134-139).

The response status is determined at line 162: `"can-change"` when L flag is present, `"finalized"` otherwise. The `Retry-After: 60` header is added for `"can-change"` responses at `ShlProtocolController.java:34-35`.

File downloads use atomic token consumption (`ManifestService.java:184-186`): a MongoDB `findAndModify` atomically sets `consumed=true` only if currently `false`, preventing double-use.

**Partial items:**
- **MAN-11 (expired/inactive status):** The spec suggests returning `"no-longer-valid"` as a manifest status for expired or deactivated SHLs. This implementation returns HTTP 404 with a generic "SHL not found" message (`GlobalExceptionHandler.java:26-37`). This is a deliberate privacy-preserving choice that makes expired/deactivated SHLs indistinguishable from non-existent ones.

**Gaps:**
- **MAN-12 (HTTP 429):** No endpoint-level rate limiting is implemented. This should be handled at the infrastructure layer (reverse proxy, CDN, or API gateway).

### 6.3 Encryption (JWE)

**Spec requirement:** Content is encrypted as JWE using direct key agreement (`dir`), AES-256-GCM (`A256GCM`), DEFLATE compression (`DEF`), and a `cty` header indicating the content type. The key is 256 bits from a cryptographically secure source.

**Implementation:** `EncryptionService.encrypt()` (lines 19-34) constructs the JWE header with exactly the specified algorithms at lines 24-27. The `DirectEncrypter` (line 30) produces a JWE compact serialization (line 32) via the nimbus-jose-jwt library, which internally handles nonce generation per NIST guidelines.

Key generation uses `SecureRandomUtil.generateBase64UrlRandom(32)` (`SecureRandomUtil.java:18-20`), which creates 32 random bytes (256 bits) from `java.security.SecureRandom` (line 12-16).

**Verdict:** Fully compliant. All six encryption requirements are met with no caveats.

### 6.4 Flag Behavior

**Spec requirement:** Three flags are defined: `L` (long-term, content may change), `P` (passcode-protected), and `U` (single-use, direct file access). Certain combinations are invalid.

**Implementation:** `ShlFlag.java` defines the three enum values (lines 4-6). The `toFlagString()` method (lines 8-14) concatenates them alphabetically (L, P, U).

Flag validation occurs at creation time in `ShlService.validateFlags()` (lines 241-247), rejecting U+L and U+P combinations with `IllegalArgumentException`. The same validation is duplicated for file uploads at `ShlService.java:107-111`.

At runtime:
- **L flag** → `ManifestService.java:162`: status is `"can-change"`, triggering `Retry-After` header
- **P flag** → `ManifestService.java:69-70`: triggers `validatePasscode()` flow
- **U flag** → `ManifestService.java:204-242`: separate direct access path via `GET /direct/{manifestId}`, enforced at line 215

**Verdict:** Fully compliant.

### 6.5 Passcode Protection

**Spec requirement:** When the P flag is set, the manifest request must include a passcode. The server should protect against brute-force attacks and return 401 with remaining attempt count on failure.

**Implementation:** Passcode hashing uses BCrypt (`ShlService.java:61-62`). The verification flow in `ManifestService.validatePasscode()` (lines 76-113) implements a secure decrement-before-verify pattern:

1. **Atomic decrement** (lines 86-90): MongoDB `findAndModify` decrements `passcodeFailuresRemaining` only if `> 0`, preventing race conditions.
2. **Attempts exhausted** (lines 91-97): If no document is found (counter at zero), the SHL is deactivated.
3. **BCrypt verify** (line 100): `passwordEncoder.matches()` against stored hash.
4. **Restore on success** (lines 102-105): The decremented counter is incremented back on correct passcode.
5. **Report remaining** (lines 107-111): On failure, the remaining count from the decremented document is returned.

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

The React UI handles all flag combinations including U-flag direct access. The static `view.html` handles P-flag but not U-flag direct access.

**Verdict:** Fully compliant via the React UI. The static viewer is supplementary.

---

## 7. Identified Gaps and Recommendations

| # | Issue | Severity | Description | Recommendation |
|---|---|---|---|---|
| 1 | **HTTPS not enforced** | Medium | URL scheme depends on `APP_BASE_URL` config (`AppProperties.java:14`). No programmatic enforcement. | Add startup validation in production profiles to reject non-HTTPS base URLs. |
| 2 | **No HTTP 429 rate limiting** | Low | No endpoint-level rate limiting on manifest or file download endpoints. | Implement at infrastructure layer (reverse proxy, CDN, or API gateway) or add Bucket4j/Resilience4j at application level. |
| 3 | **`no-longer-valid` status not returned** | Low | Expired/deactivated SHLs return 404 instead of manifest `status: "no-longer-valid"`. | Consider returning `"no-longer-valid"` for explicitly deactivated SHLs (via DELETE) while keeping 404 for expired ones. |
| 4 | **No SHLink URL length validation** | Low | Spec suggests keeping the `#shlink:/` URL under 128 characters for QR code compatibility. No validation. | Add an assertion or warning in `ShlPayloadService.buildShlinkUrl()` when URL length exceeds 128 chars. |
| 5 | **`exp` upper bound** | Very Low | `ShlPayload.exp` is Java `Long`, which can exceed 32-bit integer max (year 2038+). | Add validation cap (e.g., max 10 years from now) during SHL creation. |
| 6 | **Static `view.html` missing U-flag** | Low | The standalone HTML viewer does not handle U-flag direct access (React UI does). | Deprecate `view.html` in favor of the React UI, or add U-flag support. |

---

## 8. Implementation Strengths (Beyond Spec)

The implementation exceeds specification requirements in several areas:

| Feature | Description | Evidence |
|---|---|---|
| **Comprehensive Audit Logging** | Every access, passcode attempt, and file download is logged with IP address, user-agent, action type, recipient, and success/failure status. | `AccessLogService` used throughout `ManifestService.java` |
| **QR Code Generation** | Automatic QR code generation with configurable dimensions, returned as base64 data URI. | `QrCodeService`, `AppProperties.java:18` |
| **AWS HealthLake Integration** | Fetches FHIR bundles by clinical category (Allergies, Conditions, Medications, etc.) from AWS HealthLake. | `HealthLakeService`, `ShlService.java:82-86` |
| **Zero-Knowledge Architecture** | Encryption key exists only in URL fragment (never sent to server). Server stores only encrypted JWE payloads. | `ShlPayloadService.java:35` (fragment-based key delivery) |
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

- [HL7 FHIR SMART Health Links IG](https://build.fhir.org/ig/HL7/smart-health-links/)
- [SMART Health Links Documentation](https://docs.smarthealthit.org/smart-health-links/)
- [FHIR R4 Specification](https://hl7.org/fhir/R4/)
