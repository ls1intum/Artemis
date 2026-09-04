# User account deletion

Artemis distinguishes deactivation from permanent deletion.

## Deactivation

Deactivation prevents login and revokes passkeys, SSH keys, and VCS access tokens. It does not anonymize the account or
delete academic data. An administrator can reactivate the account, although revoked credentials must be enrolled again.

## Automatic deletion

The not-enrolled-user cleanup first applies the configured inactivity period, warning, and grace period. It physically
deletes an account only after all business-domain references have already been removed by their owning cleanup processes.
Remaining course roles, participations, exams, scores, complaints, plagiarism cases, posts, team memberships, tutorial
registrations, LTI launches, course requests, or actor references block deletion. Running the cleanup manually from the
administration page does not override this check.

Blocked accounts remain unchanged. Administrators should resolve the owning domain data or wait for the applicable
course, examination, plagiarism, or communication retention cleanup.

## Administrator-forced deletion

User management provides an impact preview with counts grouped by deletion, membership removal, and actor detachment.
After typed confirmation, an administrator may override retention:

- Data owned exclusively by the user is deleted.
- The user is removed from teams, conversations, courses, organizations, and tutorial groups.
- Shared team submissions and results remain for the other team members.
- Exercise and submission versions authored by the user are deleted. Assessor, reviewer, and similar references on
  shared records are detached without deleting another user's record.
- An unclassified foreign key aborts deletion rather than creating an incomplete deletion or a tombstone.

Administrators may delete ordinary users. Administrator and super-administrator accounts are never permanently deleted.
Self-deletion and deletion of protected system accounts are rejected.

## Legacy tombstones

Older Artemis versions anonymized a user and set the jhi_user.is_deleted field to true. The column and its query filters
remain temporarily so upgrades can retain referenced tombstones. New code must never set the flag. Automatic cleanup
physically purges a legacy tombstone once no business-domain reference remains. The column can be removed in a later
compatibility migration only after installations can no longer contain referenced tombstones.

The authoritative design and removal preconditions are tracked in
[feature proposal #13614](https://github.com/ls1intum/Artemis/issues/13614).
