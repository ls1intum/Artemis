package de.tum.cit.aet.artemis.account.service.user.deletion;

/**
 * The operation applied to a direct reference to a user during forced permanent deletion.
 */
public enum UserDeletionAction {
    DELETE, REMOVE_MEMBERSHIP, DETACH_ACTOR
}
