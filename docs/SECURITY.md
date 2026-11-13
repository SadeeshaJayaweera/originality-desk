# Originality Desk — Security & Authentication Architecture

**Specification Version:** 1.0.0  
**Phase:** 1 (Repository Quality, Architecture and Security Foundation)  
**PR:** 05 (`feature/pr-05-auth-session-security`)  

---

## 1. Authentication & Password Security

### 1.1 Password Hashing Specification
Originality Desk applies strict, industry-standard key derivation for credential storage:
- **Algorithm**: `PBKDF2WithHmacSHA256`
- **Iteration Count**: 210,000 iterations (OWASP recommended minimum for PBKDF2-HMAC-SHA256)
- **Salt**: 16 bytes (128 bits) generated via cryptographically secure pseudo-random number generator (`java.security.SecureRandom`)
- **Key Length**: 256 bits (32 bytes)
- **Serialization Format**: `<iterations>:<base64-salt>:<base64-hash>`

```java
// Password hashing implementation in Security.java
static String password(String password) {
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":"
            + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
}
```

### 1.2 Timing Attack Mitigation
To eliminate user enumeration through response timing analysis:
- When a lookup for a non-existent email occurs, the server executes a dummy PBKDF2 derivation with the identical 210,000 iteration count using `DUMMY = password("invalid-password-for-timing-only")`.
- Comparison of derived hashes is strictly executed via `MessageDigest.isEqual(...)` in constant time.

### 1.3 Memory Hygiene
Password character arrays are cleared immediately in `finally` blocks via `PBEKeySpec.clearPassword()` to prevent plaintext credentials from remaining in JVM heap memory.

---

## 2. Session Management & Storage

### 2.1 Cryptographic Session Tokens
- **Entropy**: 32 bytes (256 bits) from `java.security.SecureRandom`.
- **Encoding**: URL-safe Base64 without padding (`43` characters).
- **Storage Hash**: Session tokens are **never** stored in plaintext in the database. Only the SHA-256 digest (`token_hash`) is persisted.
- **Expiration**: 12-hour sliding window (`SESSION_MILLIS = 12 * 60 * 60 * 1000L`).

### 2.2 Concurrent Session & Capacity Controls
- Maximum 10 active concurrent sessions per user account. Older sessions are automatically purged when a new login occurs.
- Expired sessions are lazily evicted on every new authentication event.
- System-wide session cap prevents database table exhaustion.

---

## 3. Cookie & Transport Security

### 3.1 Session Cookie Attributes
Session identifiers are delivered to the browser in the `desk_session` cookie with strict flags:
- `HttpOnly`: Disallows access by client-side JavaScript, neutralizing Cross-Site Scripting (XSS) token theft.
- `SameSite=Strict`: Completely suppresses cookie transmission on cross-site requests, providing robust protection against Cross-Site Request Forgery (CSRF).
- `Path=/`: Scopes cookie to the application root.
- `Secure`: Enforced in production environments (`DESK_SECURE_COOKIES=true`).

---

## 4. Rate Limiting & Brute-Force Defenses

Originality Desk deploys in-memory fixed-window rate limiters:
1. **Authentication Endpoints** (`/api/v1/auth/login`, `/api/v1/auth/register`):
   - Capped at **15 requests per minute** per client IP address.
2. **Authenticated User API**:
   - Capped at **360 requests per minute** per user ID.
3. **Global Ingress Perimeter**:
   - Capped at **600 requests per minute** per remote IP address.
   - Responds with `HTTP 429 Too Many Requests` and `Retry-After: 60` header upon exhaustion.

