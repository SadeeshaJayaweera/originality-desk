# SAML 2.0 Institutional Authentication & Federation Integration

Originality Desk provides native, zero-external-dependency SAML 2.0 Service Provider (SP) capabilities compliant with international academic federations (eduGAIN, InCommon, UK Federation, DFN-AAI, SWITCHaai) and institutional Identity Providers (Shibboleth IdP, Microsoft Entra ID / ADFS, SimpleSAMLphp, PingFederate, Okta).

---

## 1. Architectural Overview

```
+-----------------------------------------------------------------------------------+
|                        Higher Education Identity Federation                        |
|                     (eduGAIN / InCommon / Shibboleth IdP / Entra)                 |
+-----------------------------------------------------------------------------------+
                                        |
               1. SP Metadata Exchange  |  4. HTTP-POST SAMLResponse
                  & Trust Negotiation   |     (Signed Assertion + Attributes)
                                        v
+-----------------------------------------------------------------------------------+
|                        Originality Desk Service Provider                          |
|                       (com.writingassistant.identity.saml)                        |
|                                                                                   |
|  +---------------------------+  +--------------------------+  +----------------+  |
|  |   SamlMetadataGenerator   |  | SamlAuthnRequestBuilder  |  |  SamlResponse  |  |
|  |     (SPSSODescriptor)     |  |   (Deflate + Base64)     |  |   Validator    |  |
|  +---------------------------+  +--------------------------+  +----------------+  |
|                                                                       |           |
|                                                                       v           |
|  +---------------------------+  +--------------------------+  +----------------+  |
|  |  UserSessionProvisioner   |<-|      SamlRoleMapper      |<-| SamlAssertion  |  |
|  |  (Just-In-Time Sessions)  |  | (eduPersonAffiliation)   |  |     Claims     |  |
|  +---------------------------+  +--------------------------+  +----------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 2. Core Components

### 2.1 Configuration (`SamlConfig`)
The configuration encapsulates:
- `spEntityId`: The globally unique URI identifying Originality Desk in the federation (e.g. `https://originalitydesk.institution.edu/saml/sp`).
- `acsUrl`: The Assertion Consumer Service endpoint handling incoming HTTP POST assertions (e.g. `https://originalitydesk.institution.edu/saml/acs`).
- `idpEntityId`: Identity Provider entity ID (e.g. `https://idp.institution.edu/idp/shibboleth`).
- `idpSsoUrl`: Single Sign-On HTTP-Redirect endpoint.
- `idpCertificatePem`: X.509 IdP signing certificate for digital signature verification.
- `clockSkewSeconds`: Configurable clock drift margin for timestamp validation (default: 180 seconds).
- `defaultWorkspaceId`: Target workspace for automated Just-In-Time (JIT) provisioning.

### 2.2 Metadata Publication (`SamlMetadataGenerator`)
Generates standardized `md:EntityDescriptor` XML:
- Protocol support: `urn:oasis:names:tc:SAML:2.0:protocol`
- Supported NameID formats: `persistent`, `transient`, `emailAddress`
- Binding: `urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST`
- Requested Attributes:
  - `mail` (`urn:oid:0.9.2342.19200300.100.1.3`) — Required
  - `displayName` (`urn:oid:2.16.840.1.113730.3.1.241`) — Optional
  - `eduPersonScopedAffiliation` (`urn:oid:1.3.6.1.4.1.5923.1.1.1.9`) — Required

### 2.3 Request Construction (`SamlAuthnRequestBuilder`)
Constructs cryptographically random `AuthnRequest` documents with:
- Standard `ID` generation prefixed with `_`.
- `IssueInstant` formatted in ISO-8601 UTC.
- `NameIDPolicy AllowCreate="true"`.
- RFC 1951 raw Deflate compression (without zlib wrapping) + Base64 encoding + URL encoding for HTTP-Redirect binding.

### 2.4 Hardened Response Validation (`SamlResponseValidator`)
1. **XXE Protection**: Disallows inline doctype declarations (`disallow-doctype-decl = true`), external general entities, and parameter entities.
2. **Status Verification**: Enforces `urn:oasis:names:tc:SAML:2.0:status:Success`.
3. **Replay & Correlation**: Enforces matching `InResponseTo` against cached authentication request IDs.
4. **Temporal Verification**: Validates `NotBefore` and `NotOnOrAfter` lifetime conditions with configurable clock skew.
5. **Audience Verification**: Enforces exact match against the Service Provider's `spEntityId`.
6. **Digital Signature Verification**: Verifies `ds:Signature` over assertions using standard `javax.xml.crypto.dsig.XMLSignatureFactory` against the IdP certificate.

### 2.5 Higher Education Role Mapping (`SamlRoleMapper`)
Maps `eduPersonScopedAffiliation` and `eduPersonPrimaryAffiliation` attributes into Originality Desk role hierarchies:

| SAML Affiliation Pattern | Platform Role | Capabilities |
|:---|:---|:---|
| `admin*`, `administrator*`, `institution_admin*` | `INSTITUTION_ADMIN` | Tenant administration, workspace management, policy configuration |
| `faculty*`, `instructor*`, `teacher*`, `professor*`, `staff*`, `reviewer*` | `REVIEWER` | Document analysis review, decision adjudication, exemption management |
| `student*`, `learner*`, `member*`, default fallback | `MEMBER` | Document submission, self-review of personal reports |

---

## 3. Production Deployment Guidelines

### 3.1 Federation Registration
1. Download the SP metadata XML via `GET /saml/metadata`.
2. Register the metadata with your federation registrar (e.g., InCommon Federation Manager or eduGAIN metadata aggregate).
3. Download the IdP's signing certificate from your institutional metadata feed and configure `idpCertificatePem`.

### 3.2 Tenant Isolation
All user accounts provisioned via SAML are isolated within their institutional workspace (`workspaceId`). Cross-institution assertion consumption is blocked through strict `AudienceRestriction` checks.
