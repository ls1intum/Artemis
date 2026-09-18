package de.tum.cit.aet.artemis.iris.repository;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

/**
 * The multi-statement writes against a chat session's ordered message list. A custom fragment of
 * {@link IrisSessionRepository}, so these transaction boundaries live in a repository rather than a service.
 *
 * <p>
 * Every method here is only correct as a unit: {@code iris_message_order} is allocated as one past the highest index
 * a session's rows hold, so reading that index, inserting the row and writing its own have to share
 * {@link IrisSessionRepository#findByIdWithWriteLock}, or two writers claim the same position. None of them goes
 * through the collection that owns the column, which would merge the session aggregate and let {@code orphanRemoval}
 * delete the rows the merged list never saw.
 */
@Lazy
@Repository
@Conditional(IrisEnabled.class)
public interface IrisSessionWriteRepository {

    /**
     * Append a message to the session's ordered message list under the session's write lock.
     *
     * @param sessionId the session to append to
     * @param message   the message to append; its sender, timestamp, session and content back-references are set here
     * @param sender    the sender to stamp on the message
     * @return the saved message, deliberately not the session: the caller's own instance is left untouched
     */
    @Transactional // ok: the lock, the index allocation and the insert are only correct as one unit
    IrisMessage appendMessage(long sessionId, IrisMessage message, IrisMessageSender sender);

    /**
     * Move the session to a new context and append the CTXSWAP marker recording the transition, under one write lock.
     * The marker is built here rather than by the caller because it records the mode the session is moving away from,
     * which is only known from the projection read under the lock.
     *
     * @param sessionId        the session to switch
     * @param newMode          the mode to move to
     * @param newEntityId      the entity the new mode points at
     * @param expectedCourseId the course the caller resolved and authorized the new context against
     * @param entityName       the display name of the new entity, for the marker
     * @return the persisted marker message, or {@code null} when the session already carries the target context under
     *         the lock and there is no transition left to record
     */
    @Transactional // ok: the marker append and the context update must not be separable
    @Nullable
    IrisMessage switchContextAndAppendMarker(long sessionId, IrisChatMode newMode, long newEntityId, long expectedCourseId, String entityName);

    /**
     * Append a proactive struggle message, re-checking the session's exercise binding under the write lock
     * immediately before the append. The re-check is not redundant with whatever the caller validated: a run for
     * another exercise can move this same session between the caller's resolution and this write.
     *
     * @param sessionId  the resolved exercise-chat session
     * @param exerciseId the exercise the message was decided for
     * @param text       the proactive message text
     * @param episodeId  the client-allocated episode UUID to stamp on the row, or {@code null}
     * @return the saved message, or {@code null} when the session is not (or no longer) bound to {@code exerciseId}
     */
    @Transactional // ok: the binding re-check is only worth anything while the append shares its lock
    @Nullable
    IrisMessage appendProactiveMessage(long sessionId, long exerciseId, String text, @Nullable String episodeId);

    /**
     * Delete a superseded proactive message and close the gap it leaves in the list indices. The guarded delete
     * never goes through the collection that owns {@code iris_message_order}, so read, delete and compact share the
     * session lock.
     *
     * @param messageId the message to delete
     * @param userId    the requesting user; only rows in this user's own sessions are touched
     */
    @Transactional // ok: deleting without compacting in the same unit leaves a null hole in the list
    void deleteSupersededProactiveMessageAndCompact(long messageId, long userId);
}
