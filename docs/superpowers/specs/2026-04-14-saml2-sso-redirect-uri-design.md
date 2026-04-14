# SAML2 SSO Redirect URI for External Clients

**Date:** 2026-04-14
**Branch:** `feature/general/saml2-sso-redirect-uri`
**Scope:** Artemis backend only (no client/extension changes)
**Version:** v1 — restricted deployment feature with operator risk acceptance (see Security Considerations)

## Problem

After successful SAML2 authentication, Artemis always redirects to `/` (the web UI). External clients like the VS Code extension, iOS app, and Android app have no way to receive the authentication token after SSO login. Users on SAML2-only instances must use the "forgot password" workaround to set a password for API access.

## Solution

Add an optional `redirect_uri` parameter to the SAML2 authentication flow. When present, Artemis redirects to the provided URI with a JWT token after successful authentication. Without the parameter, the existing web SAML2 flow is completely preserved.

## End-to-End Flow

```
1. External client opens browser:
   {artemisUrl}/saml2/authenticate/{registrationId}?redirect_uri=vscode://artemis/callback

2. Custom Saml2AuthenticationRequestResolver:
   a. Validates redirect_uri (scheme allowlist, length, syntax)
   b. Stores redirect_uri in Hazelcast distributed map keyed by a UUID nonce (TTL 5 min)
   c. Sets nonce as RelayState (always < 80 bytes, OASIS-compliant)

3. Browser redirects to IdP with AuthnRequest + RelayState (nonce only)

4. IdP authenticates user, sends SAML2 Response + RelayState (nonce) back to Artemis ACS URL

5. Custom AuthenticationSuccessHandler (extends SimpleUrlAuthenticationSuccessHandler):
   a. Reads RelayState from the ACS HttpServletRequest
   b. If no nonce → calls super.onAuthenticationSuccess() (default "/" redirect, web flow)
   c. If nonce present:
      i.   Atomically consumes redirect_uri from Hazelcast via IMap.remove(nonce)
      ii.  If null (expired/consumed/invalid) → HTTP 400 hard failure
      iii. Extracts Saml2AuthenticatedPrincipal from the Saml2Authentication
      iv.  Calls SAML2Service.handleAuthentication(authentication, principal, request)
      v.   Generates JWT via TokenProvider.createToken(returnedAuth, rememberMe)
      vi.  Builds redirect URI using UriComponentsBuilder, appends jwt parameter
      vii. Sends HTTP 302 redirect

6. External client receives JWT via OS URI handler
```

**Without `redirect_uri`:** The success handler calls `super.onAuthenticationSuccess()` — exactly what `defaultSuccessUrl("/", true)` does today. The SPA's existing flow (SAML2flow cookie → `POST /api/core/public/saml2` → JWT cookie) is completely untouched. `handleAuthentication()` is NOT called in the fallback branch — that happens later in the SPA's `POST /api/core/public/saml2` call. No Angular changes needed.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Token delivery | JWT as query parameter | Custom URI schemes (`vscode://`) are local-only, no proxy/referrer/history leakage. Established pattern (GitHub CLI, VS Code GitHub extension). Auth code exchange would add significant complexity for minimal security gain on custom schemes. |
| URI validation | Scheme-based allowlist in config | Custom URI schemes are OS-bound to specific apps, so scheme-level validation is sufficient. `http`/`https` are always blocked. **Limitation:** Private-use URI schemes can theoretically be claimed by multiple apps on some platforms (especially Android). For v1 this risk is accepted by the operator via configuration; future versions may add full redirect URI registration. |
| Flow integration | Custom `AuthenticationSuccessHandler` extending `SimpleUrlAuthenticationSuccessHandler` | Reuses Spring's default success handler for the fallback flow (`defaultTargetUrl="/"`, `alwaysUseDefaultTargetUrl=true`). Only adds external redirect logic on top. No Angular changes needed. |
| State transport | Nonce in RelayState + redirect_uri in Hazelcast | Raw redirect_uri in RelayState is unsafe (attacker could modify during browser redirect). Instead, store a UUID nonce in RelayState (always < 80 bytes, OASIS HTTP-Redirect MUST compliant) and keep the validated redirect_uri in a Hazelcast distributed map. Hazelcast is required because Artemis runs in clustered deployments where HttpSessions are not shared across nodes (same reason the passkey implementation uses Hazelcast repositories). |
| Token lifetime | Configurable, defaults to standard (non-rememberMe) | Hardcoding rememberMe=true is too risky for external tokens. Default to standard token validity; allow override via config property `saml2.external-token-remember-me` (default: false). |
| Reuse strategy | Maximize reuse of existing Artemis code | Reuse: `SAML2Service.handleAuthentication()` (user creation, sync, audit, login email), `TokenProvider.createToken()` (JWT generation), `SimpleUrlAuthenticationSuccessHandler` (fallback redirect), Hazelcast patterns from passkey repos. New code only for: redirect URI validation, nonce storage, AuthnRequest resolver. |

## Configuration

New properties in `application-saml2.yml`:

```yaml
saml2:
  # Existing properties unchanged
  allowed-redirect-schemes: []    # Default: empty = feature disabled
  # Example:
  # allowed-redirect-schemes:
  #   - vscode
  #   - artemis-ios
  #   - artemis-android
  external-token-remember-me: false  # Default: standard token validity for external clients
```

- Empty list = feature disabled. If `redirect_uri` is provided while the feature is disabled, it is silently ignored and the user is redirected to `/` (standard web flow). Note: The RelayState resolver API does not support HTTP error responses, so a strict HTTP 400 rejection is not possible at this layer. The user will complete the IdP login and land on the web UI instead of the external client callback.
- `http` and `https` are always rejected, even if configured (hardcoded blocklist)

Implementation: New fields in `SAML2Properties.java` (following existing JavaBean getter/setter style):
- `allowedRedirectSchemes` as `List<String>`, default empty
- `externalTokenRememberMe` as `boolean`, default false

## Components

### New: `SAML2ExternalClientAuthenticationSuccessHandler`

**Extends `SimpleUrlAuthenticationSuccessHandler`** with `defaultTargetUrl="/"` and `alwaysUseDefaultTargetUrl=true` (replicates `defaultSuccessUrl("/", true)` behavior).

**External redirect flow (nonce found in RelayState):**
1. Read RelayState from `HttpServletRequest` (ACS request parameter)
2. Atomically consume redirect_uri from Hazelcast via `IMap.remove(nonce)` (returns old value, single operation)
3. If `null` (expired TTL, consumed, or invalid nonce) → HTTP 400 hard failure. This prevents broken or replayed external flows from being silently downgraded into the web flow.
4. Extract `Saml2AuthenticatedPrincipal` from the `Saml2Authentication` passed to `onAuthenticationSuccess()`
5. Call `SAML2Service.handleAuthentication(authentication, principal, request)` — reuses all existing user creation, sync, audit logging, and login email logic
6. Pass the returned `UsernamePasswordAuthenticationToken` to `TokenProvider.createToken(auth, rememberMe)` to generate the raw JWT string
7. Build redirect URI using `UriComponentsBuilder` (handles existing query params, encoding correctly)
8. Send HTTP 302 redirect

**Fallback flow (no nonce in RelayState):**
- Call `super.onAuthenticationSuccess(request, response, authentication)` — does nothing except redirect to `/`
- `handleAuthentication()` is NOT called here — the SPA handles that via `POST /api/core/public/saml2`

**Error handling:**
- `UserNotActivatedException` → HTTP 403 response (no JWT issued, no redirect to external URI). Mirrors `PublicUserJwtResource.authorizeSAML2()` error handling.
- Invalid nonce / expired / consumed nonce → HTTP 400 (hard failure, not silent fallback)

**Nonce storage bounds (Hazelcast map):**
- Hazelcast map with TTL of 5 minutes (automatic expiry, no manual cleanup needed)
- Nonces are keyed by UUID, globally unique — no per-session grouping needed
- One-time consumption: `IMap.remove(nonce)` atomically returns and removes the entry
- Note: This `consumeAndRemove` semantic is new — the passkey Hazelcast repos don't remove on read (they use explicit `save(null)`). The atomic remove pattern is the right fit for one-time nonces.

### New: Custom `Saml2AuthenticationRequestResolver`

Wraps Spring's `OpenSaml5AuthenticationRequestResolver` to:
1. Check for `redirect_uri` query parameter on the initial request
2. If present and allowlist is non-empty:
   a. Validate the URI immediately (before IdP redirect):
      - Valid URI syntax (absolute URI, not relative)
      - Scheme in allowlist (case-insensitive, `Locale.ROOT`)
      - Scheme not `http`/`https` (hardcoded blocklist)
      - Total URI length ≤ 200 bytes
      - No fragment component
   b. Store validated redirect_uri in Hazelcast distributed map keyed by UUID nonce (TTL 5 min)
   c. Set nonce as RelayState via `setRelayStateResolver()`
3. If `redirect_uri` is present but allowlist is empty (feature disabled): silently ignored (RelayState resolver cannot send HTTP error responses), user proceeds through normal web flow
4. If no `redirect_uri`: use default behavior (no RelayState modification)

### New: `HazelcastSaml2RedirectUriRepository`

Hazelcast-backed store for nonce → redirect_uri mappings.

- **Package:** `de.tum.cit.aet.artemis.core.repository.saml2`
- Hazelcast map name: `saml2-redirect-uri-nonce-map`
- TTL: 5 minutes (configured via `MapConfig.setTimeToLiveSeconds()` in `@PostConstruct`)
- `save(nonce, redirectUri)` — stores the mapping via `IMap.put()`
- `consumeAndRemove(nonce)` — calls `IMap.remove(nonce)` which atomically returns and removes the entry. Returns `null` if not found/expired.
- `@Repository`, `@Profile(PROFILE_SAML2)`, `@Lazy`
- Constructor injection of `@Qualifier("hazelcastInstance") HazelcastInstance`

### Modified: `SAML2Configuration.java`

- Register the custom success handler via `.saml2Login(config -> config.successHandler(...))`
- Register the custom authentication request resolver
- Wire new dependencies via constructor injection (currently the class has zero injected dependencies — this is a structural change): `SAML2Service`, `TokenProvider`, `SAML2Properties`, `HazelcastSaml2RedirectUriRepository`

### Modified: `SAML2Properties.java`

- Add `allowedRedirectSchemes` field (`List<String>`, default empty) with getter/setter (existing JavaBean style)
- Add `externalTokenRememberMe` field (`boolean`, default false) with getter/setter

### Unchanged: `PublicUserJwtResource.java`

The `POST /api/core/public/saml2` endpoint remains for the web UI flow. No modifications needed.

## Validation Details

```java
// Validation happens in the AuthenticationRequestResolver, BEFORE the IdP redirect
URI uri = URI.create(redirectUri);                                 // Syntax check — catches malformed URIs
if (!uri.isAbsolute())                                             // Must be absolute
    → reject
String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
if (Set.of("http", "https").contains(scheme))                      // Hardcoded blocklist
    → reject "http/https redirect URIs are not allowed"
if (!allowedRedirectSchemes.contains(scheme))                      // Allowlist check
    → reject "URI scheme not in allowlist"
if (redirectUri.getBytes(UTF_8).length > 200)                      // Length check
    → reject "redirect_uri exceeds maximum length"
if (uri.getFragment() != null)                                     // No fragments
    → reject "redirect_uri must not contain a fragment"

// If valid: store in Hazelcast, put nonce in RelayState
String nonce = UUID.randomUUID().toString();
hazelcastRepo.save(nonce, redirectUri);  // TTL configured on map (5 min)
// Set nonce as RelayState (36 bytes, well within 80-byte OASIS limit)
```

## Security Considerations

- **No open redirects:** Only configured custom URI schemes are allowed. `http`/`https` are always blocked.
- **RelayState integrity:** The redirect_uri is never placed in RelayState. Only an opaque nonce is sent through the IdP roundtrip. The validated redirect_uri is stored server-side in Hazelcast (distributed map), which cannot be tampered with externally. The nonce is atomically consumed on first use via `IMap.remove()`.
- **Nonce storage is distributed via Hazelcast.** This ensures the nonce lookup works across clustered Artemis nodes. Note: the overall SAML2 flow has the same HttpSession dependency as before (Spring Security's `HttpSessionSaml2AuthenticationRequestRepository` correlates AuthnRequest to Response via session). This is a pre-existing deployment concern, not introduced by this feature.
- **JWT in URL is acceptable for custom schemes:** Custom URI schemes are OS-routed to specific apps. No proxy logging, no referrer headers, no browser history. This is the same pattern used by VS Code's GitHub extension and GitHub CLI.
- **No breaking changes:** Without `redirect_uri`, the success handler calls `super.onAuthenticationSuccess()` for a plain redirect to `/`. The existing web SAML2 flow (`SAML2flow` cookie → `POST /api/core/public/saml2` → JWT cookie) is completely preserved. The `POST /api/core/public/saml2` endpoint is unchanged.
- **User activation check:** Inactive/disabled users get HTTP 403 (mirroring `PublicUserJwtResource.authorizeSAML2()`), no JWT is issued, no redirect to external URI.
- **Scheme-only allowlist limitation (v1 accepted risk):** Private-use URI schemes can be claimed by multiple apps on some platforms (especially Android where scheme registration is not exclusive). Operators enabling this feature accept this risk via configuration. Future versions may add full redirect URI registration for stricter control.
- **Audit logging:** Successful external redirects are logged with audit event `SAML2_EXTERNAL_REDIRECT_SUCCESS` (redirect_uri WITHOUT jwt parameter). Rejected redirect URIs are logged with rejection reason at WARN level. Raw user input is sanitized before logging.

## Known Pre-existing Issues

1. **Auth-method JWT claim:** `SAML2Service.handleAuthentication()` returns `UsernamePasswordAuthenticationToken`. When passed to `TokenProvider.createToken()`, `AuthenticationMethod.fromAuthentication()` maps this to `PASSWORD` instead of `SAML2`. The JWT `auth-method` claim is therefore `password` for SAML2 users — both in the existing web flow and in our new external redirect flow. The root cause is in `SAML2Service` returning the wrong token type, not in `AuthenticationMethod`. Out of scope for this PR.

2. **SAML2 session dependency:** Spring Security's `HttpSessionSaml2AuthenticationRequestRepository` correlates AuthnRequest to Response via HttpSession. In clustered deployments without sticky sessions or shared session storage, this can fail. This is a pre-existing concern affecting all SAML2 flows, not introduced by this feature.

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| No `redirect_uri` parameter | `super.onAuthenticationSuccess()` → redirect to `/`, SPA handles JWT exchange |
| Empty allowlist + `redirect_uri` provided | Silently ignored, standard web flow (RelayState resolver cannot send HTTP errors) |
| User already has active IdP SSO session | Flow completes without IdP login screen, RelayState preserved |
| User cancels IdP login | SAML2 error flow, no redirect. Standard Spring Security handling |
| `redirect_uri` has existing query params | JWT appended correctly via `UriComponentsBuilder` |
| User not activated | HTTP 403, no redirect to external URI, no JWT issued |
| SAML2 profile not active | No success handler registered, feature doesn't exist |
| `redirect_uri` exceeds 200 bytes | Rejected before IdP redirect (validation in request resolver) |
| Invalid nonce in RelayState (expired TTL/consumed/unknown) | HTTP 400 hard failure (not silent fallback) |
| `redirect_uri` contains fragment | Rejected before IdP redirect |
| Relative URI as `redirect_uri` | Rejected before IdP redirect |
| `http://` or `https://` redirect_uri | Always rejected, even if in allowlist |

## Testing Strategy

- Unit tests for redirect URI validation (valid schemes, blocked schemes, length, syntax, fragments, relative URIs)
- Unit tests for nonce generation, Hazelcast storage, and atomic one-time consumption
- Integration tests extending `AbstractSpringIntegrationLocalVCSamlTest` for the full SAML2 flow with `redirect_uri` (mock IdP)
- Integration tests verifying fallback behavior without `redirect_uri` (existing web flow preserved, `super.onAuthenticationSuccess()` called)
- Integration tests for rejected URIs (wrong scheme, too long, http/https, fragments)
- Integration tests for expired / consumed / invalid nonce → hard failure (HTTP 400)
- Integration tests for nonce replay (consumed nonce) → hard failure
- Integration tests for nonce TTL expiry (Hazelcast map eviction)
- Integration tests for `redirect_uri` provided while feature disabled → HTTP 400
- Integration tests for concurrent outstanding nonces
- Integration test for `UserNotActivatedException` → HTTP 403, no external redirect
- Verify ALL existing SAML2 tests still pass (no regression)
- Verify unchanged web SPA flow with no nonce present
- Verify `POST /api/core/public/saml2` endpoint behavior is unchanged
