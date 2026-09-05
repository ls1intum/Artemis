package de.tum.cit.aet.artemis.account.repository.cleanup;

/**
 * How many rows of one kind a single account still owns.
 *
 * <p>
 * The deletion preview asks this for every reference and, when several accounts are previewed together, for all of
 * them at once. Counting them grouped by account keeps that to one query per reference rather than one per account.
 *
 * <p>
 * An interface projection rather than a record, because the membership tables have no entity of their own and have to
 * be counted with a native query, and a native query cannot call a constructor. One shape for both kinds keeps the
 * callers uniform.
 */
public interface UserReferenceCount {

    long getUserId();

    long getCount();
}
