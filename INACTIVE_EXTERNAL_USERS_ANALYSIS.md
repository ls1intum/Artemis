# Why external users end up with `activated = 0`

Analysis of the reported git authentication failure after the 9.9 upgrade.
Data from the production dump (`localhost:3307`, schema `Artemis`, 34,354 users).

## Summary

External (LDAP) users created by **course / exam / admin student import** are written with
`activated = false` and an activation key, and nothing ever activates them. Until 9.9 this was
invisible because the LDAP login path is the only authentication provider that does not check
`activated`. Release 9.9 (#13404) added the check to the git paths, so these users can still log
into the web UI but are now rejected by git.

**2,347 users are affected; 1,040 of them hold a git credential and 631 have submitted work in 2026.**

The activation key on those rows is dead data: it can only be redeemed through
`GET /activate`, which is gated behind the registration feature flag and only ever reached from a
self-registration email that an LDAP user never receives.

## The data

`jhi_user` grouped by state:

| activated | internal | deleted | count | with activation key | with password |
|---|---|---|---|---|---|
| 0 | external | no | **2,347** | 2,346 | 0 |
| 0 | external | yes | 1 | 1 | 1 |
| 0 | internal | no | 22 | 20 | 22 |
| 0 | internal | yes | 13 | 0 | 13 |
| 1 | external | no | 26,646 | 2 | 0 |
| 1 | internal | no | 5,325 | 0 | 5,325 |

The 2,347 are not stale shells — they are real, active students:

| signal | count |
|---|---|
| has a course role | 2,338 |
| has a registration number | 2,318 |
| has a participation | 1,701 |
| has a submission | 1,594 |
| **holds a git credential** (participation VCS token / SSH key / personal token) | **1,040** |
| registered for an exam | 984 |
| **submitted something in 2026** | **631** |
| has an SSH key | 52 |
| created in 2026 / 2025 | 271 / 757 |

`last_login_date` is useless here — all 34,354 rows share the single timestamp
`2026-08-02 10:30:44`, a blanket backfill from `20260718120500_changelog.xml`.

### `created_by` is the smoking gun

| `created_by` | inactive external (2,347) | active external (26,646) |
|---|---|---|
| named staff login | **1,687** (2020-06 … 2026-04) | a few hundred |
| `anonymousUser` | 660 (2019-09 … 2020-10) | 13,651 |
| `system` | **0** | 12,498 |

Inactive external users are overwhelmingly created *by an instructor or TA* — i.e. by someone
running a student import. Active external users are created by the unauthenticated first-login
path. The two populations come from different code paths, and only one of them activates.

The 660 `anonymousUser` rows are a closed historical batch (all before 2020-10). The 1,687
staff-created rows run to 2026-04, so **the leak is ongoing**: 271 new ones in 2026 alone.

## Root cause

Three entry points import students by registration number / login / email:

- `CourseAccessService.java:143` — course student/TA/editor/instructor import
- `ExamRegistrationService.java:149` — exam student registration
- `AdminUserResource.java:315` — admin user import

All three funnel into:

```
UserService.findUser(registrationNumber, login, email)      UserService.java:730
  → not in DB → UserService.findUserInLdap(...)             UserService.java:768 → :460
      → UserCreationService.createUser(login, "", …, isInternal = false)   UserService.java:481
```

and `UserCreationService.createUser` unconditionally does:

```java
// new user is not active
newUser.setActivated(false);                                UserCreationService.java:104
// new user gets registration key
newUser.setActivationKey(RandomUtil.generateActivationKey());
newUser.setInternal(isInternal);
```

Compare the LDAP **first-login** path, which calls the same factory and then immediately undoes it:

```java
User newUser = userCreationService.createUser(..., false);   LdapAuthenticationProvider.java:125
newUser.setAuthorities(authorityService.buildAuthorities(newUser));
if (!newUser.getActivated()) {
    newUser.setActivated(true);                              LdapAuthenticationProvider.java:131
    newUser.setActivationKey(null);
}
```

`findUserInLdap` has no such compensation. That asymmetry is the bug: whether an LDAP user ends up
activated depends on whether they logged in before an instructor imported them.

## Why it only broke in 9.9

`LdapAuthenticationProvider.authenticate` never consults `activated` — there is even an explicit
`// TODO: make sure the user is not deactivated in the meantime` at `LdapAuthenticationProvider.java:108`.
Every other provider does check it:

- `ArtemisInternalAuthenticationProvider.java:67`
- `SAML2Service.java:159`
- `OIDCService.java:121`
- `ArtemisWebAuthnAuthenticationProvider.java:87` (passkey)

So on an LDAP instance these users log in to the web UI without complaint. Commit `50981979d2`
("Enforce account state in git authentication", #13404, 2026-08-08, first shipped in **9.9**) added
the check to both git paths:

- `LocalVCServletService.java:487` — HTTP(S) clone/push, before any credential comparison
- `GitPublickeyAuthenticatorService.java:110` — SSH public-key auth

Hence the exact reported symptom: **web login works, git does not.** The 9.9 change is correct;
it exposed pre-existing bad data rather than causing it.

Side effect worth noting: because `SAML2Service:159` does check `activated`, an instance that
switches from LDAP to SAML2 would lock these 2,347 users out of the web UI entirely.

## Does `activated = 0` + activation key make sense for external users?

No. Confirmed in code:

- The activation key is only redeemable via `PublicAccountResource.activateAccount`
  (`GET /activate?key=`), which starts with `if (accountService.isRegistrationDisabled()) throw …`.
- The only place an activation mail is ever sent is `PublicAccountResource.registerAccount`
  (`POST /register`), behind the same flag.
- `registrationEnabled` reads `artemis.user-management.registration.enabled`, defaulting to
  disabled (`AccountService.java:57`).
- Self-registration hard-codes internal: `UserService.registerUser` sets `setInternal(true)`
  (`UserService.java:349`) with the comment *"registered users are always internal"*.

So the mechanism is **only** meaningful for internal users **on an instance with registration
enabled** — exactly as you said. For an external LDAP user it is unreachable state: no mail is
ever sent, and even if the key leaked, `/activate` returns 403 with registration off.

The 22 inactive internal users are the legitimate case: 20 abandoned self-registrations from
2019/2020 (when registration was on) that still carry keys, plus 2 without keys that were
deliberately deactivated by an admin.

## Fix

### Code

`UserCreationService.createUser(login, password, …, isInternal)` now only creates an unactivated
user when they could actually activate themselves — an internal account **and** self-registration
enabled:

```java
if (isInternal && isRegistrationEnabled()) {
    newUser.setActivated(false);
    newUser.setActivationKey(RandomUtil.generateActivationKey());
}
else {
    newUser.setActivated(true);
}
```

Worth noting while reading this: `UserService.registerUser` — the actual self-registration path —
builds its `User` by hand and never calls this factory. So no existing caller wanted an unactivated
user; the gate encodes the rule rather than serving a current caller.

Two follow-on changes:

- **`LdapAuthenticationProvider`** — removed the `if (!newUser.getActivated())` block that existed
  only to undo what the factory did unconditionally.
- **`LtiService`** — now sets `setActivated(true)` explicitly. It already cleared the activation key
  but left `activated = false`, so LTI-provisioned users had the same defect (prod has 3 LTI users,
  1 of them inactive). Stating it at the call site keeps LTI correct even on an instance that *does*
  have registration enabled, where the factory default is still unactivated.

### Data

Migration `20260820091251_changelog.xml`, guarded on the activation-key discriminator:

```sql
UPDATE jhi_user SET activated = 1, activation_key = NULL
 WHERE activated = 0 AND is_internal = 0 AND is_deleted = 0 AND activation_key IS NOT NULL;
```

`createUser` set `activated = false` **and** a key; `deactivateUser` sets `activated = false` and
**never** a key. So the predicate repairs import-created accounts and cannot touch a deliberate
administrative deactivation — it excludes `id 9465 / ge35yad` (no key, `last_modified_by = krusche`,
2025-04-24) by construction. Internal users are excluded too: an unactivated internal account with a
key is a genuine abandoned self-registration and must stay that way.

TUM prod was already repaired by hand, so this migration matches 0 rows there. It is kept for the
other Artemis instances, which have the same import path and no one to hand-fix them.

### Still open

The `TODO` at `LdapAuthenticationProvider.java:108` — the provider should check `activated` like
every other one, so a deactivated user cannot keep web access. Left out of this change deliberately:
it is only safe once every instance has run the migration above.

## Verification

| command | result |
|---|---|
| `./gradlew test --tests UserServiceTest` | 10/10 pass (3 new) |
| `./gradlew test --tests LtiServiceTest` | 12/12 pass (1 new) |
| `./gradlew test --tests CourseLdapRegistrationTest` | 5/5 pass (assertion added) |
| `AccountResource`, `LdapAuthentication`, `InternalAuthentication`, `UserSaml2`, `UserOIDC`, `ExamRegistration`, `AdminUserResource` | 160/160 pass |
| `./gradlew spotlessApply checkstyleMain` | clean |

The new assertion in `CourseLdapRegistrationTest.testRegisterLDAPUsersInCourse` was confirmed to
fail against the old behaviour before the fix was restored, so it genuinely covers the reported bug
rather than just passing.

Registration is enabled in the test configuration, which is why the internal-user path is unchanged
under test and the regression surface stayed small.

## Follow-up: the activation invariant, audited

Every write of `activated = false` in the codebase, checked one by one:

| Site | Kind | Verdict |
|---|---|---|
| `UserCreationService.createUser:119` | awaiting self-activation | now gated on `isInternal && registrationEnabled` |
| `UserService.registerUser:348` | awaiting self-activation | already correct — hard-codes `setInternal(true)`, only reachable through `POST /register`, which is gated |
| `UserCreationService.deactivateUser:335` | deliberate deactivation | correct for any account type; never sets a key |
| `UserCreationService.updateUser:271` | deliberate deactivation | admin edit form, same transition as above |
| `UserService.anonymizeUser:533` | soft deletion | correct, paired with `is_deleted` |

So the invariant holds: the *awaiting-activation* state is produced only for an internal account on an
instance with self-registration enabled. The other two kinds are not the activation workflow, and
neither sets an activation key — which is what makes the key a reliable discriminator.

This is now documented on the field itself (`User.activated` and `User.activationKey`) and in
`documentation/docs/admin/user-registration.mdx`.

### One thing deliberately left alone

`UserResource.initializeUser` (`PUT /api/account/users/initialize`) self-activates the calling user when
the account is external or when LTI is configured and the account is not LTI-created. It reads as a
workaround for exactly this bug, and `UserTestService.initializeUserExternal` asserts the behaviour, so
it is deliberate and covered rather than accidental.

Two reasons it is worth revisiting separately:

- After the migration, the `!user.isInternal()` branch is dead for newly created accounts — external
  accounts are now always created activated.
- `JWTFilter` does not re-check `activated`, and `revokeAllCredentials` clears passkeys, SSH keys and VCS
  tokens but not session cookies. A user deactivated mid-session therefore keeps a valid JWT until it
  expires, and this endpoint would let them set `activated` back to `true`.

Not changed here: it alters tested behaviour on a user-facing endpoint, and the JWT half is a broader
session-handling question than this branch.

## Follow-up: the login-options endpoint

`GET /api/core/public/login-options` backs the identifier-first login form.

**Directory lookup — removed.** It did call the directory: on a miss in the local database it ran
`findByLogin` / `findByAnyEmail` against LDAP, and answered *external provider* on a hit versus
*password* on a miss. With LDAP configured alongside OIDC or SAML2, the response therefore varied with
directory membership, and an unauthenticated caller could drive one directory query per request.
`LoginOptionsService` now decides from local state only — an internal account gets the password form,
everything else goes to the identity provider — so a known-external identifier and an
unknown one answer identically. The `LdapUserService` dependency is gone from the service, and
`testGetLoginOptions_UnknownIdentifierIsIndistinguishableFromExternalUser` locks the property.

Behaviour worth noting: an unknown identifier is now sent to the provider instead of being offered a
password form. That is the better answer anyway — an identifier with no Artemis account cannot
authenticate with a password, and the provider is where a first-time user gets provisioned.

**Rate limits.** The endpoint already carried `@LimitRequestsPerMinute(AUTHENTICATION)`, but had *no*
nginx location block, so nothing bounded it at the edge. Now:

| Layer | Before | After |
|---|---|---|
| nginx | none | `loginoptionslimit`, 30 r/m, `burst=3 delay=2` |
| Artemis | `AUTHENTICATION` (shared bucket) | dedicated `LOGIN_OPTIONS`, 30 rpm |

The dedicated type matters because bucket keys include the type name: sharing `AUTHENTICATION` meant one
login-options call and the login that follows it drew down the same allowance, and git authentication
drew from it too. 30 rpm rather than the stricter account-management budget because a whole campus can
sit behind one NAT address and this call precedes every single login.

Also found and fixed while mapping this: `GET /activate` had the nginx limit but no application-level
one. It now uses `ACCOUNT_MANAGEMENT`, like `register` and the password-reset endpoints.

**Ansible.** Not in this repository — the nginx templates live in
[`ls1intum/artemis-ansible-collection`](https://github.com/ls1intum/artemis-ansible-collection). The two
hunks to port are the zone in `docker/nginx/artemis-nginx.conf` and the location in
`docker/nginx/artemis-server.conf`.

## Verification, second round

| command | result |
|---|---|
| `nginx -t` on the assembled config (docker `nginx:alpine`) | syntax ok |
| `cd documentation && pnpm run build` | success — `onBrokenLinks: 'throw'`, so cross-references are valid |
| `LoginOptionsServiceTest` | 15/15 (4 new/rewritten) |
| `RateLimitConfigurationServiceTest` | 11/11 (3 new) |
| `LimitRequestsPerMinuteAspectTest`, `RateLimitServiceTest` | pass |
| `UserService`, `Lti`, `CourseLdapRegistration`, `AccountResource`, `LdapAuthentication`, `InternalAuthentication`, `UserSaml2`, `UserOIDC`, `AdminUserResource`, `ExamRegistration` | 202/202 |
| `./gradlew spotlessCheck checkstyleMain` | clean |

Client specs stub the `login-options` response, so the server-side change needs no client update.

## Follow-up: no workaround for a deactivated account

`UserResource.initializeUser` (`PUT /api/account/users/initialize`) used to activate the caller when the
account was external, or LTI was configured and the account was not LTI-created. That was a workaround
for external accounts being created unactivated; they no longer are, so the only way to reach the
endpoint unactivated is for an administrator to have deactivated the account. The endpoint no longer
writes `activated` at all.

### Untangling `activated` from LTI initialisation

Closing it exposed that the LTI flow was using `activated` as its own bookkeeping. `buildLtiResponse`
added `?initialize` when the account was not activated, and the endpoint then returned a generated
password *and* activated the account, so later launches skipped the dialog. Creating LTI accounts
activated — which the invariant requires — would therefore have silently stopped the launch from ever
handing the account holder their Artemis password.

Fixed by giving the flow its own marker, `lti_initialized` (migration `20260820105243`):

| | Before | After |
|---|---|---|
| Account created by the launch | unactivated | activated |
| `?initialize` added when | `!activated` | LTI-created and not yet initialised |
| Endpoint eligibility | not activated, and LTI-created or external | internal, LTI-created, not yet initialised |
| Endpoint writes | password + `activated = true` | password + `lti_initialized = true` |

Backfill marks an already-activated LTI account as initialised, since it has been through the dialog. An
unactivated one keeps the default, so a genuinely uninitialised account still gets its dialog while a
deactivated one is no longer activated by it.

`initializeUserNonLTI` and `initializeUserExternal` now assert `activated` stays `false` — previously
they asserted the opposite, which is what pinned the workaround in place.

## Follow-up: deactivation in the audit log

Both routes that write the flag now record it, since they are separate code paths — the deactivate
endpoint goes through `deactivateUser`, while the admin edit form writes it inside `updateUser`:

| Event | Principal | Data |
|---|---|---|
| `DEACTIVATE_USER` | the administrator who did it | `user=<login>` |
| `ACTIVATE_USER` | the administrator, or `system` where there is no authenticated actor | `user=<login>` |

Neither is in `GENERAL_EVENT_TYPES`, so they fall under the long retention rather than being pruned with
login records. A comment in `updateUser` already claimed the acting administrator "is recorded in the
audit event instead" of being emailed — that is now actually true.

Covered by three tests in `AccountCredentialRevocationIntegrationTest`, which assert the principal is the
administrator and not the affected account. They read through `AuditEventService` rather than the
repository, because `PersistentAuditEvent.data` is lazy and unreadable outside a session.

## Verification, third round

| command | result |
|---|---|
| `nginx -t` on the rendered ansible template | syntax ok |
| burst test against the rendered ansible config | 4 pass / 16 × 429 on login-options; control path `/api/core/public/account` unthrottled 20/20 |
| `cd documentation && pnpm run build` | success |
| the 17 affected server suites | 300/300 |
| `./gradlew spotlessCheck checkstyleMain` | clean |
