## Security Policy

Artemis is an open-source learning platform developed with a strong focus on security, data protection, and regulatory compliance.  
This document describes the security architecture, supported configurations, and vulnerability handling process.



### Supported Versions

Only the **latest released version of Artemis** is supported.

Security patches are **not** provided for older versions.  
Users are strongly encouraged to stay up to date to benefit from security fixes and dependency updates.



### Authentication

Artemis supports multiple authentication mechanisms depending on the deployment configuration:

- **External authentication providers**
  - LDAP (e.g. institutional directories)
  - SAML2-based identity providers  
  → In these cases, **no passwords are stored in Artemis**

- **Internal user management**
  - Accounts are managed by administrators
  - Optional email-based self-registration can be enabled per instance
  - For internal users, **only cryptographic password hashes** are stored (no plaintext passwords)

- **Passkey-based authentication (WebAuthn / FIDO2)**
  - Implemented using `webauthn4j-spring-security`
  - Fully compliant with the WebAuthn and FIDO2 specifications
  - All mandatory FIDO2 conformance tests provided by the FIDO Alliance are passed
  - Supports all major attestation formats (e.g. packed, TPM, Android SafetyNet, Apple Anonymous)

Passkeys provide easy-to-use and phishing-resistant authentication and are particularly suited for privileged access.



### Authorization and Privileged Roles

Artemis uses **course-scoped role-based access control (RBAC)**.

Each course defines the following hierarchical roles:

1. **Student**
2. **Tutor** *(privileged)*
3. **Editor** *(privileged)*
4. **Instructor** *(privileged)*

In addition, Artemis provides a **system-wide `Admin` role**, independent of courses.  
At TUM, administrator accounts are **strictly separated** from normal user accounts.

A **`Super Admin` role** is planned to further limit and control administrative privileges.

Authorization is:
- Evaluated **inside Artemis**, not in the identity provider
- Context-aware (course, role, resource state)
- Time-dependent (e.g. scheduled release of exercises)
- Immediately revocable (e.g. removing tutor rights)

Artemis follows the **principle of least privilege**:  
users only receive the minimum permissions required for their role and context.



### Privileged Access and Strong Authentication

Starting with the **Tutor role**, users are considered *privileged*.

- Artemis supports enforcing **strong authentication (Passkeys / MFA)** for admin users
- This enforcement is **configurable during server startup**
- Enabling mandatory Passkey authentication for privileged actions will be possible for all privileged roles in the future

This approach aligns with modern security recommendations and regulatory requirements such as NIS2.



### Token and Session Management

Artemis uses **stateless JWT-based authentication**:

- No server-side sessions are stored
- Tokens are stored securely in **HTTP-only cookies**
- Token lifetime is configurable by administrators

Typical configuration (e.g. TUM instance):

- **3 days** – standard login
- **10 days** – with “remember me”
- **30 days** – Passkey login  
  - Includes a secure **token rotation mechanism**
  - Automatically extendable up to **180 days**

Longer token lifetimes are only used for strong authentication methods.



### Infrastructure and Transport Security

Artemis follows a defense-in-depth approach:

- Deployed **behind a dedicated firewall**
- Only a reverse proxy is exposed publicly
- Internal services and network traffic are isolated

Transport security:
- Enforced TLS with modern cipher suites
- The TUM deployment achieves the **highest possible score (A+)** on SSL Labs
- **HTTP/3 (QUIC)** is used when supported by the client and infrastructure



### Dependency and Supply Chain Security

Artemis actively protects against supply chain risks:

- Automated dependency vulnerability scanning using **Mend (formerly WhiteSource)**
- Regular updates to the latest stable versions of:
  - **Spring Boot**
  - **Angular**
- Prompt application of security patches
- Avoidance of unmaintained or low-trust dependencies



### Monitoring, Incident Response, and Security Operations

- Security-relevant events (e.g. authentication failures, privilege changes) are logged
- Operators are responsible for monitoring logs and infrastructure-level alerts
- Defined incident response procedures are used to analyze, mitigate, and document security incidents

Incident detection, response, and recovery are treated as continuous operational responsibilities.



### Secure Defaults and Configuration Responsibility

Artemis is shipped with **secure default settings**.

The operating institution is responsible for:
- Network and firewall configuration
- TLS termination and certificate management
- Backup, monitoring, and infrastructure hardening

This clear separation ensures secure operation while allowing flexible deployment.



### Data Protection, Privacy, and Data Minimization

Artemis follows strict **data minimization and data economy principles**:

- Authentication credentials are not stored unless strictly required
- Personal data access is restricted based on role and course context
- Privileged access to personal or assessment-related data is explicitly controlled

Data protection considerations and data minimization are part of the **regular review and development process**.

Compliance with data protection regulations (e.g. GDPR) depends on the specific deployment and is handled by the operating institution.



### Backup and Availability

Artemis is designed to support high availability through its stateless architecture.

- No session state is stored server-side
- This enables horizontal scaling and resilience
- Backup, recovery, and disaster recovery strategies are the responsibility of the operating institution



### Security Audits and Open Source Transparency

- Regular **internal security audits and reviews** are conducted
- An **external security review** is planned
- Artemis is fully **open source**, enabling continuous peer review by the community

Open development and transparency are considered an important security-strengthening factor.



### Standards and Compliance

Artemis aligns with the following standards and regulations:

- **FIDO2 / WebAuthn**  
  https://www.w3.org/TR/webauthn/

- **BSI TR-03107** (substantial assurance level)  
  https://www.bsi.bund.de/SharedDocs/Downloads/EN/BSI/Publications/TechGuidelines/TR03107/TR-03107-1.pdf

- **EU NIS2 Directive (EU) 2022/2555**  
  https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32022L2555

Artemis does not target high-assurance eGovernment or classified data use cases, but follows best practices for systems with substantial security requirements in education.



### Reporting a Vulnerability

Please report potential security vulnerabilities **responsibly**.

📧 **Contact:** `artemis@xcit.tum.de`  
🔐 Encrypted email via **S/MIME** is supported:  
[Download certificate](.github/artemis_xcit.tum.de.pem)

We appreciate responsible disclosure and will work with reporters to resolve issues promptly.
