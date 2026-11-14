# LTI 1.3 Advantage Learning Management System Integration

Originality Desk provides native, specification-compliant 1EdTech (formerly IMS Global) **LTI 1.3 Advantage** integration, supporting turnkey deployment inside **Canvas (Instructure)**, **Moodle**, **Blackboard Learn**, and **D2L Brightspace**.

---

## 1. Protocol Architecture & Launch Sequence

```
+-----------------------------------------------------------------------------------+
|                     Learning Management System (Canvas / Moodle)                  |
+-----------------------------------------------------------------------------------+
     |                                                                         ^
     | 1. Third-Party Initiated Login                                          | 2. Auth Request
     |    (iss, login_hint, target_link_uri, lti_message_hint)                 |    Redirect
     v                                                                         |
+-----------------------------------------------------------------------------------+
|                        Originality Desk LTI Service Provider                      |
|                        (com.writingassistant.identity.lti)                       |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                           LtiOidcLaunchHandler                              |  |
|  |  * Validates iss against trusted platform configuration                     |  |
|  |  * Issues state & nonce tokens for session correlation and replay defense   |  |
|  |  * Generates signed authorization URL to LMS oidcAuthUrl                    |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
                                                                               |
                                                                               | 3. HTTP form_post
                                                                               |    (id_token + state)
                                                                               v
+-----------------------------------------------------------------------------------+
|  +-----------------------------------------------------------------------------+  |
|  |                             Token Verification                              |  |
|  |  * Validates RSA signature (SHA256withRSA) against LMS Public Key / JWKS    |  |
|  |  * Validates temporal constraints: exp, iat                                 |  |
|  |  * Validates iss, aud (client_id), deployment_id, and nonce                 |  |
|  +-----------------------------------------------------------------------------+  |
|                                        |                                          |
|                                        v                                          |
|  +---------------------------+  +--------------------------+  +----------------+  |
|  |     LtiMessageClaims      |->|      LtiRoleMapper       |->|  Lti13Service  |  |
|  | * Resource Link Claim     |  | * Instructor -> REVIEWER |  | * Course -> Ws |  |
|  | * Context (Course) Claim  |  | * Learner -> MEMBER      |  | * JIT Session  |  |
|  | * AGS Endpoints           |  | * Admin -> ADMIN         |  |   Provisioning |  |
|  +---------------------------+  +--------------------------+  +----------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 2. Supported LTI 1.3 Advantage Services

### 2.1 Core LTI 1.3 Resource Link (`LtiResourceLinkRequest`)
- Maps individual LMS assignments, syllabus modules, or external tool links directly to Originality Desk document analysis sessions.
- Automatically captures `resource_link.id`, `resource_link.title`, and `resource_link.description`.

### 2.2 Course Context Isolation & Workspace Partitioning
- The launch token's `context.id` (`https://purl.imsglobal.org/spec/lti/claim/context`) is dynamically mapped to an isolated platform workspace (`lms_{context_id}`).
- Ensures that student submissions and similarity indices for Course A are strictly isolated from Course B, maintaining institution-grade multi-tenancy.

### 2.3 Role Vocabulary Normalization (`LtiRoleMapper`)
Maps 1EdTech LIS 2.0 role URNs and friendly names to platform roles:

| LTI / LIS Role URN | Clean Role Token | Platform Role |
|:---|:---|:---|
| `http://purl.imsglobal.org/vocab/lis/v2/system/person#Administrator` | `Administrator` | `INSTITUTION_ADMIN` |
| `http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor` | `Instructor` | `REVIEWER` |
| `http://purl.imsglobal.org/vocab/lis/v2/membership#TeachingAssistant` | `TeachingAssistant` | `REVIEWER` |
| `http://purl.imsglobal.org/vocab/lis/v2/membership/Instructor#Lecturer` | `Lecturer` | `REVIEWER` |
| `http://purl.imsglobal.org/vocab/lis/v2/membership#Learner` | `Learner` | `MEMBER` |
| `http://purl.imsglobal.org/vocab/lis/v2/membership#Student` | `Student` | `MEMBER` |

### 2.4 Assignment & Grade Services (AGS) Read-Ready
- Captures `https://purl.imsglobal.org/spec/lti-ags/claim/endpoint` (`lineitem`, `lineitems`, `scope`).
- Stores the gradebook synchronization endpoints to allow publishing similarity scores and review decisions back to the LMS gradebook.

---

## 3. Security & Anti-Tampering Controls

1. **Replay & Injection Defense**:
   - The tool issues an unpredictable, cryptographically random `nonce` during OIDC initiation and verifies that the returning `id_token` contains an identical `nonce`.
   - Used nonces are immediately invalidated to prevent token replay attacks.
2. **Untrusted Issuer Rejection**:
   - Only pre-configured LMS issuers (`platformIssuer`) are permitted to initiate authentication flows.
3. **Strict Audience & Deployment Isolation**:
   - Launch tokens must target the configured `clientId` and match the institution's `deploymentId`.
4. **Cryptographic Integrity**:
   - Full RSA-SHA256 signature verification supported via standard Java `Signature` using platform public keys.
