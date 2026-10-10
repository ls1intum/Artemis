# Threat model

Artemis is an interactive learning platform used for teaching, automated assessment and examinations. Operators
self-host it, so the security boundaries below are the ones Artemis itself must enforce. The policy for reporters is in
[`.github/SECURITY.md`](../.github/SECURITY.md); the role model is in `documentation/docs/admin/access-rights.mdx`.

## What this project does and where untrusted input enters

The server is Java 25 with Spring Boot (`src/main/java/de/tum/cit/aet/artemis/`, one package per module). The client is
Angular (`src/main/webapp/app/`). `packages/tum-aet-ui` is the client's component library.

The lowest trust level is an authenticated **Student**. Instances can enable self-registration, so assume anyone can
obtain a Student account. Unauthenticated callers are below that. Assume both are hostile.

Untrusted input enters through:

- **REST API**, `/api/<module>/**`. Endpoints are secured with Artemis enforcement annotations (`@EnforceAtLeastStudent`,
  `@EnforceAtLeastTutor`, `@EnforceAtLeastEditor`, `@EnforceAtLeastInstructor`, `@EnforceAdmin`, `@EnforceSuperAdmin`, or
  the `...InCourse` / `...InExercise` / `...InLecture` / `...InLectureUnit` variants that check the role in the resource's own
  course). `@PreAuthorize` on controllers is banned, and `AuthorizationArchitectureTest` enforces it. `@ManualConfig` marks
  an endpoint that does its own authorization in the method body (or, for a few public files, none at all);
  `@EnforceNothing` marks one that is open on purpose. A few endpoints authorize by another mechanism and carry no
  annotation; they are listed in `UNAUTHENTICATED_ENDPOINT_BASELINE` in that same test. Review all three groups closely. A
  missing check, or a check against the wrong course or exercise, is the typical bug here. Routes that skip
  authentication are the `permitAll` entries in `core/config/SecurityConfiguration.java` (`/api/*/public/**`,
  `/api/*/internal/**` (guarded by `@Internal` instead), `/websocket/**`, `/git/**`, `/management/health`,
  `/management/info`, `/.well-known/*`, `/api-docs*`, `/swagger-ui/**`, the calendar feed, `/login/webauthn` when passkeys
  are on, and the sharing endpoints when the sharing module is on). OIDC and SAML2 login use their own filter chains in
  `account/config/`.
- **WebSocket (STOMP)** at `/websocket`. Every subscription topic is declared with an access rule; clients may only send to
  `/app/...` handlers.
- **Git over HTTP (`/git/**`) and SSH (port 7921)** served by LocalVC. Authentication and repository authorization happen
  in the git servlet and SSH server, not in Spring Security. The pushed content is untrusted.
- **Student code run by LocalCI.** Build agents execute submitted code in Docker containers, read test results from them
  and clone repositories from the core nodes over HTTPS (per-job clone token) or SSH (per-agent key). Core nodes and build
  agents talk through a distributed-data cluster (Hazelcast on port 5701, or Redis), which is expected to sit on a private
  network. Its members are trusted: a node in the cluster can already read any build job's clone token.
- **User content shown to other users:** Markdown, LaTeX, PlantUML, HTML in problem statements, posts, lecture units and
  feedback; uploaded files and images; Apollon diagrams; imported programming exercise archives (ZIP).
- **Authentication:** internal accounts, LDAP, SAML2, OIDC, LTI 1.3 launches, passkeys (WebAuthn), and JWTs in HTTP-only
  cookies.
- **Service callbacks** from Iris/Pyris, Athena, Jenkins (when the Jenkins profile is on) and the sharing platform.
  `/api/*/internal/**` is restricted by `@Internal` to an IP allowlist. Pyris status callbacks also carry a per-job token,
  the Pyris ingestion worker endpoints use the shared `artemis.iris.secret-token`, and Jenkins posts build results to
  `/api/programming/public/programming-exercises/new-result` with an authorization token.

## Components that matter most / least

Most important:

- `core/security`, `core/config/SecurityConfiguration.java`, and authorization in every `*Resource` and service. Look for
  missing or wrong-scope checks (access to another course's data), IDORs, and mass assignment.
- `exam`, `assessment`, `quiz`: leaking questions, solutions or other students' answers and grades; tampering with results
  or with exam timing and integrity rules.
- `localvc` (repository access control, git protocol handling) and `localci` / `buildagent` (the container isolation
  boundary and what a build job can read).
- `account`, `core/security/jwt`, `lti`: authentication, password reset, registration, token handling, account takeover.
- File handling: upload and download paths, ZIP extraction, template and export code (path traversal, zip slip, SSRF).
- Client rendering of user content (stored XSS) and token or cookie exposure.
- `.github/workflows`: several workflows run on `pull_request_target` or `workflow_run`, so script injection through
  attacker-controlled values (PR title, branch name, comment text) or running untrusted pull request code with a write
  token or secrets is in scope.

Less important:

- The client enforces nothing by itself. Client-side checks only shape the UI. Report a client issue only when it is
  exploitable on its own (XSS, open redirect, credential or token leakage), not because a button is visible.
- The AI integrations (`iris`, `athena`, `hyperion`, and the AtlasML and Atlas LLM parts of `atlas`) are optional and
  gated by configuration. They are in scope, but a flaw only matters where the feature is enabled. The rest of `atlas`
  (competencies, learning paths, learner profiles) is not an AI feature and is on by default, so review it like any other
  module.

Out of scope:

- `src/test/**` (including `src/test/playwright`), `documentation/**`, `supporting_scripts/**`, and `config/**` (lint and
  build tooling configuration).
- `src/main/resources/templates/<language>/` (`c`, `java`, `python`, `kotlin`, ...). These are exercise starter code that
  students receive, and they are intentionally simple and sometimes incomplete. The server-side templates next to them
  (`mail`, `localci`, `jenkins`, `iris`, ...) are in scope.
- `deployment/**`: example compose files, Helm charts and Kubernetes manifests.
- Code that exists only for local development (`application-dev.yml`).
- Anything that already requires membership of the Hazelcast or Redis cluster.

## How to exercise it

The container has no Docker and no network, and no database server. Do not try to start the full application. Write
reproducers as tests instead.

- **Server tests:** server integration tests normally run on PostgreSQL through Testcontainers, which cannot work here.
  Use in-process H2:

  ```bash
  ./gradlew test --tests ClientForwardTest -x webapp -Dzonky.test.database.type=H2
  ```

  Gradle works offline because everything it needs is already cached. Testcontainers still logs `Could not find a valid
  Docker environment` while these tests run; the tests pass regardless, so ignore it. Tests extend the base classes in
  `src/test/java/de/tum/cit/aet/artemis/shared/base/` (start with `AbstractSpringIntegrationIndependentTest`) and issue
  requests through `request.performMvcRequest(...)` with `@WithMockUser`. Read an existing `*ResourceIntegrationTest` of the
  affected module first. Architecture rules run fast with `-DincludeTags='ArchitectureTest'`.
- **Client tests:** `pnpm exec vitest run <path/to/spec.ts>`. Do not use `pnpm run vitest:run -- <path>`: it ignores the
  path and runs the whole suite.
- The production WAR is already built at `build/libs/Artemis-<version>.war`, and compiled classes are under `build/classes`.
  `node` and `pnpm` are on the `PATH`, and the client dependencies are installed in `node_modules`.
- Some server tests need Docker or an external service (Weaviate, Redis, a build container) and are expected to fail here.
  Ignore those.

## How you rate severity

- **Critical:** remote code execution on a core node or build agent host, including escaping a build container;
  authentication bypass or forging a valid JWT; SQL injection reachable by a Student; reading or changing data across
  courses at scale without the required role.
- **High:** privilege escalation between roles (Student to Tutor, Editor or Instructor) or between courses (Instructor of
  course A acting in course B); Admin to Super Admin escalation; bypassing passkey enforcement or the Super Admin
  approval of administrator passkeys (see `documentation/docs/admin/production-setup/security.mdx`); reading other
  users' submissions, repositories, exam questions or solutions, or grades without authorization; changing a result or
  grade without authorization; stored XSS that reaches other users; SSRF to internal services; path traversal or zip slip
  that reads or writes outside the intended directory; broken repository authorization in the git or SSH server.
- **Medium:** disclosure of limited personal data; missing authorization on a low-impact action; denial of service by a low
  privileged user through unbounded work or memory; an exploitable client issue that needs unusual user interaction.
- **Low:** anything that needs the Admin role to begin with (Administrators are trusted and can configure the server),
  except the Super Admin boundary and passkey enforcement named under High; hardening gaps without a demonstrated attack.
- For `.github/workflows`: **High** if a pull request from a fork can reach a secret or a write token, **Low** for
  hardening without such a path.
- Rate by impact. A working reproducer (a failing test, request sequence or proof of concept) is welcome but not required:
  an attack path that the code establishes on its own, such as a missing authorization check, keeps the rating its
  impact earns. Cap a finding at medium only while its exploitability or impact is still unproven, and say what is
  unproven.
- Code that students submit is meant to run in the build container, so whatever it does inside its own container is not a
  finding. Reading another job's repositories or secrets, reaching the host, or influencing another student's result is.

## Anything to leave alone

- Default credentials and secrets in dev, local and example deployment configuration. In production Artemis refuses to
  start with the published JWT key, internal admin password or build agent git password.
- TLS, HSTS, firewalling and reverse proxy configuration. These belong to the operator. (Artemis sets its own CSP, frame
  options, referrer policy and permissions policy, so a flaw in those is in scope.)
- Vulnerabilities in a third-party dependency, unless you show that Artemis calls the vulnerable code path. Dependabot,
  Renovate, Mend and the built-in OSV scan already track those.
- Rate limiting, brute force and account enumeration on endpoints that are already limited by `@LimitRequestsPerMinute`.
- Findings that need physical access, host or infrastructure access (an operator, not an Artemis admin login), or write
  access to the server's configuration files.
