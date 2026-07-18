# Scoped staff VCS access tokens for student repositories — design

**Date:** 2026-07-18
**Status:** Design (approved decisions captured inline)
**Author:** krusche (with Claude)

## 1. Problem & motivation

When a staff member (tutor / editor / instructor / admin) wants to work with a
**student's** programming-exercise repository (clone to inspect, debug, reproduce,
or assess), they authenticate git-over-HTTPS today with their **personal user VCS
access token** (`User.vcsAccessToken`). That token is:

- **Broad** — it authenticates the user for *every* repository their course roles
  allow (all student repos + all base repos across all their courses).
- **Long-lived** — valid up to one year.
- Being **deprecated** — it is unscoped and will be removed.

A leaked personal token therefore exposes everything the staff member can reach.
We want staff to instead use a **dynamic, per-repository token with a limited blast
radius**, minted only when actually needed, and stored in the existing
`repository_vcs_access_token` table.

## 2. Goals / non-goals

**Goals**
- Let staff obtain a VCS access token scoped to **exactly one student assignment
  repository**, minted on demand.
- Reuse the existing `RepositoryVCSAccessToken` mechanism (which already scopes staff
  tokens to base repositories) — extend it to `repository_type = USER`.
- Add no new privilege: the token authenticates only; read/write stays governed by the
  live course role on every git operation.
- Give users a **user-settings overview** of their scoped tokens with a **revoke**
  action.

**Non-goals**
- Not touching the git authentication/authorization code paths (they already handle a
  repository-scoped token for any URI — see §5).
- **No code-button / clone-dialog change in this concept.** This concept delivers the
  server-side *capability* (mint a scoped student-repo token) plus the overview/revoke UI.
  Wiring the clone dialog to actually *use* a scoped token when staff clone a student repo
  (today it uses the broad personal token) is a **deliberate follow-up** — see §8. Once it
  lands, staff clone dialogs mint/use the scoped token and inherit the same get-or-create
  re-mint behaviour the base-repo and participation tokens already have.
- Not migrating or removing the personal user token here (separate deprecation).
- No token expiry/TTL, no rotation, no "show token again if lost" recovery flow.
- No changes for lost tokens — in practice users never handle these directly; a lost
  token is simply re-minted on the next clone-dialog visit.

## 3. Access-rights review (grounding)

Authorization for a git operation is **role-based and re-evaluated on every request**
(`RepositoryAccessService.checkAccessRepositoryElseThrow`). **Tokens only authenticate
— they never carry permissions.** Effective rights on a **student assignment repo**:

| Role | Clone (READ) | Push (WRITE) | Existing credential |
| --- | :---: | :---: | --- |
| Student (owner) | ✅ after start | ✅ if unlocked & pre-due | own `ParticipationVCSAccessToken` |
| Tutor (TA) | ✅ | ❌ | — (uses broad personal token today) |
| Editor | ✅ | ✅ | — |
| Instructor | ✅ | ✅ | — |
| Admin | ✅ | ✅ | — |

**"Only generate a token when necessary" therefore means:**
- **Never mint for the owner** — the student already has a `ParticipationVCSAccessToken`.
  (Naturally excluded: the mint endpoint requires ≥ TA.)
- **Never mint for non-staff non-owners** — they have no access at all.
- Mint a scoped student-repo token **only** for a **≥ TA staff member who is not the
  owner**, **on demand**, for **one specific repository**, and **get-or-create** so we
  never create duplicates.
- Tutors are read-only by role and never receive a write-capable path.

## 4. Decisions (from design discussion)

| Decision | Choice | Rationale |
| --- | --- | --- |
| Permission model | **Read + write, capped by live role** | Matches today's role matrix exactly; enforced by the existing per-request role check; no permission column needed. |
| Lifetime | **Persist until revoked** | Safe because authorization is live: a lingering token stops working the moment the holder loses their role, before async cleanup. "Dynamic" = created on demand, not short-lived. |
| Secret storage | **Plaintext, retrievable** | Consistent with the base-repo and participation tokens; get-or-create can return the same ~262-bit random token so the clone dialog can re-embed it. Relies on DB access control. |
| Participation link | **Add nullable `participation_id` FK** | Enables cascade cleanup on participation delete, per-repo audit, and mint-time validation. |
| Mint-time authorization | **≥ TA guard + explicit READ-access check (defense in depth)** | Mint only a token for a repo the requester can already access. |
| Overview columns | **Context + Revoke, no timestamps** | Keeps the feature schema-cost-free beyond the one FK. |
| Personal token in overview | **Excluded** | Stays on its existing (soon-deprecated) page. |

## 5. Design

The student-repo token is the existing `RepositoryVCSAccessToken`, extended to
`repository_type = USER`. The **git-side auth & authorization code needs no change**:

- **Authentication** — `LocalVCServletService.tryAuthenticationWithRepositoryVcsAccessToken`
  already looks up `findByUserIdAndRepositoryUri(userId, exact repositoryUri)`. A
  student-repo row authenticates unchanged; a token for repo A can never authenticate
  repo B (the URI is part of the lookup key).
- **Authorization** — student repos already route through
  `RepositoryAccessService.checkAccessRepositoryElseThrow`, which re-derives read/write
  from the live course role every request (tutor = read; editor/instructor/admin =
  read+write). The token adds zero privilege.

So the implementation is: (a) allow **minting** for `USER` repos, (b) the **schema**
FK, (c) **cleanup** on participation delete, and (d) the **overview + revoke** UI.

### 5.1 Schema — `repository_vcs_access_token`

Add one nullable column; everything else is unchanged.

| Column | Base-repo token | Student-repo token (new) |
| --- | --- | --- |
| `user_id` | staff user | staff user |
| `exercise_id` | exercise (NOT NULL) | exercise (NOT NULL) — so `deleteByExerciseId` already covers it |
| `repository_type` | TEMPLATE/SOLUTION/TESTS/AUXILIARY | **USER** |
| `auxiliary_repository_id` | set for AUX | null |
| `repository_uri` | base repo URI | **student assignment repo URI** (scoping key) |
| `vcs_access_token` | plaintext `varchar(50)` | plaintext `varchar(50)` |
| **`participation_id`** *(new, nullable)* | null | **FK → `participation(id)`, `ON DELETE CASCADE`, indexed** |

- Unique constraint `(user_id, repository_uri)` unchanged → one token per (staff, repo);
  base-repo and student-repo rows never collide (different URIs).
- Liquibase changelog: add `participation_id` column + FK (cascade) + index. New
  timestamped changelog file under `src/main/resources/config/liquibase/changelog/`.
- Entity `RepositoryVCSAccessToken`: add the lazy `@ManyToOne Participation participation`.

### 5.2 Minting — `RepositoryVcsAccessTokenResource` + `RepositoryVcsAccessTokenService`

Extend the existing `GET`/`PUT api/programming/repository-vcs-access-token`:

- Accept an optional `participationId` param. When `repositoryType=USER`, `participationId`
  is required (mirror of the existing AUXILIARY + `auxiliaryRepositoryId` rule).
- `validateRepositoryTypeInput`: allow `USER` when `participationId` is present; keep
  rejecting `USER` without it.
- Keep `@EnforceAtLeastTutorInExercise` (≥ TA in the exercise's course).
- **Add a defense-in-depth authorization check**: verify the participation belongs to the
  exercise, and that the requester has at least **READ** access to that specific student
  repo via `checkAccessRepositoryElseThrow(..., READ)`. Mint only what the requester can
  already access.
- Service: extend `resolveBaseRepository` (→ `resolveRepository`) with a `USER` branch that
  loads the participation, resolves its `repositoryUri`, and sets `participation` +
  `repository_type=USER` on the token. `getOrCreateToken` and the race-safe unique-constraint
  retry are reused as-is.
- Return the plaintext token (get-or-create).

### 5.3 Lifecycle / cleanup

- **Create**: on demand (get-or-create) via the mint endpoint.
- **Delete** (all reuse existing hooks, which already match student-repo rows):
  - Staff leaves course → `deleteForUserInCourseIfNoLongerStaff` (guarded by "no longer ≥ TA").
  - Exercise deleted → `deleteByExerciseId` (student-repo rows carry `exercise_id`).
  - User deleted → `deleteAllByUserId`.
  - **Student participation deleted → `participation_id` `ON DELETE CASCADE` (new).**
  - Manual revoke (see §5.5).
- No rotation; a re-mint after revoke produces a fresh secret (unique-constraint row was
  deleted, so `getOrCreateToken` inserts anew).

### 5.4 Token overview page (user settings)

- New user-settings sub-page ("Access tokens") rendering a **server-paginated** table using
  the **TUM UI table** (`app/shared-ui/tum-ui/table/tum-ui-table.component.ts`).
- **List endpoint** (new), e.g. `GET api/account/vcs-access-tokens` (paginated): returns the
  **current user's** scoped tokens across **both** token tables
  (`participation_vcs_access_token` + `repository_vcs_access_token`). Because the two tables
  are distinct and per-user token counts are modest (tens–low hundreds), the service fetches
  both, maps to a DTO, merges, and paginates in application code (documented tradeoff; a
  DB-level union/view can replace it if volumes ever grow).
- **DTO fields (metadata only, never the secret):** token id, token type
  (`PARTICIPATION` | `REPOSITORY`), course + exercise title, and a repository descriptor
  (base repo type; for `USER` the student's login/name; for a participation "your
  submission"). No token value, no created/last-used (per decision).
- **Columns rendered:** Course / Exercise · Repository · Revoke.

### 5.5 Revoke

- **Revoke endpoint** (new), e.g. `DELETE api/account/vcs-access-tokens/{type}/{id}`:
  - Loads the row, **verifies `user_id == currentUser`** (ownership) before deleting — a
    user can never revoke another user's token; returns 403 otherwise.
  - Deletes the row (works uniformly for participation and repository tokens).
- After revoke, the **next clone-dialog visit re-mints automatically** via the existing
  get-or-create client flow (code button `create…` on 404). This already holds today for
  the token types the clone dialog mints (participation and base-repo tokens); it extends to
  student-repo tokens once the clone-dialog wiring follow-up (§8) lands. No revoke-side change
  is needed either way.

## 6. Security & access-control risk analysis

| Risk | Mitigation |
| --- | --- |
| Cross-repository reuse | Auth lookup is `(userId, exact repositoryUri)` — a token works only for its one repo. |
| Privilege escalation | Impossible — authorization is re-derived from the live course role on every git op; the token grants nothing on its own. |
| Stale role (staff removed from course) | The git-time role check fails **immediately**, before async row cleanup runs — the token becomes inert. |
| Tutor writing to student work | Blocked by the role check (WRITE needs ≥ editor); a tutor's token is read-only in effect. |
| Leaked token | Blast radius = one repository, role-capped, revocable (delete row / staff-leave / user-delete). |
| Token farming (staff mints many) | On demand + get-or-create dedup; each token per-repo and role-capped; all auditable. |
| Minting for a repo the requester can't access | Blocked by ≥ TA guard **and** the explicit `checkAccessRepositoryElseThrow(READ)` check. |
| Listing / revoking another user's token | List and revoke are constrained to `user_id == currentUser`; revoke validates ownership before delete. |
| Secret exposure in the UI | The overview shows metadata only; the secret is only ever shown in the clone dialog, masked with reveal. |
| Auditability | Every git access is logged in `VcsAccessLog` (user, participation, `authenticationMechanism = REPOSITORY_VCS_ACCESS_TOKEN`, IP, commit hash, action). |

## 7. Testing

**Server (JUnit + Testcontainers):**
- Mint: ≥ TA can mint a `USER` token for a student repo in their course; a non-TA cannot;
  minting is rejected when the participation is not in the exercise or the requester lacks
  READ access; get-or-create returns the same token; unique-constraint race is handled.
- Git auth: cloning a student repo with the scoped token authenticates the staff user and is
  authorized read/write per role (tutor read-only, editor+ read/write); a `USER` token for
  repo A cannot authenticate repo B.
- Cleanup: token deleted on staff-leave, exercise-delete, user-delete, and
  **participation-delete (cascade)**.
- Overview/revoke: list returns only the caller's tokens (both types), never the secret;
  revoke deletes only an owned token and 403s on a foreign token; re-mint after revoke
  produces a new secret.

**Client (Vitest):**
- Overview table renders the current user's tokens (metadata only, no secret), paginates, and
  revoke calls the endpoint and refreshes.

## 8. Follow-ups & open items for the plan

**Primary follow-up (separate concept/PR):** wire the **clone dialog (code button)** to mint
and use a `USER` scoped token when a staff member clones a **student** repo, instead of the
broad personal token. This is what makes the capability user-facing end-to-end; it was
deliberately kept out of this concept. The server-side mint endpoint (§5.2) is designed so this
follow-up is a thin client change (call get-or-create with `participationId`, embed the
returned token) mirroring the base-repo path already in the code button.

**Open items for the implementation plan:**
- Exact endpoint placement/naming for list + revoke (`AccountResource` vs a dedicated
  `VcsAccessTokenResource`).
- DTO shape for the merged list and how course/exercise titles are resolved efficiently
  (avoid N+1).
- Whether the overview page replaces or sits beside the current `user-settings/vcs-token`
  page as the personal token is deprecated.
