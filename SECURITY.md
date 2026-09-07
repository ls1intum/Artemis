## Security Policy

Artemis is an open-source learning platform developed with a strong focus on security, data protection, and regulatory compliance.  
This document describes the security architecture, supported configurations, and vulnerability handling process.



### Supported Versions

The **latest released version of Artemis** is the supported version. It receives security fixes by
default, and operators are strongly encouraged to stay up to date so that they receive them without
having to ask.

Artemis publishes a minor release every two weeks and bugfix releases whenever they are necessary.
There is currently **no long-term support (LTS) release**. Security fixes are, however, **backported to
older releases on request**, prepared on the `release/X.Y.x` branch of the affected version. Support for
an older release is offered on request and on a best-effort basis; it is not an LTS guarantee and there
is no defined support window.

Requests for a backport or for support on an older release go to `artemis@xcit.tum.de`.

Versioning, release cadence, and upgrade expectations are documented in the
[release and support policy](https://docs.artemis.tum.de/about/releases).



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
  - Implemented using `webauthn4j-spring-security` and `webauthn4j-core`
  - Supports the WebAuthn and FIDO2 specifications through that library, including the attestation
    formats it implements (e.g. packed, TPM, Android SafetyNet, Apple Anonymous)
  - Artemis itself has not undergone FIDO Alliance certification; conformance statements apply to the
    underlying library, not to Artemis as a product

Passkeys provide easy-to-use and phishing-resistant authentication and are particularly suited for privileged access.



### Authorization and Privileged Roles

Artemis uses **course-scoped role-based access control (RBAC)**.

Each course defines the following hierarchical roles:

1. **Student**
2. **Tutor** *(privileged)*
3. **Editor** *(privileged)*
4. **Instructor** *(privileged)*

In addition, Artemis provides two **system-wide roles**, independent of courses:

- **`Admin`** can access all administrative features (e.g. server health checks, user management, course creation)
- **`Super Admin`** has all `Admin` permissions plus exclusive privileges for security-critical
  operations: managing administrator accounts (creating, updating, activating, deactivating, deleting
  `Admin` and `Super Admin` users) and approving passkey registrations for administrators

At TUM, administrator accounts are **strictly separated** from normal user accounts.

The complete permission matrix is documented in
[Access Rights](https://docs.artemis.tum.de/admin/access-rights).

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
- **HTTP/3 (QUIC)** is used when supported by the client and infrastructure
- Transport configuration is an operator responsibility. The TUM reference deployment is configured to
  reach an **A+ rating** on SSL Labs; other deployments depend on their own reverse proxy configuration



### Dependency and Supply Chain Security

The most effective supply chain control is having fewer dependencies to begin with:

- **The number of third-party dependencies is deliberately kept small.** New dependencies are reviewed
  before they are introduced, and existing ones are removed whenever the functionality can reasonably be
  covered without them.
- Unmaintained or low-trust dependencies are avoided.
- Dependency families that must move in lockstep (for example the Apache HttpComponents, Bouncy Castle,
  JGit, and Apache MINA SSHD modules) are guarded by a build check that fails when one member is
  upgraded without its siblings, because such a mismatch produces a runtime `LinkageError` rather than a
  build failure.

On top of that, several independent layers watch the dependencies that do remain:

- **Built-in vulnerability scan.** Artemis queries the OSV vulnerability database for the dependencies of
  the running instance, exposes the result in the Admin area, and emails a weekly summary to the
  configured administrator address (see below). This is the layer that reflects what an operator is
  actually running, rather than what is on `develop`.
- **GitHub Dependabot alerts** for the repository.
- **Mend (formerly WhiteSource)** scanning, configured so that a vulnerable dependency fails its check on
  a pull request.
- **Renovate** proposes dependency updates continuously, which keeps the gap between a published fix and
  its adoption short.
- **CodeQL** static security analysis of server and client code, alongside pull requests and on a
  schedule for the `develop` and `main` branches.
- Regular updates to the latest stable versions of **Spring Boot** and **Angular**, and prompt
  application of security patches.

**Vulnerability Monitoring and SBOM Support:**

Artemis provides built-in tools to help administrators monitor and manage dependency security:

- **Software Dependencies Page:** Administrators can view and check for known vulnerabilities directly in the application via the Admin area, allowing for quick assessment of the current security posture
- **Software Bill of Materials (SBOM):** Release-eligible builds embed a CycloneDX SBOM for the server and the client, downloadable from the Admin area for advanced security analysis in external systems. Builds produced without the SBOM option, including local builds and pull request builds, do not contain one, and the endpoint returns 404 in that case
- **Automated Weekly Security Scan:** A scheduled job queries the OSV vulnerability database for the dependencies of the running instance once per week and emails a summary to the configured administrator address, including an upgrade recommendation when critical or high severity issues are found and a newer Artemis release is available



### Monitoring, Incident Response, and Security Operations

- Security-relevant events (e.g. authentication failures, privilege changes) are logged
- Operators are responsible for monitoring logs and infrastructure-level alerts
- Defined incident response procedures are used to analyze, mitigate, and document security incidents

Incident detection, response, and recovery are treated as continuous operational responsibilities.



### Secure Defaults and Configuration Responsibility

Artemis is shipped with **secure default settings**, and under the `prod` profile it refuses to start
when a security-critical property still holds a value that is published in this repository: the JWT
signing key, the internal admin password, or the build-agent git password. The startup error names the
property and how to supply a value. See
[Security configuration](https://docs.artemis.tum.de/admin/production-setup/security).

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

Artemis is designed with the following standards and regulations in mind. This is a statement of
design intent, not a certification, and it does not by itself establish compliance for any particular
deployment:

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

Please report vulnerabilities in Artemis itself here. Issues in the configuration or operation of a
specific Artemis instance should go to the institution that operates it.

We appreciate responsible disclosure and will work with reporters to resolve issues promptly.



### Disclosure and Advisories

Artemis handles disclosure through
[GitHub Security Advisories](https://github.com/ls1intum/Artemis/security/advisories):

1. A report is triaged privately, and confirmed issues are tracked in a **draft advisory** so that the
   discussion and the fix stay private until a release exists.
2. The fix ships in the next release of the supported version, and is backported to older releases on
   request (see [Supported Versions](#supported-versions)).
3. The advisory is **published** once operators have had a reasonable opportunity to upgrade. It names
   the affected versions, the fixed version, the impact, and any workaround, and it carries a CVE where
   one is warranted.
4. Reporters are credited in the advisory unless they ask not to be.

Publishing advisories rather than fixing silently is deliberate: an operator needs to know whether the
release they skipped mattered, and a project's advisory history is the only way to judge that from the
outside.



### Verifying a Release

Every GitHub release carries, next to `Artemis.war`:

- `SHA256SUMS`, covering the WAR and both SBOMs
- `artemis-server-sbom.cdx.json` and `artemis-client-sbom.cdx.json`, the CycloneDX SBOMs extracted from
  the WAR that ships, so they describe exactly the bytes you downloaded
- a signed [build provenance attestation](https://docs.github.com/en/actions/security-for-github-actions/using-artifact-attestations/using-artifact-attestations-to-establish-provenance-for-builds),
  which binds the artifact to the workflow run and commit that produced it

```bash
sha256sum --check SHA256SUMS
gh attestation verify Artemis.war --repo ls1intum/Artemis
```

Container images pushed to `ghcr.io/ls1intum/artemis` carry the same guarantees. Both artefacts are
keyless: the signing identity is the GitHub Actions workflow that produced the image, proven through an
OIDC token, so there is no private key to store or rotate.

```bash
# GitHub build provenance, pushed to the registry next to the image
gh attestation verify oci://ghcr.io/ls1intum/artemis:<tag> --repo ls1intum/Artemis

# cosign signature, for Kubernetes admission controllers such as Kyverno or policy-controller
cosign verify ghcr.io/ls1intum/artemis@<digest> \
  --certificate-identity-regexp '^https://github.com/ls1intum/Artemis/' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

Images are signed **by digest**, not by tag: a tag is mutable, so a signature bound to one would say
nothing about the image a consumer actually pulled.



### Further Reading

- [Trust and transparency](https://docs.artemis.tum.de/about/trust) is the entry point for security,
  privacy, accessibility, and AI data processing questions
- [Security configuration](https://docs.artemis.tum.de/admin/production-setup/security) for operators
- [Access rights](https://docs.artemis.tum.de/admin/access-rights) for the complete role model
- [Release and support policy](https://docs.artemis.tum.de/about/releases)
