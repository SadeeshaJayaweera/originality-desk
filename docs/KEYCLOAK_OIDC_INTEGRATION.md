# Keycloak and OpenID Connect (OIDC) Integration Architecture

## 1. Executive Summary and Enterprise Identity Strategy

Originality Desk integrates enterprise-grade **OpenID Connect (OIDC) Single Sign-On (SSO)**, enabling universities, colleges, and research institutions to federate identity through Keycloak, Okta, Microsoft Entra ID (Azure AD), Google Workspace, or any standard OIDC identity provider.

### Core Architectural Guarantees
- **Dual Authentication Support**: Institutions can maintain local credentials (for standalone system administration and evaluation) while delegating academic faculty and student authentication to institutional SSO.
- **Just-In-Time (JIT) User Provisioning**: New users authenticated via OIDC are automatically provisioned into their designated workspace upon initial login.
- **Dynamic Role Synchronization**: Keycloak realm roles are automatically mapped to platform permission tiers (`INSTITUTION_ADMIN`, `REVIEWER`, `MEMBER`) and updated upon each authentication event.
- **Cryptographic & Temporal Validation**: Tokens are strictly validated for issuer integrity, client audience bounds, and expiration with 60-second clock skew tolerance.

---

## 2. Authentication Protocol Flow

```
User Browser              Originality Desk API                Keycloak IdP
    │                              │                               │
    │ 1. Click "Institutional SSO" │                               │
    ├─────────────────────────────►│                               │
    │                              │ 2. Build Auth URL (state/nonce)
    │◄─────────────────────────────┤                               │
    │ 3. Redirect to Keycloak Auth URL                             │
    ├─────────────────────────────────────────────────────────────►│
    │ 4. Authenticate & Consent                                    │
    │◄─────────────────────────────────────────────────────────────┤
    │ 5. Callback with Authorization Code                          │
    ├─────────────────────────────►│                               │
    │                              │ 6. Exchange Code for ID Token │
    │                              ├──────────────────────────────►│
    │                              │◄──────────────────────────────┤
    │                              │ 7. Validate Claims & Map Role │
    │                              │ 8. Provision User & Session   │
    │ 9. Set HTTP-only Cookie      │                               │
    │◄─────────────────────────────┤                               │
```

---

## 3. Role Mapping Model (`OidcRoleMapper`)

| Identity Provider Claim / Role | Platform Internal Role | Capabilities |
|---|---|---|
| `originality-admin`, `admin`, `realm-admin` | `INSTITUTION_ADMIN` | Full workspace administration, conflict resolution, audit access |
| `originality-reviewer`, `reviewer`, `instructor`, `grader` | `REVIEWER` | Match and document review submission, conflict flagging |
| Any other authenticated role | `MEMBER` | Document upload, analysis submission, report export |

---

## 4. Configuration Reference

```properties
# Enable OIDC Single Sign-On
oidc.enabled=true

# Keycloak Realm Discovery Endpoint
oidc.issuerUri=https://keycloak.institution.edu/realms/academic

# Registered Client Credentials
oidc.clientId=originality-desk
oidc.clientSecret=Secr3tClientCredential123!
oidc.redirectUri=https://originality.institution.edu/api/v1/auth/oidc/callback
oidc.scopes=openid profile email roles

# Role Claim Mappings
oidc.adminRoleName=originality-admin
oidc.reviewerRoleName=originality-reviewer
oidc.defaultWorkspaceId=default
```

---

## 5. Verification and Test Suite

Verified via `OidcIntegrationTest`:
1. `oidcDisabledByDefault`: Confirms OIDC is disabled by default and safely rejects unauthorized callback attempts.
2. `authorizationUrlConstruction`: Tests RFC-compliant URL formatting with client credentials, state, and nonce.
3. `idTokenClaimsValidation`: Tests JWT parsing, issuer verification, audience checking, and expired token rejection.
4. `oidcRoleMapping`: Verifies accurate translation of Keycloak realm roles to internal platform permission tiers.
5. `endToEndUserProvisioningAndSession`: Tests JIT user provisioning and role upgrade synchronization across multiple logins.
