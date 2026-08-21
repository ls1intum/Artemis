Title: `General: Only require account activation where a user can actually activate their own account`

### Summary

An account is only created unactivated when its own owner can activate it, which requires an internal
account **and** self-registration to be enabled. Externally managed accounts created by the course
member import, exam registration and admin user import were created unactivated with an activation key
they had no way to redeem, which left them unable to authenticate against the paths that enforce the
flag.

The activation state is now derived from those two conditions, the `activated` field documents the
invariant, and a migration activates the affected accounts. Deliberate deactivations are untouched: the
update only applies where an activation key is still present, which never holds for an account an
administrator switched off.

Also in this PR:

- Deactivating and activating an account is written to the audit log with the acting administrator.
- `PUT users/initialize` no longer writes `activated`, so an account holder cannot reverse a
  deactivation. LTI initialisation now tracks whether the password dialog is still owed in its own
  `lti_initialized` column instead of borrowing `activated`.
- `GET login-options` is answered from local account state, gets its own rate-limit bucket, and is
  bounded by nginx; `GET activate` gains the application-level limit it was missing.
- The admin user management documentation is rewritten and a stale claim about `use-external`
  controlling LDAP is corrected.

### Motivation and Context

Whether an externally managed account ended up activated depended only on whether the user logged in
before an instructor imported them, because the import path and the first-login path disagreed. The
LDAP provider is the only authentication provider that does not check `activated`, which is why the
inconsistency stayed invisible until the git paths began enforcing it.

### Steps for Testing

1. With self-registration disabled, import a student into a course by registration number so the account
   is created from the directory. The account is created activated and can clone its repositories.
2. With self-registration enabled, register an account. It is created unactivated with an activation key
   and is activated by the link in the mail.
3. Deactivate a user as an administrator. Check the audit log for `DEACTIVATE_USER` naming you and the
   affected account, and confirm the account cannot sign in or use git.
4. Launch an exercise over LTI as a new user. The password dialog appears once; a second launch does not
   show it again.

### Server Test Coverage

Activation invariant, LTI initialisation, login options, rate-limit wiring and the audit entries are
covered; the affected suites total 300 tests.

### Review Progress

#### Code Review
- [ ] Code Review 1
- [ ] Code Review 2

#### Manual Tests
- [ ] Test 1
- [ ] Test 2
