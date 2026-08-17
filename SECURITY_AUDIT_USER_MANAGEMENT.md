# Security Audit: Artemis Internal User Management

**Scope:** internal (Artemis-database) user management — registration, activation, login, password
reset, password change, session/JWT handling, passkeys, external-identity linking, VCS access
tokens, and the admin user-management APIs.
**Threat model:** account takeover (student → instructor → admin), privilege escalation, and
credential/secret disclosure.
**Branch audited:** `feature/kubernetes-localci` (merge base `cab3f2a557`).
**Method:** manual source review of the full authentication/authorisation path, plus a 12-dimension
parallel code audit. **The planned adversarial-verification pass did not run** (it aborted on an
org spend limit), so nothing here rests on it: every finding below was verified by hand against the
code, and several were additionally confirmed empirically against the live production deployment
(noted inline where that happened). Line numbers refer to the audited commit.

**Production status (`artemis.tum.de`, build 9.8, checked 2026-08-03).** Verified from
`/management/info` and the live proxy: `registrationEnabled: false`, `useExternal: true` with
`ldap` among `activeModuleFeatures`, `repositoryAuthenticationMechanisms: [token, ssh, password]`,
and HSTS is set at the proxy. The operator has since overridden the JWT signing key, the
internal-admin credentials and the build-agent git credentials, so C-1, C-2 and C-7 are remediated
*for that instance* — they remain open for every other deployment until the shipped defaults are
removed from the repository. Findings that do not apply to production are marked inline.

---

## Executive summary

The core password-reset primitives are, in isolation, sound: the reset key is generated from
`SecureRandom` with ~119 bits of entropy, is single-use, is time-limited, and the reset link is built
from the configured `server.url` rather than a request header (so there is no host-header
injection). Passkey ownership checks, the admin/super-admin split, and `@JsonIgnore` on the secret
columns are all correctly implemented.

The problems are not in the primitives — they are in the **layers around** them:

1. **The shipped production configuration contains working secrets.**
   `docker/artemis/config/prod.env` — loaded via `docker/artemis.yml` by
   `docker/artemis-prod-mysql.yml`, which its own header comment describes as *"the default artemis
   production setup also linked to in the docker-compose.yml in the project root"* — ships a real,
   usable JWT signing key and an `artemis_admin` / `artemis_admin` super-admin credential. A third
   default, `buildjob_user` / `buildjob_password`, appears in `prod-multinode.env` and grants read
   access to *every* git repository ahead of any authorization or audit logging. Nothing at startup
   refuses any of them. Each one alone is a complete bypass.
2. **Brute-force protection does not exist in the reference deployment.** Three layers, all
   ineffective: `artemis.rate-limiting.enabled` defaults to `false`, so every
   `@LimitRequestsPerMinute` is a no-op; when enabled, the client IP is read from the *first*
   `X-Forwarded-For` element, which the shipped nginx `$proxy_add_x_forwarded_for` lets the attacker
   control; and the nginx `limit_req` block that *is* unspoofable is bound to `/api/authenticate`, a
   path that no longer exists. There is also no per-account lockout anywhere, and two independent
   password oracles (web login and git-over-HTTP, the latter sending no login notification).
3. **There is no revocation anywhere.** A password reset, password change, admin deactivation, soft
   delete, or role revocation does not invalidate existing JWTs, VCS access tokens, SSH keys, or
   passkeys. `remember-me` tokens live 30 days; passkey tokens 180 days and self-renew. The git
   authentication paths never check `activated` or `deleted` at all, and `PUT users/initialize` lets a
   deactivated user re-activate themselves with their still-valid token.
4. **The account-recovery chain has no notifications and no re-verification.** A user can change
   their e-mail with no confirmation, and neither the e-mail change nor a completed password reset
   nor a password change sends any mail. A short-lived session is therefore promotable to permanent,
   silent account ownership — and passkey enrolment needs no step-up either, so the takeover survives
   the victim's password reset.
5. **Two trust boundaries are wider than they look.** Any authenticated student can turn on DEBUG
   logging platform-wide via `/management/loggers` (which is what makes the otherwise-low logging
   findings dangerous — they supply the missing precondition themselves), and with
   `trustExternalLTISystems` enabled, any registered LTI platform can authenticate as any Artemis
   account, admins included, by asserting an e-mail address.

Counts: **7 critical**, **15 high**, **17 medium**, **12 low**.

---

## Critical

### C-1 — Working JWT signing key committed in the shipped production configuration

| | |
|---|---|
| CWE | CWE-798 Use of Hard-coded Credentials / CWE-321 Hard-coded Cryptographic Key |
| Files | `docker/artemis/config/prod.env:26`, `src/main/resources/config/application-dev.yml:83`, `core/security/jwt/TokenProvider.java:77-91`, `core/config/ConfigurationValidator.java:182-231` |

`docker/artemis/config/prod.env` line 26 ships:

```
JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64SECRET="bXktc2VjcmV0LWtleS13aGljaC1zaG91bGQtYmUtY2hhbmdlZC1pbi1wcm9kdWN0aW9uLWFuZC1iZS1iYXNlNjQtZW5jb2RlZAo="
```

which decodes to the 74-byte string
`my-secret-key-which-should-be-changed-in-production-and-be-base64-encoded\n`. 74 bytes = 592 bits,
which is **above** the 512-bit minimum `Keys.hmacShaKeyFor` enforces for HS512 — so it is accepted
silently, with no warning. The same value is in `application-dev.yml:83`.

`docker/artemis.yml:21` loads this file, and `docker/artemis-prod-mysql.yml` — whose own header
comment reads *"this is the default artemis production setup also linked to in the
docker-compose.yml in the project root"* — extends `artemis.yml`. `prod.env` also sets
`SPRING_PROFILES_ACTIVE="artemis,scheduling,athena,core,prod,docker"`, so this is the real
production path, not an example.

`ConfigurationValidator` validates the internal-admin username/password *lengths* and the Weaviate
config. It never inspects the JWT secret.

**Attack.** Take the key from the public repository. Forge
`{"sub":"<any login>","auth":"ROLE_SUPER_ADMIN,ROLE_ADMIN,ROLE_USER","auth-method":"PASSWORD","is-passkey-super-admin-approved":true,"exp":<far future>}`,
sign with HS512, and send it as `Authorization: Bearer <jwt>` or as the `jwt` cookie. `JWTFilter`
(`core/security/jwt/JWTFilter.java:149-163`) validates the signature and installs the authorities
**straight from the token** — it never loads the user from the database. Full super-admin access to
every course, exam, grade, and repository, on any deployment that did not override the secret. No
credentials, no user interaction, no rate limit.

**Fix.**
1. Remove the value from `prod.env` and `application-dev.yml`; leave the key empty and require it to
   be supplied.
2. Add a hard startup check in `ConfigurationValidator`: refuse to boot under the `prod` profile if
   the decoded secret is blank, shorter than 64 bytes, or equal to any known shipped default
   (compare against a `Set<String>` of the historical defaults, using `MessageDigest.isEqual`).
   Fail closed — `throw`, do not `log.warn`.
3. Generate a per-deployment key at first boot into a mounted secret file if none is configured, and
   log loudly that a new key was created.

### C-2 — Default super-admin credentials `artemis_admin` / `artemis_admin` shipped in the production configuration

| | |
|---|---|
| CWE | CWE-1392 Use of Default Credentials |
| Files | `docker/artemis/config/prod.env:14-15`, `src/main/resources/config/application-artemis.yml:26-29`, `account/service/user/UserService.java:163-202`, `core/config/ConfigurationValidator.java:182-231` |

Both the shipped prod env file and `application-artemis.yml` set:

```
username: artemis_admin
password: artemis_admin
```

`UserService.applicationReady()` (`UserService.java:163-175`) runs `ensureInternalAdminExists` on
**every startup**, which force-sets `activated = true`, re-hashes the configured password, and
re-grants `SUPER_ADMIN_AUTHORITY` (`UserService.java:185-202`). So the credential is not just
created once — it is reasserted on every restart, silently overwriting any stronger password an
operator later set through the UI.

`ConfigurationValidator.validateAdminConfiguration()` only checks lengths
(`username.length() >= 4`, `password.length() >= 8`). `artemis_admin` is 13 characters and passes.

Combined with C-4 (rate limiting disabled by default) and C-5 (no account lockout), this is a
single-request takeover of the whole instance.

**Fix.** Remove the defaults from both files. In `ConfigurationValidator`, reject the boot under
`prod` when username == password, when the password equals a shipped default, or when the password
fails a minimum-strength check. Additionally, only reassert the password in `ensureInternalAdminExists`
when the account is newly created or when an explicit
`artemis.user-management.internal-admin.force-password-reset=true` flag is set — silently resetting a
super-admin password on every restart is itself a hazard.

### C-3 — Rate limiting is fully bypassable via a spoofed `X-Forwarded-For` header

| | |
|---|---|
| CWE | CWE-807 Reliance on Untrusted Inputs in a Security Decision / CWE-348 Use of Less Trusted Source |
| Files | `core/util/HttpRequestUtils.java:23-41`, `admin/service/RateLimitService.java:99-114,79-83`, `docker/nginx/artemis-server.conf:13,64` |

`HttpRequestUtils.getIpStringFromRequest` walks a list of eleven candidate headers, starting with
`X-Forwarded-For`, and returns **the first comma-separated element of the first one present**:

```java
private static final String[] IP_HEADER_CANDIDATES = { "X-Forwarded-For", "Proxy-Client-IP", ... };

for (String header : IP_HEADER_CANDIDATES) {
    String ipList = request.getHeader(header);
    if (ipList != null && !ipList.isEmpty() && !"unknown".equalsIgnoreCase(ipList)) {
        return ipList.split(",")[0];      // <-- attacker-controlled
    }
}
```

The shipped nginx configuration uses `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for`
(`docker/nginx/artemis-server.conf:13` and `:64`). `$proxy_add_x_forwarded_for` **appends** the real
peer address to whatever the client sent. A request carrying `X-Forwarded-For: 203.0.113.9` arrives
at Artemis as `X-Forwarded-For: 203.0.113.9, <real ip>` — and `split(",")[0]` picks the forged value.
Reading the *first* element is exactly backwards; the trustworthy element is the *last* one, the one
the proxy appended.

`RateLimitService.getOrCreatePerMinuteBucket` keys the Bucket4j bucket on that string
(`"type=" + rpmType.name() + "#rpm=" + rpm + "#" + clientId`), so a fresh header value yields a fresh
bucket with a full token allowance.

**Attack.** `for i in $(seq 1 100000); do curl -H "X-Forwarded-For: 10.0.$((i/256)).$((i%256))" -d '{"username":"ab12cde","password":"guess'$i'"}' https://artemis/api/core/public/authenticate; done`
— unlimited login attempts, unlimited password-reset e-mail triggering (mail-bombing any user), and
unlimited registration attempts. The same header also defeats `InternalAspect`'s IP allowlist
(M-9) and forges the exam-session IP audit trail (M-10).

**Fix.** Stop parsing headers by hand. Set `server.forward-headers-strategy: FRAMEWORK` (or
`NATIVE`) and let Spring's `ForwardedHeaderFilter` handle it, with `server.tomcat.remoteip.*`
configured with a `trusted-proxies` regex covering only the reverse proxy. If the manual helper must
stay, change it to: read only `X-Forwarded-For`, take the **last** element, and only when
`request.getRemoteAddr()` is itself in a configured trusted-proxy CIDR set — otherwise use
`getRemoteAddr()` unconditionally. Delete the `Proxy-Client-IP`, `WL-Proxy-Client-IP`, `HTTP_*` and
`REMOTE_ADDR` candidates entirely: those are CGI variable names, never real HTTP headers, and their
only effect is to widen the spoofing surface.

### C-4 — Rate limiting is disabled by default

| | |
|---|---|
| CWE | CWE-1188 Insecure Default Initialization of Resource |
| Files | `core/config/RateLimitingProperties.java:28`, `src/main/resources/config/application-artemis.yml:80-83`, `admin/service/RateLimitService.java:61-67` |

```java
// RateLimitingProperties.java:28
private boolean enabled = false;
```

```yaml
# application-artemis.yml:80-83
rate-limiting:
  enabled: false
  account-management-requests-per-minute: 5
  authentication-requests-per-minute: 30
```

`RateLimitService.enforcePerMinute` returns immediately when
`!configurationService.isRateLimitingEnabled()`. No shipped profile sets `enabled: true`, so in a
default deployment **every** `@LimitRequestsPerMinute` annotation is inert:
`POST /api/core/public/authenticate`, `POST register`,
`POST account/reset-password/init`, `POST account/reset-password/finish`, and the LocalVC git
handshake (`LocalVCServletService.java:263-268`).

There is a **second, independent** kill switch that also defaults to off:
`featureToggleService.isFeatureEnabled(Feature.RateLimit)`. `FeatureToggleService` deliberately
excludes `RateLimit` from the "default everything to true" loop
(`FeatureToggleService.java:96-101`) and only registers it when the property is already on:

```java
if (rateLimitConfigurationService.isRateLimitingEnabled() && !features.containsKey(Feature.RateLimit)) {
    features.put(Feature.RateLimit, true);          // FeatureToggleService.java:136-138
}
```

So both gates must be flipped, and both ship closed. An admin can additionally disable the feature
toggle at runtime over the websocket-backed feature-toggle API.

**Fix.** Default `enabled` to `true` in `RateLimitingProperties` and in
`application-artemis.yml`, and add `RateLimit` to the default-on set in `FeatureToggleService`. Treat
authentication-path throttling as mandatory rather than opt-in — an operator who wants it off can say
so explicitly. Consider making the toggle non-disableable for `AUTHENTICATION` and
`ACCOUNT_MANAGEMENT`.

### C-5 — No account lockout or per-account throttling on password authentication

| | |
|---|---|
| CWE | CWE-307 Improper Restriction of Excessive Authentication Attempts |
| Files | `account/security/ArtemisInternalAuthenticationProvider.java:40-72`, `core/web/open/PublicUserJwtResource.java:87-115`, `localvc/service/LocalVCServletService.java:437-495` |

There is no failed-attempt counter, no exponential backoff, no CAPTCHA, and no `LockedException`
anywhere in the authentication path. The only defence is the IP-keyed bucket, which is off by
default (C-4) and spoofable (C-3).

Worse, there are **two** independent password oracles:

* `POST /api/core/public/authenticate` (web login).
* Git-over-HTTP basic auth. `LocalVCServletService.authenticateUser` falls through to
  `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, passwordOrToken))`
  at line 489-490, i.e. the user's **Artemis password** is a valid git credential. The rate limit
  there is only counted on `/info/refs` (line 263), and — critically — this path never calls
  `ArtemisSuccessfulLoginService.sendLoginEmail`, so a successful password guess produces **no
  notification at all**. (Verified: `sendLoginEmail` is called only from `PublicUserJwtResource`,
  `OIDCAuthenticationSuccessHandler`, `SAML2Service`, and the passkey success handler — never from
  `localvc`.)

Combined with the 8-character minimum and no complexity rule (M-1), credential stuffing against a
university-sized user base is unconstrained.

**Fix.**
1. Add a per-account failed-attempt counter with exponential backoff — a Hazelcast-backed
   Bucket4j bucket keyed on the *lowercased login*, independent of the IP bucket, so distributed
   attacks are also covered. Return the same generic failure regardless of lockout state and do not
   leak "account locked" to the caller.
2. Apply the same account-keyed limit inside `LocalVCServletService.authenticateUser` and in the SSH
   authenticator, not just on `/info/refs`.
3. Send a `NEW_LOGIN` notification for git/SSH password authentication too, and add a
   `FAILED_LOGIN_BURST` notification.
4. Strongly consider disabling password authentication for git entirely and requiring a VCS access
   token — the token already exists, and this removes the oracle.

### C-6 — Session hijack is promotable to permanent account ownership: e-mail change with no re-verification and no notification

| | |
|---|---|
| CWE | CWE-620 Unverified Password Change / CWE-640 Weak Password Recovery Mechanism |
| Files | `account/web/AccountResource.java:88-93`, `account/service/AccountService.java:79-101`, `account/service/user/UserCreationService.java:192-204`, `notification/domain/GlobalNotificationType.java`, `account/service/user/UserService.java:253-262,539-551` |

`PUT /api/account/basic-information` → `AccountService.updateBasicInformationOfCurrentUser` →
`UserCreationService.updateBasicInformationOfCurrentUser` performs
`user.setEmail(email.toLowerCase())` and saves. There is:

* no confirmation mail to the **old** address,
* no verification of the **new** address,
* no re-entry of the current password,
* no application of `registration.allowed-email-pattern` (which *is* enforced on registration,
  `PublicAccountResource.java:121-126`) — so the pattern is trivially bypassed post-registration.

`GlobalNotificationType` is the complete set of security notifications:

```java
public enum GlobalNotificationType {
    NEW_LOGIN, NEW_PASSKEY_ADDED, VCS_TOKEN_EXPIRED, SSH_KEY_EXPIRED, MAINTENANCE, MAVEN_CENTRAL_RATE_LIMIT
}
```

There is **no** notification for a completed password reset (`UserService.completePasswordReset`
sends nothing), for a password change (`UserService.changePassword` sends nothing), or for an e-mail
change.

**Attack chain.** Attacker gets a session for a few minutes — shared/unlocked lab machine, an
unexpired cookie on a borrowed laptop, or an XSS (made materially easier by H-6's
`unsafe-inline`/`unsafe-eval` CSP):

1. `PUT /api/account/basic-information` with `email: attacker@evil.com`. Silent.
2. `POST /api/core/public/account/reset-password/init` with the victim's login. The reset mail now
   goes to the attacker.
3. `POST /api/core/public/account/reset-password/finish` — attacker owns the password.
4. The victim's own sessions keep working (H-1), so nothing looks wrong, and the `NEW_LOGIN` mail
   for the attacker's subsequent logins also goes to `attacker@evil.com`.

The victim receives **zero** e-mails at their real address at any point. Recovery requires an admin.

**Fix.**
1. Make the e-mail change a two-step flow: write the requested address to a
   `pending_email` + `pending_email_key` + `pending_email_date` triple, mail a confirmation link to
   the **new** address, and only commit on confirmation. Simultaneously mail a "your e-mail was
   changed, was this you?" notice with an undo link to the **old** address.
2. Require the current password (or a fresh passkey assertion) to initiate an e-mail change.
3. Apply `allowed-email-pattern` on change, not only on registration.
4. Add `PASSWORD_RESET_COMPLETED`, `PASSWORD_CHANGED`, and `EMAIL_CHANGED` to
   `GlobalNotificationType` and send them unconditionally — these three must not be user-disableable.

### C-7 — Shipped default build-agent git credentials grant read access to every repository, bypassing all authorization

| | |
|---|---|
| CWE | CWE-1392 Use of Default Credentials / CWE-306 Missing Authentication for Critical Function |
| Files | `localvc/service/LocalVCServletService.java:252-259`, `src/main/resources/config/application-localvc.yml:13-14`, `src/main/resources/config/application-buildagent.yml:58-59`, `docker/artemis/config/prod-multinode.env:41-42` |

```java
// LocalVCServletService.java:252-259
if (repositoryAction == RepositoryActionType.READ) {
    UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
    if (Objects.equals(usernameAndPassword.username(), buildAgentGitUsername) && Objects.equals(usernameAndPassword.password(), buildAgentGitPassword)) {
        // Authentication successful
        return;
    }
}
```

This `return` happens **before** `parseRepositoryUri`, before the rate-limit check (line 263-268),
before any participation/ownership authorization, and before any VCS access-log entry. A caller
presenting these credentials can clone **any** repository in the installation — every student's
submission, every exercise's solution and test repository — with no further checks and no audit trail.

The credentials are shipped as defaults in three places:

```yaml
build-agent-git-username: buildjob_user      # application-localvc.yml:13, application-buildagent.yml:58
build-agent-git-password: buildjob_password  # application-localvc.yml:14, application-buildagent.yml:59
```

```
ARTEMIS_VERSIONCONTROL_BUILDAGENTGITUSERNAME='buildjob_user'      # prod-multinode.env:41
ARTEMIS_VERSIONCONTROL_BUILDAGENTGITPASSWORD='buildjob_password'  # prod-multinode.env:42
```

The `prod-multinode.env` occurrence is the serious one — that is a production env file, not an
example. The comments say *"Replace with more secure credentials for production"*, but nothing
enforces it and `ConfigurationValidator` does not look at these properties.

**Attack.** `git clone https://buildjob_user:buildjob_password@artemis/git/<PROJECTKEY>/<project>-solution.git`
— exercise solutions before the deadline, and every student's repository for plagiarism or grade
manipulation reconnaissance. No Artemis account required.

**Fix.** Refuse to boot under `prod` when these equal the shipped defaults (same
`ConfigurationValidator` check as C-1/C-2). Replace the shared static password with a per-build-agent
credential or an mTLS/SSH-key identity. Move the build-agent check *after* the rate limit and, at
minimum, write a VCS access-log entry for build-agent fetches so the access is auditable. Scope the
credential to the repositories a given build job actually needs rather than granting blanket read.

---

## High

### H-1 — No token revocation: password reset, password change, deactivation and deletion leave existing sessions fully valid

| | |
|---|---|
| CWE | CWE-613 Insufficient Session Expiration / CWE-384 Session Fixation (adjacent) |
| Files | `core/security/jwt/JWTFilter.java:149-167`, `core/security/jwt/TokenProvider.java:139-167,176-187`, `account/service/user/UserService.java:253-262,539-551,465-478`, `core/web/open/PublicUserJwtResource.java:164-172`, `src/main/resources/config/application-prod.yml:111-112`, `core/config/SecurityConfiguration.java:82-83` |

`JWTFilter.doFilter` validates the signature and expiry and then installs the principal and
authorities **directly from the token claims**. It never touches the database — no check that the
user still exists, is still `activated`, is not `deleted`, or still holds those authorities. The
tokens carry no `jti` and no version counter, and there is no denylist. The code comments this
explicitly (`JWTFilter.java:70-74`).

Consequences, each confirmed against the code:

| Event | Existing tokens |
|---|---|
| `completePasswordReset` (`UserService.java:253-262`) | remain fully valid |
| `changePassword` (`UserService.java:539-551`) | remain fully valid |
| Admin `deactivateUser` (`UserCreationService.java:262-266`) | remain fully valid — `activated` is never re-read per request |
| `softDeleteUser` (`UserService.java:465-478`) | incidentally broken, not revoked (see below) |
| `removeUserFromCourse` / authority change | old authorities remain effective |
| `POST /logout` (`PublicUserJwtResource.java:164-172`) | clears the cookie only; a copied token still works |

The soft-delete row deserves precision, because it is the one case that looks safe and is not by
design: `anonymizeUser` rewrites `login` to a random string (`UserService.java:490,494`), so the
token's `sub` claim no longer resolves and `userRepository.getUser()` — which calls a plain
`findOneByLogin` with **no `deleted = false` filter** (`UserRepository.java:90,1006-1010`) — throws.
That happens to break most requests, but it is a side effect of the rename, not revocation: any code
path that authorises from the token claims alone, without loading the user, still succeeds. Admin
*deactivation*, which does not rename anything, leaves the session completely intact.

Blast radius: `token-validity-in-seconds-for-remember-me: 2592000` = **30 days**
(`application-prod.yml:112`), and
`artemis.user-management.passkey.token-validity-in-seconds-for-passkey: 15552000` = **180 days**
(`SecurityConfiguration.java:82`), the latter kept alive by `JWTFilter.rotateTokenSilently`
(`JWTFilter.java:81-111`), which re-issues the token with the **old authorities** copied from the
expiring one.

This directly undermines the password reset the user is being asked to trust: "I think I was
compromised, so I reset my password" does **not** evict the attacker for up to 30 days.

**Fix.** Add a `credentialsInvalidatedAt` (or monotonic `tokenVersion`) column to `jhi_user`; stamp
it in `completePasswordReset`, `changePassword`, `deactivateUser`, `softDeleteUser`, and on authority
changes. Embed the value as a claim at issuance and compare in `JWTFilter`, rejecting stale tokens.
The comparison needs the user record, so back it with a small Hazelcast `@Cacheable`
`login → (credentialsInvalidatedAt, activated, deleted)` projection, evicted by the same writers —
follow the `TitleCacheEvictionService` pattern the caching guideline prescribes rather than adding a
Hibernate L2 cache. Independently: shorten `remember-me` well below 30 days, and re-check
`activated`/`deleted` on every request via that same cached projection.

### H-2 — SSO-provisioned accounts are flagged `internal = true`, giving every SAML2/OIDC user a shadow local password and an SSO/MFA bypass

| | |
|---|---|
| CWE | CWE-287 Improper Authentication / CWE-304 Missing Critical Step in Authentication |
| Files | `account/service/user/UserCreationService.java:123-162` (esp. 151-152), `account/security/SAML2Service.java:194-211`, `account/security/OIDCService.java:128-158`, `account/service/user/UserService.java:281-289`, `core/web/open/PublicAccountResource.java:247-271` |

Both SSO paths provision users through `userCreationService.createUser(ManagedUserVM)`, which
unconditionally sets:

```java
user.setActivated(true);
user.setInternal(true);          // UserCreationService.java:151-152
```

`prepareUserForPasswordReset` gates only on `user.getActivated() && user.isInternal()`
(`UserService.java:282`), so **every SAML2- and OIDC-provisioned user is eligible for the Artemis
password-reset flow**, regardless of whether `artemis.user-management.saml2.enable-password` is set.
After completing a reset they have a local bcrypt password, and
`ArtemisInternalAuthenticationProvider` — which filters on `internal = true`
(`ArtemisInternalAuthenticationProvider.java:49,54`) — will authenticate them.

**Attack.** In a deployment where the IdP enforces MFA and controls account lifecycle, an attacker
who can read the victim's mailbox (or who triggered C-6) obtains a local Artemis password and logs
in with username+password. This bypasses IdP MFA, bypasses IdP account suspension, and bypasses
conditional-access policies entirely. The same shadow credential also survives the user being
disabled upstream, because Artemis's `activated` flag is only synced on an actual SSO login.

**Fix.** Introduce an explicit authentication-source field (or reuse `internal` correctly) and set
`internal = false` for SSO-provisioned users. Add a dedicated
`createExternallyManagedUser(...)` path rather than reusing the admin `ManagedUserVM` constructor.
Gate `prepareUserForPasswordReset` on the authentication source, and gate
`ArtemisInternalAuthenticationProvider` so an SSO-sourced account can never authenticate with a
local password unless `saml2.enable-password` is explicitly on.

### H-3 — Every admin- and SSO-created user is given a live 24-hour password-reset key that is never used

| | |
|---|---|
| CWE | CWE-1030 Missing Neutralization / CWE-522 Insufficiently Protected Credentials |
| Files | `account/service/user/UserCreationService.java:142-143`, `account/web/admin/AdminUserResource.java:151-157`, `account/service/user/UserService.java:253-262` |

```java
// UserCreationService.createUser(ManagedUserVM), lines 142-143
user.setResetKey(RandomUtil.generateResetKey());
user.setResetDate(Instant.now());
```

Every user created via `POST /api/account/admin/users`, via SAML2 provisioning, and via OIDC
provisioning gets a fresh reset key valid for 24 hours. But `AdminUserResource.createUser` has the
mail send commented out:

```java
// NOTE: Mail service is NOT active at the moment
// mailService.sendCreationEmail(newUser);
```

So the key is never delivered and never consumed — it is a live credential-equivalent sitting in
`jhi_user.reset_key` in plaintext for every account the platform creates, serving no purpose.

Separately, `completePasswordReset` only clears `reset_key` on **success**. An expired key is
filtered out by `.filter(user -> user.getResetDate().isAfter(...))` but never deleted, so stale keys
accumulate in the table indefinitely.

**Fix.** Do not set a reset key in `createUser(ManagedUserVM)`. If a "set your initial password"
flow is wanted, generate the key at the point the mail is actually sent, and give it a short (1 h)
lifetime. Add a scheduled job that nulls `reset_key`/`reset_date` once past expiry, and store only
`SHA-256(key)` in the column (see M-3).

### H-4 — User enumeration and account-type disclosure on the password-reset endpoint

| | |
|---|---|
| CWE | CWE-204 Observable Response Discrepancy / CWE-203 Observable Discrepancy |
| Files | `core/web/open/PublicAccountResource.java:247-271`, `src/main/webapp/app/account/password-reset/init/password-reset-init.component.ts:73-88` |

`requestPasswordReset` returns four distinguishable outcomes for an unauthenticated caller:

| Input | Response |
|---|---|
| unknown login/e-mail | `200 OK` |
| existing **internal** user | `200 OK` |
| existing **external** user | `400` with `errorKey: "externalUser"` |
| ≥2 internal matches | `400` with `errorKey: "usernameNotUnique"` |

The comment at line 266 (*"Pretend the request has been successful to prevent checking which emails
or usernames really exist"*) is correct for the first case but is defeated by the next two. The
Angular client branches on the key explicitly
(`password-reset-init.component.ts:81-86`), so it is a stable, documented contract.

An attacker can therefore confirm which logins/e-mails exist as external accounts, and — combined
with M-2 — probe for ambiguity. Given C-3/C-4, this is unbounded.

**Fix.** Always return `200 OK`. Move the "this account is managed externally, reset it at
`<link>`" guidance into the **e-mail** sent to the address (which only the real owner reads), not
into the HTTP response. Handle the multi-match case server-side (log it for an admin, e-mail nothing)
rather than surfacing it.

### H-5 — Timing oracle distinguishes existing activated internal accounts at login

| | |
|---|---|
| CWE | CWE-208 Observable Timing Discrepancy |
| Files | `account/security/ArtemisInternalAuthenticationProvider.java:41-72`, `src/main/resources/config/application-artemis.yml:12` |

```java
if (optionalUser.isEmpty()) {
    return null;                                          // fast: 1 SELECT
}
if (!user.getActivated()) {
    throw new UserNotActivatedException(...);              // fast: 1 SELECT
}
if (!passwordService.checkPasswordMatch(...)) {            // slow: bcrypt cost 11 ≈ 100 ms
    throw new AuthenticationServiceException(...);
}
```

bcrypt is executed **only** when an activated internal user exists. With
`bcrypt-salt-rounds: 11`, that is a ~100 ms difference — orders of magnitude above network jitter and
trivially measurable. An attacker enumerates the entire user base by response latency, then feeds
the confirmed list into credential stuffing (C-5).

**Fix.** Always perform a bcrypt comparison. Keep a fixed dummy hash (generated once at startup at
the configured cost) and run `checkPasswordMatch(submittedPassword, DUMMY_HASH)` when the user is
absent, not activated, or a bot — discarding the result. Return the same generic exception in all
three cases.

### H-6 — Content-Security-Policy allows `unsafe-inline` and `unsafe-eval`, with no `default-src`, `object-src`, or `base-uri`

| | |
|---|---|
| CWE | CWE-1021 Improper Restriction of Rendered UI Layers / CWE-79 (impact amplifier) |
| Files | `core/config/SecurityConfiguration.java:231,267-279` |

```java
static final String CSP_POLICY_DIRECTIVES =
    "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://www.youtube.com; worker-src 'self' blob:";
```

The policy sets only `script-src` and `worker-src`. It permits `unsafe-inline` **and**
`unsafe-eval`, and — with no `default-src` — leaves `object-src`, `base-uri`, `form-action`,
`frame-src`, `connect-src`, and `style-src` completely unconstrained. `base-uri` in particular means
a single injected `<base>` tag repoints every relative script URL.

The JWT cookie is `httpOnly` (`JWTCookieService.java:107`), which is good, but the CSP is what stands
between an HTML-injection bug anywhere in the app and the account-takeover chain in C-6 — an XSS
does not need to read the cookie, it only needs to issue the three authenticated requests. HSTS is
also disabled here (`SecurityConfiguration.java:277`) on the assumption that nginx sets it; that
assumption should be asserted, not assumed.

**Fix.** Move Angular to nonce- or hash-based scripts and drop `unsafe-inline`. Remove
`unsafe-eval` (identify the dependency that needs it; it is usually a dev-mode template compiler or
an old charting library). Add `default-src 'self'; object-src 'none'; base-uri 'self'; form-action
'self'; frame-ancestors 'none'`. Add a CSP report endpoint and run `report-only` first to size the
work.

### H-7 — Wrong-password login on an *internal* account returns HTTP 500 with a full stack trace

> **Corrected after measuring production.** An earlier draft of this finding claimed "every mistyped
> password returns 500". That is wrong, and the correction matters for prioritisation. I sent one
> login attempt with an obviously fake username to `artemis.tum.de` and got a clean **401 with an
> empty body** — because production has LDAP enabled (`ldap` appears in `activeModuleFeatures`), and
> Spring's LDAP provider is registered *first* and throws `BadCredentialsException`, which the
> controller does catch. The defect below is real, but it only fires when the **internal** provider is
> the one that fails: internal-only deployments (the default `run-e2e-tests-local-fast.sh` setup and
> most self-hosted instances) for all logins, and LDAP deployments only for `internal = true`
> accounts — on production that is the admin account and any locally created accounts, not the
> LDAP-managed student body. Severity accordingly reduced from High to Medium-High.

| | |
|---|---|
| CWE | CWE-755 Improper Handling of Exceptional Conditions / CWE-209 Generation of Error Message Containing Sensitive Information |
| Files | `account/security/ArtemisInternalAuthenticationProvider.java:63,66,69`, `core/web/open/PublicUserJwtResource.java:99-114`, `core/exception/ExceptionTranslator.java:312-336`, `account/exception/UserNotActivatedException.java` |

`ArtemisInternalAuthenticationProvider` signals every failure with an exception that is **not** a
`BadCredentialsException`:

* wrong password → `AuthenticationServiceException("Invalid password for user " + login)` (line 69)
* not activated → `UserNotActivatedException` (line 66)
* bot account → `AuthenticationServiceException` (line 63)

`PublicUserJwtResource.authenticate` catches only `BadCredentialsException`
(line 111). `ProviderManager` stores a plain `AuthenticationException` in `lastException` and
rethrows it after the provider chain is exhausted, so all three propagate to
`ExceptionTranslator.handleGenericException`, which finds no `@ResponseStatus`, defaults to
`INTERNAL_SERVER_ERROR`, and logs
`log.error("Unhandled exception occurred: Invalid password for user <login>", ex)` **with the full
stack trace** (line 320-321).

I verified the propagation step empirically against this project's `spring-security-core-7.1.0.jar`
by driving a real `ProviderManager` with providers that throw each of these:

```
AuthenticationServiceException (wrong password path) -> propagates as AuthenticationServiceException | isBadCredentials=false
custom AuthenticationException (UserNotActivated path) -> propagates unchanged                       | isBadCredentials=false
provider returns null (unknown user)                  -> propagates as ProviderNotFoundException     | isBadCredentials=false
BadCredentialsException (what the controller catches)  -> propagates as BadCredentialsException       | isBadCredentials=true
```

and confirmed from the `spring-webmvc` bytecode that `ResponseEntityExceptionHandler`'s built-in
handler list covers no `AuthenticationException` type, so `handleGenericException` is the only match.

So wherever the internal provider is the failing one, a mistyped password produces a **500** plus a
stack trace in the error log, and the `401` branch is dead code for that path.
`LdapAuthenticationIntegrationTest` covers the 401 (Spring's LDAP provider does throw
`BadCredentialsException`), but `InternalAuthenticationIntegrationTest` has **no wrong-password test
against the HTTP endpoint** — its `testAuthenticateWithWrongPassword` calls the provider directly and
only asserts the exception message, never a status code. That is why this has gone unnoticed.

Impact: log flooding usable as a cheap DoS on log storage and on the error-alerting pipeline; the
victim's login appears in error logs on every failed attempt; and the anomalous status code
distinguishes internal from LDAP-backed accounts.

**Fix.** Throw `BadCredentialsException` (with a generic message that does **not** include the
login) for the wrong-password and bot cases. Add an `@ExceptionHandler(AuthenticationException.class)`
to `ExceptionTranslator` mapping to `401` with a generic body, and give `UserNotActivatedException`
an explicit `@ResponseStatus(HttpStatus.FORBIDDEN)`. Add
`InternalAuthenticationIntegrationTest` cases asserting `401` for a wrong password and for an
unknown user, and `403` for a deactivated user.

### H-8 — OIDC overwrites an existing user's e-mail from an unverified claim, with no uniqueness check

| | |
|---|---|
| CWE | CWE-345 Insufficient Verification of Data Authenticity / CWE-694 Use of Multiple Resources with Duplicate Identifier |
| Files | `account/security/OIDCService.java:94-119`, `account/repository/UserRepository.java:86,210-221`, `account/security/ArtemisInternalAuthenticationProvider.java:46-55` |

On every OIDC login for an existing user:

```java
String email = oidcUser.getAttribute(emailClaimKey);
if (email != null && !email.isBlank() && !Objects.equals(actualUser.getEmail(), email)) {
    actualUser.setEmail(email);          // OIDCService.java:111-114
    isUpdated = true;
}
```

There is **no `email_verified` check** and **no uniqueness check**. Two problems:

1. If the IdP lets users set their own `email` claim (common for self-service or federated IdPs), an
   attacker sets it to the victim's address, logs in via OIDC, and the claim overwrites *their own*
   record — but two Artemis users now share an e-mail.
2. With duplicate e-mails, `findOneByEmailIgnoreCase` (used by registration and by
   `ArtemisInternalAuthenticationProvider` for e-mail login,
   `findOneWithAuthoritiesByEmailAndInternal`) returns two rows and throws
   `IncorrectResultSizeDataAccessException` → 500. And
   `findAllByEmailOrUsernameIgnoreCase` returns two internal users → the password-reset endpoint
   answers `usernameNotUnique`, permanently locking **both** users out of account recovery
   (see M-2 for the same effect reachable without OIDC).

The DB does not save you: the initial changelog declares a unique index on `login`
(`00000000000000_initial_schema.xml:3332`) but **not** on `email`.

**Fix.** Require `email_verified == true` before accepting the claim (make it configurable but
default-on). Before writing, check `findOneByEmailIgnoreCase` and refuse the update with an audited
error if it belongs to a different user id. Add a unique index on `jhi_user.email` in a new
changelog, after de-duplicating existing rows. Apply the same checks in `SAML2Service.syncUserDataFromSaml2`
(which currently syncs only first/last name — so an IdP e-mail change never propagates, which is the
opposite inconsistency).

### H-9 — `PASSWORD_MAX_LENGTH = 100` exceeds bcrypt's hard 72-**byte** limit, so strong passphrases break password reset with an HTTP 500

| | |
|---|---|
| CWE | CWE-1284 Improper Validation of Specified Quantity in Input / CWE-703 Improper Check for Unusual Conditions |
| Files | `core/config/Constants.java:17`, `account/service/AccountService.java:67-69`, `account/service/user/PasswordService.java:30-32`, `core/exception/ExceptionTranslator.java:312-336`, `src/main/webapp/app/account/password-reset/finish/password-reset-finish.component.ts:56-63` |

`Constants.PASSWORD_MAX_LENGTH = 100` and `AccountService.isPasswordLengthInvalid` accepts anything
in `[8, 100]`. The Angular reset form applies the same bound
(`Validators.maxLength(PASSWORD_MAX_LENGTH)`), so the UI actively invites a 100-character passphrase.

But Spring Security 7.1.0's `BCryptPasswordEncoder` **rejects** anything over 72 bytes. Verified
empirically against the exact jar on this project's classpath
(`spring-security-crypto-7.1.0.jar`):

```
encode(73 chars) THREW: IllegalArgumentException: password cannot be more than 72 bytes
encode(100 chars) THREW: IllegalArgumentException: password cannot be more than 72 bytes
```

`PasswordService.hashPassword` does not catch it, and `ExceptionTranslator` has no handler for
`IllegalArgumentException`, so it lands in `handleGenericException` → **HTTP 500** with
`"An internal server error occurred"` and a full stack trace in the error log.

Affected: `POST /api/core/public/account/reset-password/finish`
(`UserService.completePasswordReset:256`), `POST /api/account/change-password`
(`UserService.changePassword:545`), `POST /api/core/public/register`
(`UserService.registerUser:301`), and both admin user endpoints
(`UserCreationService:140,235`).

On production the blast radius is narrower than it looks, for the same reason as H-7: almost every
account is LDAP-managed and never sets an Artemis password, so this is reachable mainly via
`change-password` and `reset-password/finish` on internal accounts.

The limit is in **bytes**, not characters, which widens this considerably: a German passphrase with
umlauts or any password containing emoji exceeds 72 bytes well before 72 characters (a 40-emoji
password is 160 bytes). So this is not an edge case reachable only by pathological input — it is
reachable by a user following current passphrase advice, and it fails them at exactly the moment they
are locked out and trying to recover. That is why this sits in High rather than as a footnote:
it breaks account recovery, and the error gives the user no idea what to do.

**Fix.** Pre-hash before bcrypt so any length works:
`passwordEncoder.encode(Base64.getEncoder().encodeToString(sha256(rawPassword)))`, applied
consistently in `hashPassword` **and** `checkPasswordMatch` (this is a hash-format change, so it needs
a `DelegatingPasswordEncoder` with a new id and rehash-on-login — see L-6). If that migration is
unwanted, instead lower `PASSWORD_MAX_LENGTH` to 72, validate on **bytes**
(`rawPassword.getBytes(UTF_8).length`) rather than `String.length()`, mirror the byte-based check in
the Angular validator, and return a `PasswordViolatesRequirementsException` (400) with a clear
message. Either way, add a test that sets a 100-character and an emoji-containing password through
the reset flow.

### H-10 — Passkey registration requires no step-up authentication, so a session yields a persistent backdoor that survives a password reset

| | |
|---|---|
| CWE | CWE-306 Missing Authentication for Critical Function / CWE-384 Session Hijacking (persistence) |
| Files | `account/security/passkey/ArtemisWebAuthnRegistrationFilter.java:60-71`, `account/service/user/UserService.java:253-262,465-478`, `src/main/resources/config/application-core.yml:51` |

`ArtemisWebAuthnRegistrationFilter` delegates registration to Spring Security's
`WebAuthnRegistrationFilter` unchanged and only adds a notification mail afterwards. There is **no
re-entry of the current password and no fresh assertion from an existing passkey** — an ordinary
authenticated session is sufficient to enrol a new authenticator.

Two properties make this worse than a normal "attacker has a session" situation:

* A passkey is a **permanent** credential. `completePasswordReset` (`UserService.java:253-262`) does
  not delete passkeys, so the standard remediation the user is told to perform does not remove the
  attacker's authenticator. Nor does `softDeleteUser` — its cleanup list covers participation tokens,
  repository tokens, learner profiles, SSH keys, notification settings and course roles
  (`UserService.java:466-472`), but **not** `PasskeyCredential` rows.
* Passkey-issued tokens live 180 days (`token-validity-in-seconds-for-passkey: 15552000`) and are
  silently rotated by `JWTFilter.rotateTokenSilently`, so the resulting session is effectively
  permanent (see H-1).

There **is** a `NEW_PASSKEY_ADDED` notification (`ArtemisWebAuthnRegistrationFilter.java:64-70`),
which is genuinely good and is why this is High rather than Critical. But it is gated on
`isNotificationEnabled`, so a user who disabled it gets nothing — and in the C-6 chain the attacker
has already repointed the e-mail address, so the notice goes to them.

**Fix.** Require step-up authentication to enrol a passkey: current password, or an assertion from an
already-registered passkey, within a short (5-minute) freshness window — mirror what
`change-password` already does. Delete all passkeys in `completePasswordReset` and in
`softDeleteUser`, and make `NEW_PASSKEY_ADDED` non-disableable (like the three notifications C-6
asks for). Cap the passkey token lifetime well below 180 days.

### H-11 — The nginx login rate limit is bound to a stale path, so the last line of brute-force defence protects nothing

| | |
|---|---|
| CWE | CWE-307 Improper Restriction of Excessive Authentication Attempts |
| Files | `docker/nginx/artemis-server.conf:30-40`, `docker/nginx/artemis-nginx.conf:10`, `core/web/open/PublicUserJwtResource.java:56,87` |

```nginx
# artemis-nginx.conf:10
limit_req_zone $binary_remote_addr zone=loginlimit:10m rate=30r/m;

# artemis-server.conf:30-39
location /api/authenticate {
    proxy_pass http://artemis/api/authenticate;
    limit_req zone=loginlimit burst=3 delay=2;
}
```

The actual login endpoint is **`/api/core/public/authenticate`** —
`PublicUserJwtResource` is `@RequestMapping("api/core/public/")` with `@PostMapping("authenticate")`.
`/api/authenticate` no longer exists. nginx `location` matching is prefix-based on the request URI, so
login requests never enter this block; they fall through to the generic `location /` which has no
`limit_req` at all.

This matters because it is the *only* throttle that is **not** bypassable: the zone keys on
`$binary_remote_addr`, the real TCP peer, so C-3's `X-Forwarded-For` trick does not touch it. It was
the one control that would have covered for C-3 and C-4 — and it is aimed at a dead URL.

**Confirmed on the live production proxy.** `proxy.production.artemis.cit.tum.de:/etc/nginx/sites-available/proxy.conf:85-94`
carries the identical `location /api/authenticate` block, and the file is Ansible-managed ("Do not
make changes here - they will be overwritten"), so the fix has to land in the Ansible role, not on the
host. The `loginlimit` zone there is also keyed on `$binary_remote_addr`, so it would be
unspoofable — it simply never matches. Production consequently has **no login rate limiting at all**,
at either layer, and no throttle whatsoever on the password-reset endpoints.

Net effect in the reference deployment and in production: **login has no rate limiting whatsoever.**
Application-level is off by default (C-4) and spoofable when on (C-3); proxy-level is misrouted. Same
for `POST account/reset-password/init` and `finish`, which have no nginx block at all.

(The neighbouring `location /login/webauthn` block *is* correct — that is the real passkey path — which
is what makes the login block look maintained when it is not.)

**Fix.** Change the location to `/api/core/public/authenticate` (and keep `/api/authenticate` only if
the legacy alias still resolves). Add `limit_req` blocks for
`/api/core/public/account/reset-password/init`, `/api/core/public/account/reset-password/finish`, and
`/api/core/public/register`. Add a smoke test that asserts a 429 arrives after N rapid POSTs to the
real login URL, so this cannot silently rot again — that is the actual defect here, a control with no
test asserting it fires.

### H-12 — Any authenticated user can change server log levels and read thread dumps via the actuator

| | |
|---|---|
| CWE | CWE-732 Incorrect Permission Assignment for Critical Resource / CWE-215 Insertion of Sensitive Information Into Debugging Code |
| Files | `src/main/resources/config/application.yml:179-190`, `core/config/SecurityConfiguration.java:292,304,330` |

`application.yml` exposes `configprops`, `env`, `logfile`, `loggers`, `threaddump` (plus health, info,
metrics, prometheus). `SecurityConfiguration` permits only
`/management/info` and `/management/health*` anonymously (line 292) and IP-gates
`/management/prometheus/**` (line 304). Everything else falls through to
`.requestMatchers("/**").authenticated()` (line 330) — i.e. **any logged-in student**.

So a student can:

* `POST /management/loggers/de.tum.cit.aet.artemis` with `{"configuredLevel":"DEBUG"}` — turn on DEBUG
  logging for the entire application, cluster-wide.
* `GET /management/threaddump` — full thread stacks of the running server.
* `GET /management/env`, `GET /management/configprops` — the complete property-key inventory and
  property-source layout.

Two consequences. The direct one is denial of service: DEBUG on the whole application under exam load
floods the log pipeline and the disk. The serious one is that it **weaponises L-1 and L-2**, which are
otherwise low because production logs at `INFO` — the attacker supplies the missing precondition
themselves. Once DEBUG is on, the logs receive live password-reset keys
(`UserService.java:254`), activation keys (`UserService.java:227`), and complete request header sets
including other users' `Cookie` and `Authorization` values (`JWTFilter.java:204-205`). If the operator
has set `logging.file.name` — which `docker/artemis/config/playwright.env:44` does, so it is a
configuration the project itself uses — then `GET /management/logfile` is readable by that same
student and the chain completes to arbitrary account takeover.

Mitigating, and why this is High rather than Critical as shipped: `management.endpoint.env.show-values`
is unset, so Spring Boot's default of `NEVER` masks all property *values* in `env`/`configprops` — the
JWT secret is not directly readable there. And `logging.file.name` is not set in the shipped prod
config, so `/management/logfile` returns 404 by default.

**Fix.** Restrict `/management/**` to `hasAuthority(ROLE_ADMIN)` with the existing anonymous
exceptions for `info`/`health`/`health/readiness`/`health/liveness` and the IP-gated `prometheus`. Drop
`env`, `configprops`, `logfile` and `threaddump` from the exposure list unless there is a concrete
operational need, and if `loggers` is kept, make it admin-only and audit every level change. Also set
`management.endpoint.env.show-values: NEVER` explicitly rather than relying on the framework default.

### H-13 — `PUT users/initialize` lets any token holder re-activate a deactivated account, defeating admin deactivation

| | |
|---|---|
| CWE | CWE-863 Incorrect Authorization |
| Files | `account/web/UserResource.java:124-138`, `account/service/user/UserCreationService.java:286-292,262-266`, `core/security/jwt/JWTFilter.java:149-167` |

```java
@PutMapping("users/initialize")
@EnforceAtLeastStudent
public ResponseEntity<UserInitializationDTO> initializeUser() {
    User user = userRepository.findOneWithAuthoritiesByLogin(SecurityUtils.getCurrentUserLogin().orElseThrow()).orElseThrow();
    if (user.getActivated()) {
        return ResponseEntity.ok().body(new UserInitializationDTO(null));
    }
    if ((ltiApi.isPresent() && !ltiApi.get().isLtiCreatedUser(user)) || !user.isInternal()) {
        user.setActivated(true);              // <-- self-service reactivation
        userRepository.save(user);
        return ResponseEntity.ok().body(new UserInitializationDTO(null));
    }
    String result = userCreationService.setRandomPasswordAndReturn(user);   // also sets activated = true
    return ResponseEntity.ok().body(new UserInitializationDTO(result));
}
```

The endpoint exists to bootstrap LTI-provisioned users, but the guard is inverted for the general
case: for any **external** user (`!user.isInternal()`) — and for any user at all when the LTI module is
present and the user is not LTI-created — it simply sets `activated = true` on the caller's own
account. The other branch reaches `setRandomPasswordAndReturn`, which also sets `activated = true`
(`UserCreationService.java:289`).

Chained with H-1, this defeats administrative deactivation entirely:

1. Admin calls `PATCH users/{id}/deactivate`. `activated` becomes `false`, but the user's existing JWT
   remains valid — `JWTFilter` never re-reads `activated` (H-1).
2. The user calls `PUT /api/account/users/initialize` with that still-valid token.
3. `activated` is back to `true`. They log in normally again.

For a SAML2/OIDC deployment this is the whole point of deactivation — cutting off a user who has left
the institution or is under investigation — and it is reversible by the user with one request and no
notification to anyone.

**Fix.** Do not set `activated` on an account that an administrator deactivated. Distinguish
"never activated" from "deactivated" — e.g. a separate `deactivatedByAdminAt` timestamp, or only
permit initialisation when `activationKey != null` (which is the genuine never-activated state, since
`activateUser` nulls it). Scope the endpoint to LTI-created internal users only, which is what the
Javadoc says it is for, and return 403 otherwise. Fixing H-1 is the structural remedy: deactivation
must invalidate tokens.

### H-14 — VCS access tokens and SSH keys bypass account deactivation and soft delete entirely

| | |
|---|---|
| CWE | CWE-613 Insufficient Session Expiration / CWE-284 Improper Access Control |
| Files | `localvc/service/LocalVCServletService.java:437-495`, `localvc/service/GitPublickeyAuthenticatorService.java`, `account/service/user/UserService.java:465-478,486-518` |

Grepping both git authentication entry points for `getActivated`, `isDeleted`, `activated` and `isBot`
returns **nothing**. The token branches in `authenticateUser`
(`LocalVCServletService.java:471-485`) return the `User` on a token match and never consult account
state; only the password fall-through at line 489-490 goes via `authenticationManager`, which *does*
check `activated`.

Combined with the fact that `softDeleteUser` clears participation and repository tokens but **not**
`user.vcsAccessToken` (`UserService.java:466-472`, and `anonymizeUser` at 486-518 does not touch it
either):

| State | Web login | Git via VCS token | Git via SSH key |
|---|---|---|---|
| `deactivateUser` | blocked | **works** | **works** |
| `softDeleteUser` | blocked | **works** (token retained) | blocked (keys deleted at line 470) |

So an administrator who deactivates a user — the standard response to a departure, a compromise, or an
academic-integrity investigation — leaves that user full read/write access to their repositories for
up to a year (the maximum token expiry, `AccountResource.java:128-130`). During an exam this is also an
integrity hole: a deactivated or excluded student can still push.

**Fix.** Add an explicit account-state check at the top of `authenticateUser` and in the SSH
authenticator: reject when `!activated`, `deleted`, or `isBot`, for every credential type, before any
token comparison. Clear `user.vcsAccessToken` in `deactivateUser` and `softDeleteUser`, and delete SSH
keys and passkeys in both. This is the same underlying gap as H-1 and M-11 and should be fixed as one
"revoke everything" service method invoked from every lifecycle transition.

### H-15 — LTI `trustExternalLTISystems` authenticates as any Artemis account, including admins, from an unverified e-mail claim

| | |
|---|---|
| CWE | CWE-290 Authentication Bypass by Spoofing / CWE-345 Insufficient Verification of Data Authenticity |
| Files | `lti/service/LtiService.java:88-131` (esp. 109-118), `lti/service/LtiService.java:47-48` |

```java
// LtiService.java:109-118
if (artemisAuthenticationProvider.getUsernameForEmail(email).isPresent() || userRepository.findOneByEmailIgnoreCase(email).isPresent()) {
    if (trustExternalLTISystems) {
        User user = userRepository.findOneWithAuthoritiesByEmail(email).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), user.getGrantedAuthorities()));
        return;
    }
    throw new LtiEmailAlreadyInUseException();
}
```

When `artemis.lti.trustExternalLTISystems` is enabled, an LTI launch whose `email` claim names **any**
existing Artemis account authenticates as that account, inheriting `user.getGrantedAuthorities()` —
including `ROLE_ADMIN` and `ROLE_SUPER_ADMIN`. There is no check that the launching platform is
authoritative for that e-mail's domain, no check that the account is internal or external, no
`activated` check on this branch, no `deleted` check, and no `ArtemisSuccessfulLoginService`
notification (LTI is absent from the `sendLoginEmail` call sites).

The trust boundary is therefore: *any* registered LTI platform can assert any Artemis user's e-mail
address and become them. A single misconfigured or compromised Moodle/Canvas instance — or an
instructor who can add a platform registration — yields super-admin on Artemis.

`trustExternalLTISystems` defaults to `false` (`LtiService.java:47`), which is why this is High rather
than Critical as shipped. **On any deployment that enables it, treat this as Critical.** The flag
exists because institutions do turn it on to avoid the `LtiEmailAlreadyInUseException` friction, so
"it's off by default" is weak comfort.

**Fix.** Never derive authentication from an e-mail claim alone. Require the LTI subject (`sub`) to be
pre-linked to the Artemis account, and make first-time linking an explicit, audited, user-confirmed
step. If the trust mode must remain, at minimum: restrict it to a per-platform allowlist of e-mail
domains that platform owns, refuse to authenticate any account holding `ROLE_ADMIN` /
`ROLE_SUPER_ADMIN` (or any elevated authority) through it, apply the `activated`/`deleted` checks, and
send a login notification. Also validate `deployment_id` against a registered deployment and bind the
launch to the course's configured platform.

---

## Medium

### M-1 — No password complexity requirement and no breached-password check

| | |
|---|---|
| CWE | CWE-521 Weak Password Requirements |
| Files | `core/config/Constants.java:15,17`, `account/service/AccountService.java:67-69`, `src/main/resources/config/application-artemis.yml:12` |

The entire server-side policy is `8 <= length <= 100` (`AccountService.isPasswordLengthInvalid`). No
character-class requirement, no dictionary or breach check (there is no HaveIBeenPwned / k-anonymity
lookup anywhere in the codebase), and no check that the password differs from the login or e-mail.
`password` and `12345678` are both accepted. The client renders a
`PasswordStrengthBarComponent`, but that is advisory only — the server does not enforce it.

`bcrypt-salt-rounds: 11` (`application-artemis.yml:12`) is reasonable, so the hashing itself is not
the weakness; the weakness is that the plaintext space being hashed is unconstrained at the low end.
Combined with C-5 (no lockout) and C-4 (no rate limiting), an 8-character lower-bound with no
blocklist is the difference between credential stuffing succeeding and failing.

**Fix.** Raise the minimum to 12 and add a zxcvbn-style strength check enforced server-side. Add a
k-anonymity breached-password lookup against the HIBP range API, fail-open on timeout, applied on
registration, change-password, and reset-finish. Reject passwords equal or near-equal to the login,
e-mail local part, first name, or last name.

### M-2 — A login may contain `@` and `.`, letting an attacker permanently break another user's password reset

| | |
|---|---|
| CWE | CWE-20 Improper Input Validation / CWE-1289 Improper Validation of Unsafe Equivalence |
| Files | `core/config/Constants.java:24`, `account/repository/UserRepository.java:210-221`, `core/web/open/PublicAccountResource.java:250-260` |

```java
public static final String LOGIN_REGEX = "^[_'.@A-Za-z0-9-]*$";   // Constants.java:24
```

`@` and `.` are permitted, so a login can be a full e-mail address. The reset lookup matches on
either field:

```sql
WHERE user.deleted = FALSE
  AND (LOWER(user.email) = LOWER(:searchInput) OR LOWER(user.login) = LOWER(:searchInput))
```

**Attack.** Register (or get an admin to import) an account with `login = "victim@tum.de"` and
`email = "attacker@evil.com"`. Registration's duplicate checks pass — `findOneByLogin("victim@tum.de")`
finds nothing and `findOneByEmailIgnoreCase("attacker@evil.com")` finds nothing. Now
`reset-password/init` for `victim@tum.de` matches two internal users and returns
`usernameNotUnique` forever: **the victim can never self-service a password reset again.** The same
response also confirms the victim exists.

**Fix.** Tighten `LOGIN_REGEX` to disallow `@` (and ideally `'`), and add a startup/migration check
for existing logins containing `@`. Independently, make the reset lookup unambiguous: try an exact
e-mail match first, and only fall back to a login match if no e-mail matched — and require that a
login match not look like an e-mail address.

### M-3 — Reset and activation keys are stored in plaintext

| | |
|---|---|
| CWE | CWE-522 Insufficiently Protected Credentials / CWE-256 Plaintext Storage of a Password |
| Files | `account/domain/User.java:125-131`, `src/main/resources/config/liquibase/changelog/00000000000000_initial_schema.xml:1068-1069`, `account/service/user/UserService.java:253-262,281-289` |

`reset_key` and `activation_key` are `varchar(20)` columns holding the key verbatim. `@JsonIgnore`
correctly prevents API serialisation, but anyone with read access to the database, a database
backup, a replica, a `mysqldump`, or a SQL-injection primitive elsewhere in the application gets
**directly usable account-takeover tokens** for every account with a pending reset — including, per
H-3, every account the platform has ever created via the admin API or SSO.

The reset key itself is strong: `RandomStringUtils.random(20, 0, 0, true, true, null, SECURE_RANDOM)`
over 62 alphanumerics ≈ 119 bits (`RandomUtil.java:26-28`), seeded from `SecureRandom`. On MySQL's
default case-insensitive collation the effective space drops to 36²⁰ ≈ 103 bits — still far beyond
brute force, so this is a note, not a finding.

**Fix.** Store `SHA-256(key)` (hex) and compare the hash — a single-use, high-entropy token does not
need a slow KDF, and SHA-256 keeps the lookup indexable. Look up by hash and compare with
`MessageDigest.isEqual`. Widen the column to `varchar(64)` in a new changelog and treat existing
plaintext keys as invalid on migration. Do the same for `activation_key`.

### M-4 — Reset key valid for 24 hours, not invalidated on failed attempts, and carried in a URL query string that lands in access logs

| | |
|---|---|
| CWE | CWE-640 Weak Password Recovery Mechanism / CWE-598 Use of GET Request Method With Sensitive Query Strings |
| Files | `account/service/user/UserService.java:253-262`, `src/main/resources/templates/mail/passwordResetEmail.html:16`, `core/security/filter/SpaWebFilter.java`, `src/main/webapp/app/account/password-reset/finish/password-reset-finish.component.ts:70-77` |

```java
return userRepository.findOneByResetKey(key)
        .filter(user -> user.getResetDate().isAfter(Instant.now().minusSeconds(86400)))
```

* **24 hours** is far longer than the ~1 hour OWASP recommends for a reset token.
* The key is consumed only on success. Failed submissions neither invalidate it nor increment a
  counter, so the token tolerates unlimited guessing (harmless at 119 bits, but it also means a
  leaked-and-noticed key cannot be burned).
* The link is `.../account/reset/finish?key=<key>`
  (`passwordResetEmail.html:16`). That is an SPA route, so the browser issues a real GET to the
  server, `SpaWebFilter` forwards it to `index.html`, and **the reset key is written verbatim to the
  nginx and Tomcat access logs**. Anyone with log access — ops, a log-shipping pipeline, an
  aggregation SaaS — can harvest live reset keys.
  `password-reset-finish.component.ts` never strips the key from the URL afterwards, so it also
  persists in browser history and in any session-replay tooling.
  (Positive: `Referrer-Policy: strict-origin-when-cross-origin` is set at
  `SecurityConfiguration.java:275`, and the mail template loads its favicon from `${baseUrl}`, so
  there is no third-party referer leak.)
* `.filter(user -> user.getResetDate().isAfter(...))` NPEs if `reset_key` is non-null while
  `reset_date` is null. No current writer produces that state, but the column is nullable and
  nothing enforces the pairing — add a DB check constraint or a null guard.

**Fix.** Cut validity to 1 hour (configurable). Add a `reset_attempts` counter and invalidate the
key after ~5 failures. Change the link to `.../account/reset/finish` and deliver the key in the URL
**fragment** (`#key=...`), which is never sent to the server and so never logged — the Angular route
can read it from `location.hash`; then `history.replaceState` it away after reading. Make
`completePasswordReset` an atomic conditional update
(`UPDATE jhi_user SET password_hash=?, reset_key=NULL, reset_date=NULL WHERE reset_key=? AND reset_date>?`)
and treat a zero row count as failure, so the token is provably single-use under concurrency.

### M-5 — Re-registration over a pending account destroys it: SSO lockout, role and matriculation-number loss, then hard deletion

| | |
|---|---|
| CWE | CWE-863 Incorrect Authorization / CWE-284 Improper Access Control |
| Files | `account/service/user/UserService.java:298-353,363-385`, `account/service/user/UserCreationService.java:70-114`, `account/service/UserScheduleService.java:56-94`, `src/main/resources/config/application-artemis.yml:30-33` |

`handleRegisterUserWithSameLoginAsExistingUser` overwrites an existing **non-activated** user with
attacker-supplied data when the login and e-mail both match:

```java
if (existingUser.getEmail().equals(newUser.getEmail())) {
    newUser.setId(existingUser.getId());
    User updatedExistingUser = userRepository.save(newUser);   // UserService.java:374-375
    instanceMessageSendService.sendRemoveNonActivatedUserSchedule(updatedExistingUser.getId());
```

`newUser` is a **freshly constructed** `User` with only the fields `registerUser` sets
(lines 300-321). Because the id is then assigned and passed to `save()`, JPA merges the detached
instance and overwrites *every* column — so this is not a takeover, it is a **destructive overwrite**:

* `internal` → `true`, `password` → the attacker's hash. An LDAP/SAML2-backed victim can no longer
  authenticate: `LdapAuthenticationProvider` skips internal users, and the internal provider now
  compares their real password against the attacker's hash.
* `activated` → `false`, `registrationNumber` → `null` (never set on `newUser`), `authorities` → just
  `STUDENT`. Any TA/instructor grant and the matriculation-number linkage used for exam and grade
  attribution are gone.
* `sendRemoveNonActivatedUserSchedule` arms `UserScheduleService`, whose
  `removeNonActivatedUser` performs a **hard** `userRepository.delete(user)`
  (`UserScheduleService.java:89-105`) after `cleanup-time-minutes: 60`. For a pending account with no
  dependent rows — exactly the state LDAP auto-provisioning leaves users in — the delete succeeds and
  the record is gone. For a victim with participations, FK constraints make it throw, and because the
  task runs in a bare `scheduler.schedule(() -> ...)` lambda (line 63-67) the exception is swallowed
  into the `Future` and never surfaces — leaving the victim silently locked out indefinitely.
* The activation mail goes to `newUser.getEmail()`, which by construction is the **victim's** address,
  so the attacker cannot activate and gains no access. This is denial of service and data
  destruction, not takeover.

The preconditions are guessable in a university deployment: the login follows
`^([a-z]{2}\d{2}[a-z]{3})$` (`application-artemis.yml:25`) and the e-mail is typically
`first.last@tum.de`. `UserCreationService.createUser(login, ...)` — the LDAP path — creates users with
`activated = false` (line 95), which is the vulnerable state, and an instructor adding students to a
course by CSV before they ever log in produces exactly those rows.

There is a further consequence I have not executed but which follows from Hibernate's merge
semantics and is worth confirming with a test: `User` declares `orphanRemoval = true` on eight
associations, including `examUsers`, `completedLectureUnits`, `competencyProgresses`,
`tutorialGroupRegistrations`, `savedPosts` and (with `cascade = ALL`) `learnerProfile`
(`User.java:187-241`). `newUser` carries freshly constructed empty `HashSet`s for all of them, so
merging it over the victim's id should orphan-remove those rows — i.e. destroy exam registrations,
lecture-unit completions and competency progress, not just credentials. Treat this as the likely
blast radius pending a test that exercises it.

Mitigating, and the reason this is Medium rather than High: `registration.enabled` defaults to
`false` (`application-artemis.yml:31`), and the admin path `createUser(ManagedUserVM)` sets
`activated = true` (line 151). **Not applicable to production**, where `/management/info` reports
`registrationEnabled: false`. On any instance with self-registration enabled this is High.

**Fix.** Do not overwrite. If the login already exists and is non-activated, re-send the activation
mail to the **stored** address and return `200` without touching the row — the legitimate owner gets
their link, the attacker learns nothing and changes nothing. Never merge an attacker-constructed
entity over an existing id; if a field-level update is ever genuinely needed, set the specific fields
on the loaded managed entity instead. Separately, make `removeNonActivatedUser` log its failures
rather than swallowing them, and never schedule deletion for a row that was not created by that same
registration request.

### M-6 — Any instructor can harvest the e-mail address of every user on the platform

| | |
|---|---|
| CWE | CWE-359 Exposure of Private Personal Information |
| Files | `account/web/UserResource.java:96-117`, `core/dto/UserDTO.java:113-149` |

`GET /api/account/users/search?loginOrName=<3+ chars>` is `@EnforceAtLeastInstructor` and returns
`UserDTO`s built by `new UserDTO(user)`, which populates `email`, `login`, `firstName`, `lastName`,
`internal`, and `authorities` (`UserDTO.java:115-132`). The search is **platform-wide**, not scoped
to the caller's courses. The endpoint nulls out `langKey`, `createdBy`, `createdDate`,
`lastModifiedBy`, `lastModifiedDate` and `selectedLLMUsage` — but not `email` or `authorities`.

25 results per query with a 3-character minimum means a few thousand queries enumerate an entire
institution's e-mail directory, plus which accounts hold `ROLE_ADMIN`. There is no rate limit on this
endpoint.

(Positive: `visibleRegistrationNumber` is a `@Transient` opt-in field
(`User.java:99-100,367-377`) that this path never populates, so matriculation numbers are **not**
leaked here.)

**Fix.** Scope the search to users who share a course with the caller, or gate the cross-course
variant behind `@EnforceAdmin`. Drop `email` and `authorities` from the response — the registration
UI needs login and name to disambiguate, not the e-mail. Add
`@LimitRequestsPerMinute` and audit-log bulk searches.

### M-7 — CSRF protection disabled; the only defence is `SameSite=Lax`

| | |
|---|---|
| CWE | CWE-352 Cross-Site Request Forgery |
| Files | `core/config/SecurityConfiguration.java:259`, `core/security/jwt/JWTCookieService.java:101-113` |

```java
.csrf(CsrfConfigurer::disable)
```

with cookie-borne authentication (`httpOnly(true).sameSite("Lax").secure(isSecure).path("/")`).
`SameSite=Lax` does block cross-site `POST`/`PUT`/`DELETE`, so there is no *classic* CSRF here — that
is why this is Medium and not High. But the entire protection rests on one cookie attribute, with
no defence in depth:

* `Lax` still permits **top-level GET navigation** with the cookie attached. Any state-changing
  `GET` is CSRF-able. `GET /api/core/public/activate?key=...` is one (low impact — it needs a valid
  key).
* `Lax` is a *same-site*, not same-origin, control. Any content served from a sibling host under the
  registrable domain (a course-hosted page, a student-controlled subdomain, a legacy service) is
  "same-site" and can forge authenticated state-changing requests — including the C-6 chain.
* `cookieSecure` can be explicitly forced to `false` in production
  (`JWTCookieService.java:104-105`: an explicit `artemis.security.authentication.jwt.cookie-secure: false`
  overrides the `!dev` default).

**Fix.** Re-enable CSRF with `CookieCsrfTokenRepository.withHttpOnlyFalse()` and the
`XorCsrfTokenRequestAttributeHandler`, and have the Angular `HttpClient` echo the token — Angular
supports this out of the box. Move the cookie to `SameSite=Strict` where navigation flows permit, or
adopt the `__Host-` prefix. Audit for state-changing `GET`s and convert them to `POST`. Refuse to
boot under `prod` if `cookie-secure` is explicitly `false`.

### M-8 — Pre-hijacking: an attacker can pre-create the account an SSO identity will later bind to

| | |
|---|---|
| CWE | CWE-287 Improper Authentication (federated identity pre-hijacking) |
| Files | `account/security/SAML2Service.java:134-155`, `account/security/OIDCService.java:79-94` |

Both SSO paths resolve the incoming identity to a local account **by login only**:

```java
final String username = substituteAttributes(properties.getUsernamePattern(), principal);
Optional<User> user = userRepository.findOneWithAuthoritiesByLogin(username);   // SAML2Service.java:134-135
```

```java
String username = oidcUser.getAttribute(usernameClaimKey);
Optional<User> localUser = userRepository.findOneWithAuthoritiesByLogin(username);  // OIDCService.java:79-81
```

Neither checks whether the matched account is internal, who created it, or whether it was ever
verified against the IdP. On a deployment with self-registration enabled, an attacker who can guess
a future login (TUM identifiers follow `^([a-z]{2}\d{2}[a-z]{3})$` —
`application-artemis.yml:25` — a ~4.5-million space that is routinely predictable from a name)
registers that login with their own e-mail and password. When the real owner later signs in via SSO,
they are silently bound to the attacker's pre-created record — and the attacker retains a working
local password (H-2) and knows the recovery e-mail.

**Fix.** Record the authentication source and the IdP subject identifier (`sub` / NameID) on the
user at provisioning time, and match on **that** rather than on the login. When an SSO login resolves
to an account with no recorded IdP subject, require an explicit, audited linking step (confirm via
the IdP-asserted e-mail) instead of binding implicitly.

### M-9 — `@Internal` endpoint IP allowlist defaults to `0.0.0.0/0` and is header-spoofable

| | |
|---|---|
| CWE | CWE-1188 Insecure Default / CWE-807 Reliance on Untrusted Inputs |
| Files | `src/main/resources/config/application.yml:171-176`, `core/security/annotations/InternalAspect.java:49-64`, `core/config/SecurityConfiguration.java:297` |

```yaml
# application.yml:171-176  — "Currently set to allow all IPs."
security:
    internal:
        allowed-cidrs:
            - ::/0
            - 0.0.0.0/0
```

`SecurityConfiguration.java:297` marks `/api/*/internal/**` as `permitAll()`, so `InternalAspect` is
the only network control — and with the shipped default it allows the entire internet. Even when an
operator narrows the CIDRs, `InternalAspect.checkAccess` resolves the client IP via the same
spoofable `getIpStringFromRequest` (line 57), so `X-Forwarded-For: 10.0.0.1` defeats it.

Mitigating (hence Medium, and adjacent to this audit's core scope): the endpoints behind it carry a
second factor. `PyrisInternalStatusUpdateResource` calls
`pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, ...)` and `AthenaInternalResource`
requires an `Authorization` header. The IP allowlist is defence in depth — but it is currently a
no-op.

**Fix.** Default `allowed-cidrs` to loopback plus the private ranges, or to empty (the aspect already
fails closed on an empty list — `InternalAspect.java:37-43`). Resolve the IP from
`request.getRemoteAddr()` behind a trusted-proxy check, per C-3.

### M-10 — Exam-session IP addresses are recorded from a spoofable header

| | |
|---|---|
| CWE | CWE-807 Reliance on Untrusted Inputs in a Security Decision |
| Files | `exam/web/StudentExamResource.java:792`, `core/util/HttpRequestUtils.java:49-53` |

```java
final var ipAddress = !storeSessionDataInStudentExamSession ? null
        : HttpRequestUtils.getIpAddressFromRequest(request).orElse(null);
```

The IP stored on the `ExamSession` — used to detect account sharing and multi-device access during
exams — is taken from the attacker-controlled `X-Forwarded-For`. A student can make every exam
session appear to originate from any address they choose, defeating the integrity signal, or
fabricate evidence implicating another address.

**Fix.** Per C-3, resolve from `getRemoteAddr()` behind a trusted-proxy check. Additionally record
the raw `X-Forwarded-For` chain separately as untrusted metadata so investigators can see both.

### M-11 — VCS access token is a long-lived alternative account credential, stored in plaintext and not revoked on password reset

| | |
|---|---|
| CWE | CWE-522 Insufficiently Protected Credentials / CWE-613 Insufficient Session Expiration |
| Files | `account/web/AccountResource.java:123-155`, `core/web/open/PublicAccountResource.java:211-220`, `account/service/user/UserService.java:253-262,465-478`, `src/main/resources/config/liquibase/changelog/00000000000000_initial_schema.xml:1080` |

`jhi_user.vcs_access_token` is a plaintext `varchar(50)`. `GET /api/core/public/account` deliberately
returns it to the owner (`PublicAccountResource.java:213-215`) so the client can build a
token-embedded clone URL. `PUT /api/account/user-vcs-access-token` lets any student mint one with an
expiry up to **one year out** (`AccountResource.java:128-130`), unthrottled.

Neither `completePasswordReset` nor `changePassword` clears it. `softDeleteUser`
(`UserService.java:465-478`) deletes participation and repository tokens but **never nulls
`user.vcsAccessToken`** — so a soft-deleted user's token survives anonymisation. Passkeys are
likewise absent from that cleanup list.

Result: a compromise that yields the token (a `.git/config` on a shared machine, a CI log, a
database read) grants repository access for up to a year, and the documented remediation — "reset
your password" — does not revoke it.

**Fix.** Store `SHA-256(token)` with a short public prefix for lookup, and show the token exactly
once at creation. Clear `vcsAccessToken` (and delete passkeys, participation tokens, repository
tokens, SSH keys) in `completePasswordReset`, `changePassword`, `deactivateUser`, and
`softDeleteUser`. Cap the requested expiry well below a year and add `@LimitRequestsPerMinute` to the
creation endpoint. Replace the `Objects.equals`/`String.equals` token comparisons
(`LocalVCServletService.java:423,428,472`) with `MessageDigest.isEqual`.

### M-12 — `anonymizeUser` writes a plaintext value into the password-hash column

| | |
|---|---|
| CWE | CWE-257 Storing Passwords in a Recoverable Format |
| Files | `account/service/user/UserService.java:486-518` (esp. 488, 495) |

```java
final String randomPassword = RandomUtil.generatePassword();
...
user.setPassword(randomPassword);          // UserService.java:495 — NOT hashed
```

Every other writer passes through `passwordService.hashPassword`. Here a 20-character plaintext goes
straight into `password_hash`. Currently fail-safe — `BCryptPasswordEncoder.matches` logs
*"Encoded password does not look like BCrypt"* and returns `false`, so the value can never
authenticate, and the account is also `activated = false`. But it is a latent trap: any future change
to the encoder (e.g. moving to `DelegatingPasswordEncoder`, which treats a prefix-less value
differently) could turn a non-credential into a working one, and the value pollutes the column that
security tooling scans for plaintext secrets.

**Fix.** `user.setPassword(passwordService.hashPassword(RandomUtil.generatePassword()))` — or better,
set it to `null` and make `ArtemisInternalAuthenticationProvider` reject a null/blank hash explicitly
rather than relying on bcrypt's parse failure. Add the passkey deletion, `vcsAccessToken` clearing,
and a `credentialsInvalidatedAt` bump (H-1) to `softDeleteUser` while you are here.

### M-13 — The passkey super-admin approval flag is read from an independently-resolved credential, not from the one whose signature was verified

| | |
|---|---|
| CWE | CWE-565 Reliance on Cookies/Request Data without Validation / CWE-345 Insufficient Verification of Data Authenticity |
| Files | `account/security/passkey/ArtemisWebAuthnAuthenticationProvider.java:70-103,117-121`, `core/security/jwt/TokenProvider.java:148-151` |

`ArtemisWebAuthnAuthenticationProvider.authenticate` resolves the credential **twice, independently**:

```java
String credentialId = webAuthnRequest.getWebAuthnRequest().getPublicKey().getId();      // line 74 — client-supplied `id` string
PasskeyCredential credential = this.passkeyCredentialsRepository.findByCredentialId(credentialId).orElseThrow(...);   // line 76

PublicKeyCredentialUserEntity userEntity = this.relyingPartyOperations.authenticate(webAuthnRequest.getWebAuthnRequest());  // line 81 — the actual crypto verification
...
Map<String, Object> details = createAuthenticationDetailsWithPasskeyApprovalStatus(credential);   // line 90 — flag from the FIRST lookup
```

The privilege-bearing bit — `credential.isSuperAdminApproved()`, which becomes the
`is-passkey-super-admin-approved` JWT claim (`TokenProvider.java:148-151`) — is taken from the entity
found via the request's `id` field, while the signature is verified by
`relyingPartyOperations.authenticate`, which resolves the credential from `rawId`. Nothing in this
method asserts that the two resolutions produced the same credential.

To be precise about what this is and is not: this is **not** a remote forgery by an arbitrary attacker,
because `relyingPartyOperations.authenticate` still demands a valid signature, so a private key is
required. The realistic exposure is a **user with two passkeys, one approved and one not** — present a
valid assertion from the unapproved authenticator while setting the JSON `id` to the approved
credential's id, and the resulting token carries `is-passkey-super-admin-approved: true`. Whether that
is reachable depends on whether Spring Security's deserialisation enforces
`id == base64url(rawId)`; I did not confirm that it does not, so the exploitability is unproven.

Either way the pattern is wrong: an authorization attribute must be read from the credential that was
cryptographically verified, never from a parallel lookup on unverified request data. The correct
version is not more expensive, so there is no reason to keep the current one.

**Fix.** Derive the credential from the verified result — resolve by the verified `rawId`/user entity
returned by `relyingPartyOperations.authenticate`, then read `isSuperAdminApproved()` from that row.
Add an assertion that the pre-lookup and post-verification credential ids match, and fail closed on
mismatch. Add a test that presents a mismatched `id`/`rawId` pair and asserts the login is rejected.

### M-14 — The canonical `/api/admin/**` prefix is not matched by the URL-level admin rule

| | |
|---|---|
| CWE | CWE-1220 Insufficient Granularity of Access Control |
| Files | `core/config/SecurityConfiguration.java:294`, `admin/web/*.java` |

```java
.requestMatchers("/api/*/admin/**").hasAuthority(Role.ADMIN.getAuthority())   // line 294
```

A single `*` in an Ant pattern matches exactly one path segment, so this covers
`/api/account/admin/**`, `/api/core/admin/**`, `/api/exam/admin/**` and so on — but **not**
`/api/admin/**`, which is the canonical prefix the admin module has migrated to.
`AdminLogResource`, `AdminSbomResource`, `AdminFeatureToggleResource`, `AdminStatisticsResource`,
`AdminAuditResource`, `AdminOrganizationResource`, `AdminDataExportResource`, `AdminCourseResource`,
`AdminCleanupResource`, `AdminScheduleResource`, `AdminBuildJobQueueResource`,
`AdminMetricsResource` and `AdminWebsocketResource` are all mapped under `"api/admin/"` and therefore
fall through to `.requestMatchers("/**").authenticated()`.

I verified every one of those controllers: **all thirteen carry a class-level `@EnforceAdmin`**, so
there is no exploitable hole today. That is why this is Medium and not Critical. The finding is that
the two-layer design has silently become one layer for the entire admin module, and the remaining layer
is a hand-written annotation on each class. A new admin controller that omits `@EnforceAdmin` would be
exposed to every authenticated student with nothing to catch it — and the account module has no
`ResourceArchitectureTest`, so the guardrail that would assert annotation presence does not run here
(see L-12).

**Fix.** Change the matcher to cover both shapes: `.requestMatchers("/api/admin/**", "/api/*/admin/**")`.
Add an architecture test asserting that every `@RestController` whose mapping contains an `admin/`
segment carries `@EnforceAdmin` or `@EnforceSuperAdmin`.

### M-15 — No HSTS header is emitted anywhere: Spring disables it because "nginx does it", and nginx does not

| | |
|---|---|
| CWE | CWE-319 Cleartext Transmission of Sensitive Information |
| Files | `core/config/SecurityConfiguration.java:276-277`, `docker/nginx/artemis-server.conf`, `docker/nginx/nginx.conf` |

```java
// Disables HTTP Strict Transport Security as it is managed at the reverse proxy level (typically nginx).
.httpStrictTransportSecurity((HeadersConfigurer.HstsConfig::disable))
```

Grepping the shipped nginx configuration under `docker/nginx/` for `Strict-Transport-Security`
returns nothing. So the assumption in the comment is false for the *shipped* reference deployment,
and no HSTS header is sent at all there.

**Does not apply to production.** The live proxy config sets
`add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload";` — verified
directly on `proxy.production.artemis.cit.tum.de`. This is therefore a defect in the Docker reference
deployment that other operators copy, not in the TUM instance.

Consequence for this audit's scope: a user who reaches `http://artemis…` — a typed URL, an old
bookmark, a link in a course PDF — is exposed to SSL stripping on that first request. The JWT cookie
is `secure` so it is not transmitted, but the *login form* is, so credentials can be captured, and a
password-reset link clicked from an e-mail client that rewrites to `http` leaks the reset key in the
clear.

**Fix.** Either add `add_header Strict-Transport-Security "max-age=63072000; includeSubDomains" always;`
to the nginx server block, or stop disabling it in Spring and let the application emit it. Pick one
owner and assert it in a test — the failure mode here is precisely a control each layer believes the
other provides.

### M-16 — WebAuthn user verification is only `PREFERRED`, so a passkey is accepted as a single factor

| | |
|---|---|
| CWE | CWE-308 Use of Single-factor Authentication |
| Files | `account/dto/passkey/PublicKeyCredentialCreationOptionsDTO.java:86-89`, `account/dto/passkey/ArtemisAuthenticatorSelectionCriteriaDTO.java:41`, `src/main/webapp/app/account/user/settings/passkey-settings/webauthn.service.ts:357` |

`publicKeyCredentialCreationOptionsToDTO` passes through whatever
`options.getAuthenticatorSelection().getUserVerification()` returns, and nothing in Artemis overrides
Spring Security's default of `PREFERRED`. The client specs corroborate the value that actually reaches
the browser (`userVerification: 'preferred'` throughout
`webauthn.service.spec.ts` and `credential-option.util.spec.ts`). `residentKey` defaults to
`DISCOURAGED` (`ArtemisAuthenticatorSelectionCriteriaDTO.java:41`).

`PREFERRED` means the authenticator *may* skip PIN/biometric and the assertion is still accepted.
Artemis does not inspect the `uv` flag in the authenticator data afterwards. So a passkey degrades to
pure possession: an unlocked or borrowed laptop with a platform authenticator, or a hardware key left
in a USB port, logs in with no user interaction beyond a tap. Given that a passkey login yields a
180-day self-renewing session (H-1) and that passkeys survive password resets (H-10), this is a weak
first factor with a very long tail.

**Fix.** Set `userVerification = REQUIRED` for both registration and authentication, and verify the
`uv` bit in the authenticator data server-side rather than trusting the client's request options.
Consider `residentKey = REQUIRED` for a genuine usernameless flow. If `PREFERRED` must stay for
compatibility with some authenticator, then require `uv` at minimum for accounts holding elevated
authorities and for the `require-for-administrator-features` path.

### M-17 — SSH public keys are registered without proof of possession and squat a global fingerprint namespace

| | |
|---|---|
| CWE | CWE-345 Insufficient Verification of Data Authenticity / CWE-770 Allocation Without Limits or Throttling |
| Files | `localvc/service/sshuserkeys/UserSshPublicKeyService.java:46-60` |

```java
public void createSshKeyForUser(User user, AuthorizedKeyEntry keyEntry, UserSshPublicKeyDTO sshPublicKey) ... {
    PublicKey publicKey = keyEntry.resolvePublicKey(null, null, null);
    String keyHash = HashUtils.getSha512Fingerprint(publicKey);
    ...
        throw new BadRequestAlertException("Key already exists", "SSH key", "keyAlreadyExists", true);
```

Two problems. First, there is **no proof of possession**: the user submits a public key and Artemis
binds it to their account without ever challenging them to sign a nonce with the matching private key.
Second, the uniqueness check on the SHA-512 fingerprint is **global**, not per-user.

SSH public keys are, by design, public — GitHub publishes every user's at
`https://github.com/<user>.keys`. So an attacker can enumerate a cohort's public keys and register
them all on their own account. Each victim is then permanently unable to add their own key
("Key already exists") and cannot use SSH with Artemis at all. There is no self-service way for the
victim to discover why, and no way for them to reclaim it without an admin.

This is denial of service, not takeover: the attacker does not hold the private keys, so they cannot
authenticate with the squatted keys either. (The inverse — the victim connecting and being
authenticated as the attacker — would require the lookup to resolve to the attacker's row, which it
does, but that hands the victim the attacker's identity, not the reverse, so it is a correctness bug
rather than an attack.)

**Fix.** Require proof of possession: issue a nonce, have the client sign it with the private key, and
verify before persisting. That closes the squat completely, because the attacker cannot sign for a key
they do not hold. Additionally scope the uniqueness constraint per user, and return a distinct error
when the collision is with another account so an admin can be alerted.

---

## Low

### L-1 — Reset and activation keys logged at DEBUG
`UserService.java:254` (`log.debug("Reset user password for reset key {}", key)`) and
`UserService.java:227` (`log.debug("Activating user for activation key {}", key)`) log live
credentials. Production level is `INFO` for `de.tum.cit.aet.artemis`
(`application.yml:22`, `logback-spring.xml:32`), so they are not emitted by default — but Artemis
exposes the `loggers` management endpoint (`application.yml:170-183`) and a `LogResource`, so an
admin can raise the level at runtime and begin harvesting. **Fix:** log a truncated fingerprint
(`key.substring(0,4) + "…"`) or the user id instead of the key.

### L-2 — `JWTFilter` logs every request header, including `Cookie` and `Authorization`, on an invalid token
`JWTFilter.java:204-217`: `compactHeaders(request)` concatenates every header into the log line.
DEBUG-gated, same caveat as L-1. `collectHeaders` (line 225-229) is unused dead code. **Fix:** log
only `source`, `request_uri`, and a token fingerprint; delete `collectHeaders`.

### L-3 — `User.toString()` includes `activationKey`
`User.java:509-511`, reached from `log.debug("Save user {}", user)`
(`UserService.java:271`, `UserCreationService.java:275`) and from `log.info("Changed Information for
User: {}", user)` (`UserCreationService.java:202`). In practice the key is `null` by the time the
`INFO` statements run, so exposure is minimal. **Fix:** drop `activationKey` from `toString()`.

### L-4 — Activation keys never expire and are reused across purposes
`UserService.activateRegistration` (line 226-232) applies no expiry check; only the scheduled
non-activated-user cleanup bounds the window (`registration.cleanup-time-minutes: 60`). The same
20-character generator serves activation keys, reset keys, generated passwords, and anonymised
logins (`RandomUtil.java:26-40`). **Fix:** add an explicit `activation_date` + expiry check; use
distinct, purpose-labelled generators.

### L-5 — `RandomStringUtils.random(...)` is deprecated in commons-lang3
The static `RandomStringUtils.random` overloads are deprecated in favour of
`RandomStringUtils.secure()`. Behaviour is currently correct (an explicit `SecureRandom` is passed),
but the deprecation will eventually force a change on a security-critical path.
`RandomUtil.java:19-21`'s static `SECURE_RANDOM.nextBytes(new byte[64])` is also a no-op
force-seed carried over from the JHipster original. **Fix:** migrate to
`RandomStringUtils.secure().next(20, ...)` and drop the pseudo-seeding.

### L-6 — Bare `BCryptPasswordEncoder`, so there is no cost-factor upgrade path
`PasswordService.java:26-28` constructs `new BCryptPasswordEncoder(bcryptSaltRounds)` directly rather
than a `DelegatingPasswordEncoder`. Hashes carry no `{id}` prefix, and there is no rehash-on-login
when the configured cost rises — so raising `bcrypt-salt-rounds` from 11 only affects new hashes,
forever. **Fix:** wrap in a `DelegatingPasswordEncoder` with `bcrypt` as the default (and an
`argon2` migration target), and rehash on successful login when
`passwordEncoder.upgradeEncoding(hash)` is true.

### L-7 — `SecurityUtils.checkUsernameAndPasswordValidity` leaks the password policy pre-authentication
`SecurityUtils.java:51-70` returns messages such as *"The password has to be at least 8 characters
long"* from the unauthenticated `POST authenticate` endpoint (called at
`PublicUserJwtResource.java:95`). Minor, but it tells an attacker the exact policy without any
credential. **Fix:** return a single generic "invalid credentials" response from the login endpoint;
keep the specific messages for the registration and change-password flows where they are useful.

### L-8 — `createUser(ManagedUserVM)` does not lowercase the login, unlike every other writer
`UserCreationService.java:125` uses `userDTO.getLogin()` verbatim, whereas `updateUser` (line 216) and
`UserService.registerUser` (line 302) both `toLowerCase()`. `AdminUserResource.createUser` checks for
duplicates with `findOneByLogin(login.toLowerCase())` but then stores the original casing. On a
case-sensitive database an admin can create `Alice`, which
`ArtemisInternalAuthenticationProvider` (which lowercases the input, line 43) can never authenticate;
on MySQL's case-insensitive default the two collide. Also note `String.toLowerCase()` without a
`Locale` in `UserService.registerUser:302,310` is locale-sensitive (Turkish dotless-ı). **Fix:**
lowercase with `Locale.ENGLISH` in `createUser`, and use `Locale.ENGLISH` at every
`toLowerCase()` on identity fields.

### L-9 — Login response body returns the JWT even though the client never reads it
`PublicUserJwtResource.authenticate:109` returns `Map.of("access_token", responseCookie.getValue())`
alongside the `Set-Cookie` header. The Angular client discards it —
`AuthServerProvider.login` just returns the observable and relies entirely on the HttpOnly cookie
(`auth-jwt.service.ts:31-33`). So the body copy is pure surplus attack surface: it lands in any
proxy, APM, or browser-devtools capture of response bodies, and an XSS can read it (unlike the
cookie). **Fix:** return the token only when the caller explicitly asks (a query flag, or gate it on
the mobile-app `tool` parameter), and omit it for browser logins.

### L-10 — Dev profile combines wildcard CORS origins with `allow-credentials: true`
`application-dev.yml:72-78` sets `allowed-origin-patterns: "*"` with `allow-credentials: true`.
Unlike `allowedOrigins`, Spring's `allowedOriginPatterns` permits exactly this combination, so **any**
website can issue credentialed cross-origin requests to a dev instance and read the responses. The
prod profile does not set this, so it is dev-only — but a developer browsing the web while a local
Artemis is running is exposed, and the same profile also ships the default JWT secret (C-1), so a dev
instance is doubly open. **Fix:** restrict dev origins to
`http://localhost:9000` and `http://localhost:8080`; never pair `*` with credentials.

### L-11 — SAML2 attribute substitution uses `String.replaceAll` without `Matcher.quoteReplacement`
`SAML2Service.substituteAttributes` (line 217-225) interpolates IdP-supplied attribute values as the
*replacement* argument of `replaceAll`, where `$` and `\` are metacharacters. An attribute value
containing `$1` raises `IndexOutOfBoundsException` (failing the login), and because substitution
iterates over keys, a value containing `{otherKey}` is itself substituted in a later pass. **Fix:**
use `Matcher.quoteReplacement(value)`, and build the result in a single pass over the pattern rather
than iteratively rewriting `output`.

### L-12 — The account module has no `ResourceArchitectureTest`, so the annotation guardrail never runs for it
Other modules have a `*ResourceArchitectureTest` asserting that every REST method carries an
appropriate `@Enforce*` annotation and that no method-level annotation weakens a class-level one. There
is none for the account module, which is where `AdminUserResource`, `AccountResource`,
`PasskeyResource`, `TokenResource` and `UserResource` live — precisely the controllers where a missing
or weakened annotation matters most. Combined with M-14 (the URL-level admin rule no longer covering
`/api/admin/**`), a future admin endpoint could ship with no protection at either layer and no test
would fail. **Fix:** add `AccountResourceArchitectureTest` mirroring the existing per-module tests, and
extend it to assert that every controller mapped under an `admin/` segment carries `@EnforceAdmin` or
`@EnforceSuperAdmin`.

---

## Verified as correct

Recording these so the next audit does not re-litigate them:

* **No host-header injection in reset/activation links.** `MailService` takes `baseUrl` from the
  configured `@Value("${server.url}")` (`MailService.java:58-59,108`), never from a request header.
* **Reset-key entropy is adequate.** 20 alphanumerics from `SecureRandom` ≈ 119 bits
  (`RandomUtil.java:26-28`).
* **Reset key is single-use on success** — `setResetKey(null)` + `setResetDate(null)`
  (`UserService.java:257-258`).
* **Secrets are not API-serialisable.** `@JsonIgnore` on `password`, `resetKey`, `activationKey`,
  `vcsAccessToken`, `vcsAccessTokenExpiryDate` (`User.java:80,125,130,151,160`).
* **`change-password` requires the current password**, verified server-side
  (`UserService.changePassword:539-551`), and is refused for external users
  (`AccountResource.java:106-108`).
* **`registration.enabled` defaults to `false`** (`application-artemis.yml:31`), which closes M-5 and
  M-8 on default deployments.
* **External users cannot authenticate against the internal provider.** The queries filter on
  `internal = true` (`ArtemisInternalAuthenticationProvider.java:49,54`), and LDAP-sourced users are
  created with no password hash at all (`UserCreationService.java:74-82`).
* **Admin / super-admin separation is correctly enforced.** `checkSuperAdminAuthorizationToManageAdmin`
  is applied on create, activate, deactivate, update, single delete and bulk delete
  (`AdminUserResource.java:136,173,194,240,390,418`), and `isAdminByAuthorityName` covers **both**
  `ROLE_ADMIN` and `ROLE_SUPER_ADMIN` (`AuthorizationCheckService.java:633-639`) — so a plain admin
  can neither grant `SUPER_ADMIN` (the obvious escalation: set authorities + set a known password in
  one `PUT`) nor delete a super-admin. `checkCannotRemoveSuperAdminFromDefaultAdmin` prevents locking
  the instance out, and both delete paths refuse to delete the caller and the Iris bot.
* **The WebAuthn relying-party ID and allowed origins come from configuration, not from a request
  header.** `ArtemisPasskeyWebAuthnConfigurer` derives `rpId` from
  `artemis.user-management.passkey.relying-party-id` or, failing that, the host of the configured
  `server.url` (line 156), and builds `allowedOrigins` from configured URLs plus an explicit
  `additional-allowed-origins` list (lines 158-199). A Host-header-derived `rpId` would have been a
  phishing bypass; it is not derived that way.
* **Passkey ownership is checked.** `updatePasskeyLabel` and `deletePasskey` compare
  `credential.getUser().getId()` against the current user and return 404 on mismatch
  (`PasskeyResource.java:128-133,158-163`). Approval is `@EnforceSuperAdmin`
  (`PasskeyResource.java:176-178`), and the admin listing is too (line 93-94).
* **Tool tokens cannot be escalated.** `ToolsInterceptor` rejects any request whose token carries a
  `tools` claim when the handler lacks `@AllowedTools` (`ToolsInterceptor.java:59-62`), so
  `POST /api/account/tool-token` cannot be used to exchange one tool scope for another.
* **`extractValidJwt` refuses ambiguous credentials** — presenting both a cookie and a bearer token
  is a 400, not a silent preference (`JWTFilter.java:190-193`).
* **The passkey approval claim is not self-assertable.** `rotateTokenSilently` rebuilds the token
  from a `UsernamePasswordAuthenticationToken` whose `details` is null, so
  `is-passkey-super-admin-approved` falls back to `false` (`TokenProvider.java:148-151`) — the
  rotation path fails closed.
* **`jhi_user.login` has a unique index** (`00000000000000_initial_schema.xml:3332`), and
  `findAllByEmailOrUsernameIgnoreCase` filters `deleted = FALSE`
  (`UserRepository.java:210-221`).
* **`activateUser` clears the activation key** (`UserCreationService.java:250-255`).
* **Registration-number disclosure is opt-in.** `visibleRegistrationNumber` is `@Transient` and must
  be populated explicitly (`User.java:99-100,367-377`); the instructor search never does.
* **`X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and a restrictive
  `Permissions-Policy` are all set** (`SecurityConfiguration.java:273-279`).
* **The client never persists the JWT in JS-reachable storage.** `AuthServerProvider`
  (`auth-jwt.service.ts:31-54`) discards the `access_token` from the login response and only ever
  *clears* local/session storage; authentication is entirely cookie-based, so an XSS cannot read the
  session token out of storage. (It could still act through the cookie — see H-6.)
* **Sessions are stateless.** `SessionCreationPolicy.STATELESS`
  (`SecurityConfiguration.java:281`), so there is no server session to fix.
* **Both delete paths and the bulk delete refuse to remove the caller or the Iris bot**
  (`AdminUserResource.java:384-387,415-419`).

---

## Suggested remediation order

**Ship immediately (configuration only, no code):**
1. C-1, C-2 — purge the default JWT secret and admin credentials from `prod.env`,
   `application-dev.yml`, and `application-artemis.yml`. Rotate the JWT secret on every deployment
   that ever ran with the default; this invalidates all existing sessions, which is the point.
2. C-7 — rotate `build-agent-git-username` / `build-agent-git-password` away from
   `buildjob_user` / `buildjob_password`, including in `prod-multinode.env`.
3. C-4 — flip `artemis.rate-limiting.enabled` to `true`.
4. H-11 — repoint the nginx `loginlimit` block at `/api/core/public/authenticate` and add blocks for
   the reset and register endpoints. This is a one-line fix that restores the only non-spoofable
   throttle in the stack.
5. H-12 — restrict `/management/**` to `ROLE_ADMIN`; drop `env`, `configprops`, `logfile`,
   `threaddump` from the exposure list.
6. M-9 — narrow `artemis.security.internal.allowed-cidrs`.
7. M-15 — add the HSTS header at whichever layer you decide owns it.
8. If `artemis.lti.trustExternalLTISystems` is enabled anywhere, turn it off pending H-15.

**First code PR (small, high leverage):**
9. C-3 — fix client-IP resolution (one utility method; fixes C-3, M-9, M-10 together).
10. H-7 — throw `BadCredentialsException`, add the `AuthenticationException` handler, add the missing
    tests.
11. H-5 — constant-time dummy bcrypt on the user-absent path.
12. H-4 — always return `200` from `reset-password/init`.
13. H-3 — stop minting unused reset keys in `createUser(ManagedUserVM)`.
14. H-9 — byte-based password-length validation, so long passphrases stop 500-ing the reset flow.
15. H-13 — stop `users/initialize` from re-activating an admin-deactivated account.
16. M-13 — read the passkey approval flag from the verified credential.
17. M-14 / L-12 — widen the admin URL matcher and add the missing architecture test.

**Second PR (the account-recovery hardening the audit was really about):**
18. C-6 — verified e-mail change + the three missing security notifications.
19. C-5 — per-account lockout, applied to the git path too.
20. M-4 — 1-hour expiry, attempt counter, key in the URL fragment, atomic single-use update.
21. M-3 — hash reset and activation keys at rest.
22. M-2 — disallow `@` in `LOGIN_REGEX` and disambiguate the reset lookup.
23. H-10 — step-up authentication for passkey enrolment; delete passkeys on reset and soft delete.
24. M-16 — `userVerification = REQUIRED`, verified server-side.

**Third PR (structural):**
25. H-1 — `credentialsInvalidatedAt` + `JWTFilter` enforcement; shorten `remember-me`. This is the
    keystone: H-13, H-14 and much of C-6's severity all reduce once tokens are revocable.
26. H-14 — one "revoke every credential" service method, invoked from every lifecycle transition, plus
    `activated`/`deleted` checks in both git authentication paths.
27. H-2 / M-8 — a real authentication-source field and IdP-subject-based linking.
28. H-15 — bind LTI launches to a pre-linked subject; never authenticate from an e-mail claim.
29. H-8 — `email_verified`, e-mail uniqueness check, unique index on `jhi_user.email`.
30. M-11 — hash VCS tokens; revoke all credentials on reset/change/delete.
31. M-17 — proof of possession for SSH key registration.
32. H-6 — CSP without `unsafe-inline`/`unsafe-eval`; M-7 — re-enable CSRF.
