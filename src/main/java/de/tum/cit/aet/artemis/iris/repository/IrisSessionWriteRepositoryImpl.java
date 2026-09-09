package de.tum.cit.aet.artemis.iris.repository;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.domain.message.IrisContextSwitchMarker;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;

/**
 * Implementation of {@link IrisSessionWriteRepository}.
 *
 * <p>
 * The containing repository is injected through an {@link ObjectProvider} and resolved per call. A fragment cannot
 * declare a constructor dependency on the repository it is composed into - that is a cycle, and the application runs
 * with {@code spring.main.allow-circular-references: false} - but it still has to go through the repository proxy
 * rather than around it, both to reuse the queries declared on {@link IrisSessionRepository} statement for statement
 * and to keep the transaction interceptor in the call path.
 */
public class IrisSessionWriteRepositoryImpl implements IrisSessionWriteRepository {

    private static final Logger log = LoggerFactory.getLogger(IrisSessionWriteRepositoryImpl.class);

    private final ObjectProvider<IrisSessionRepository> irisSessionRepository;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final IrisMessageRepository irisMessageRepository;

    public IrisSessionWriteRepositoryImpl(ObjectProvider<IrisSessionRepository> irisSessionRepository, IrisChatSessionRepository irisChatSessionRepository,
            IrisMessageRepository irisMessageRepository) {
        this.irisSessionRepository = irisSessionRepository;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.irisMessageRepository = irisMessageRepository;
    }

    @Override
    public IrisSession appendMessage(long sessionId, IrisMessage message, IrisMessageSender sender) {
        return appendInCurrentTransaction(sessionId, message, sender);
    }

    @Override
    public @Nullable IrisMessage switchContextAndAppendMarker(long sessionId, IrisChatMode newMode, long newEntityId, long expectedCourseId, String entityName) {
        var sessions = irisSessionRepository.getObject();
        // Append the marker and update the context fields under ONE session write lock. The append below takes that
        // lock itself, but if it were the only holder it would release it on its own commit, and the context update
        // that follows would then cascade-merge a message list a concurrent append may already have added to.
        // orphanRemoval would delete that new row.
        if (!(sessions.findByIdWithWriteLockElseThrow(sessionId) instanceof IrisChatSession locked)) {
            throw new IllegalStateException("Context can only be switched on a chat session, but session " + sessionId + " is not one");
        }
        // Taking the lock does not refresh an entity the persistence context already manages, and a caller can hand us
        // one it loaded earlier. Without the refresh the state we decide on could be the one that was read before the
        // lock existed. Flush first so the refresh cannot discard changes a caller made but has not written yet.
        sessions.flush();
        sessions.refresh(locked);
        // Everything describing the transition comes from the LOCKED session, not from the caller's copy. A concurrent
        // switch that took the lock first has already moved the session on, so a decision made before the lock would
        // either record a previous mode that never was, or append a second marker for a switch that already happened.
        if (locked.getMode() == newMode && locked.getEntityId() == newEntityId) {
            return null;   // the other switch picked the same target; there is no transition left to record
        }
        if (expectedCourseId != locked.getCourseId()) {
            // Thrown from inside the boundary on purpose: the flush above has already written whatever the caller had
            // pending, so refusing after this method returned would commit it.
            throw new ConflictException("New context must belong to the same course as the session", "Iris", "irisCourseMismatch");
        }

        var marker = IrisContextSwitchMarker.forSwitch(locked.getMode(), newMode, newEntityId, entityName);
        IrisMessage markerMessage = new IrisMessage();
        markerMessage.addContent(new IrisJsonMessageContent(JsonObjectMapper.get().valueToTree(marker)));
        IrisMessage saved = appendInCurrentTransaction(locked.getId(), markerMessage, IrisMessageSender.CTXSWAP).getMessages().getLast();

        locked.setMode(newMode);
        locked.setEntityId(newEntityId);
        irisChatSessionRepository.save(locked);
        return saved;
    }

    @Override
    public @Nullable IrisMessage appendProactiveMessage(long sessionId, long exerciseId, String text, @Nullable String episodeId) {
        var sessions = irisSessionRepository.getObject();
        if (!(sessions.findByIdWithWriteLockElseThrow(sessionId) instanceof IrisChatSession locked)) {
            return null;
        }
        sessions.flush();
        sessions.refresh(locked);
        if (locked.getMode() != IrisChatMode.PROGRAMMING_EXERCISE_CHAT || !Objects.equals(locked.getEntityId(), exerciseId)) {
            log.info("Dropping proactive message: session {} moved to mode={} entity={} before the append for exercise {}", locked.getId(), locked.getMode(), locked.getEntityId(),
                    exerciseId);
            return null;
        }

        var message = new IrisMessage();
        message.addContent(new IrisTextMessageContent(text));
        message.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        // Stamp the exercise the message was decided for, not the one the session happens to point at later: the
        // session's entityId moves with every context switch, so it cannot tell an episode's rows apart once the
        // student navigates away. Episode lookups filter on this column.
        message.setProactiveExerciseId(exerciseId);
        if (episodeId != null) {
            message.setProactiveEpisodeId(episodeId);
        }
        return appendInCurrentTransaction(locked.getId(), message, IrisMessageSender.LLM).getMessages().getLast();
    }

    @Override
    public void deleteSupersededProactiveMessageAndCompact(long messageId, long userId) {
        var sessionId = irisMessageRepository.findOwnedSessionId(messageId, userId);
        if (sessionId.isEmpty()) {
            // Missing row, or another user's: nothing to delete and nothing to lock. Silent noop.
            return;
        }
        irisSessionRepository.getObject().findByIdWithWriteLockElseThrow(sessionId.get());
        // Read the index BEFORE the row is gone; after the delete there is nothing left to read it from.
        var removedIndex = irisMessageRepository.findListIndex(messageId);
        int deleted = irisMessageRepository.deleteSupersededProactiveMessage(messageId, userId);
        if (deleted == 0) {
            // The row failed one of the delete's own guards (wrong origin, or an outcome landed on it): it is still
            // there, its index is still valid, and compacting would corrupt the list.
            return;
        }
        removedIndex.ifPresent(index -> irisMessageRepository.compactMessageOrderAfter(sessionId.get(), index));
    }

    /**
     * The append itself, as a plain call so it joins whatever boundary is already open rather than opening one of its
     * own. Every caller above is already inside this fragment's transaction.
     *
     * <p>
     * It re-takes the session's write lock even when the caller holds it, which is what the nested transaction did
     * before this moved out of the services. That is deliberate: the lock is re-entrant for the holder, and the
     * reload it guards is what makes the append safe on its own for callers that take no outer lock at all.
     *
     * @param sessionId the session to append to
     * @param message   the message to append
     * @param sender    the sender to stamp on the message
     * @return the saved session
     */
    private IrisSession appendInCurrentTransaction(long sessionId, IrisMessage message, IrisMessageSender sender) {
        var sessions = irisSessionRepository.getObject();
        // Reload immediately before the cascade, rather than trusting an already-initialized collection. The
        // saveAndFlush below merges the whole session aggregate, so a stale messages list is written back over the
        // committed rows: a column another transaction has set in the meantime (proactiveOutcome) is reset to its
        // stale value, and the appended message is given the list position that a row missing from the stale list
        // already holds, so the collection comes back with two entries on one index. Hibernate does not delete the
        // missing row itself - orphan detection walks the collection's loaded snapshot, which never held it - but a
        // list that cannot be read back by position is just as lost. Saving the message on its own instead is not an
        // option: @OrderColumn on IrisSession#messages is maintained from the owner side, so a standalone insert
        // leaves iris_message_order null and the next read fails with "Illegal null value for list index".
        //
        // Reloading alone only narrows that window, it does not close it: the reload and the merge would be two
        // separate unlocked operations, so a concurrent append (a normal chat message racing a proactive callback or
        // an ambient reveal) can commit in between and then be deleted by the other writer's stale collection. Take a
        // write lock on the session row FIRST and do the reload, the append and the save in ONE transaction, so
        // concurrent appends to the same session serialize instead of overwriting each other.
        var locked = sessions.findByIdWithWriteLockElseThrow(sessionId);
        // Flush first: the refresh below overwrites the entity with database state and would otherwise DISCARD
        // changes the caller has made but not yet flushed - a context switch sets mode and entityId on the session
        // right before appending the context-switch marker. Hibernate's auto-flush before the lock query above
        // happens to cover this today, but the ordering is too easy to break to leave implicit.
        sessions.flush();
        // Which re-read is needed depends on what the lock handed back. A caller that already holds this session in
        // the persistence context gets that very instance back, and a query does NOT refresh a managed entity: the
        // fetch join would hand back the same stale collection and the lock would protect nothing, so refresh it
        // explicitly. A session the lock loaded fresh carries no collection yet, and the fetch join IS the read -
        // refreshing it as well would only cascade over every message and its eagerly fetched content.
        IrisSession lockedWithMessages;
        if (Hibernate.isInitialized(locked.getMessages())) {
            sessions.refresh(locked);
            lockedWithMessages = locked;
        }
        else {
            lockedWithMessages = sessions.findByIdWithMessagesElseThrow(locked.getId());
        }

        message.setSender(sender);
        message.setSentAt(ZonedDateTime.now());
        message.setSession(lockedWithMessages);
        message.getContent().forEach(content -> content.setMessage(message));

        lockedWithMessages.getMessages().add(message);
        // saveAndFlush so the cascaded message has its generated id.
        return sessions.saveAndFlush(lockedWithMessages);
    }
}
