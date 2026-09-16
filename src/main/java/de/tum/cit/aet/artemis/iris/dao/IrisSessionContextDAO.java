package de.tum.cit.aet.artemis.iris.dao;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

/**
 * The context a chat session currently points at, read as a projection rather than off the entity.
 *
 * <p>
 * A pessimistic lock does not refresh an instance the persistence context already manages, so a writer that locks a
 * session and then reads its mode off that instance can decide on state from before the lock. A projection is built
 * from the result set of its own query, which is what gets past that.
 *
 * <p>
 * That query is itself a locking read, so the values are current rather than merely outside the persistence context:
 * on MySQL a plain read would be answered from the snapshot the transaction opened, which for a writer that had read
 * the session before locking it still holds the context from before a concurrent switch.
 *
 * @param chatMode the mode the session is in
 * @param entityId the entity that mode points at
 * @param courseId the course the session belongs to
 */
public record IrisSessionContextDAO(IrisChatMode chatMode, long entityId, long courseId) {
}
