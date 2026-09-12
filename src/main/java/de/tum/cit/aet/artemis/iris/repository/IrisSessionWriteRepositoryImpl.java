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
        // Marker append and context update under one session write lock. If the append were the only holder it would
        // release the lock on its own commit, and the context update would then cascade-merge a message list a
        // concurrent append may already have added to, which orphanRemoval would delete.
        if (!(sessions.findByIdWithWriteLockElseThrow(sessionId) instanceof IrisChatSession locked)) {
            throw new IllegalStateException("Context can only be switched on a chat session, but session " + sessionId + " is not one");
        }
        // Taking the lock does not refresh an entity the persistence context already manages, so without this the
        // decision could run on state read before the lock. Flush first so the refresh discards nothing pending.
        sessions.flush();
        sessions.refresh(locked);
        // Everything describing the transition comes from the locked session, not the caller's copy: a concurrent
        // switch that won the lock has already moved the session on.
        if (locked.getMode() == newMode && locked.getEntityId() == newEntityId) {
            return null;   // the other switch picked the same target; there is no transition left to record
        }
        if (expectedCourseId != locked.getCourseId()) {
            // Thrown from inside the boundary: the flush above already wrote what the caller had pending.
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
        // Stamp the exercise the message was decided for: the session's entityId moves with every context switch,
        // so it cannot tell an episode's rows apart. Episode lookups filter on this column.
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
            // The row failed one of the delete's guards, so it is still there and compacting would corrupt the list.
            return;
        }
        removedIndex.ifPresent(index -> irisMessageRepository.compactMessageOrderAfter(sessionId.get(), index));
    }

    // A plain call, so it joins whatever boundary is already open. It re-takes the session's write lock even when
    // the caller holds it: the lock is re-entrant for the holder, and the reload it guards is what makes the append
    // safe on its own for callers that take no outer lock.
    private IrisSession appendInCurrentTransaction(long sessionId, IrisMessage message, IrisMessageSender sender) {
        var sessions = irisSessionRepository.getObject();
        // Reload immediately before the cascade rather than trusting an already-initialized collection. saveAndFlush
        // merges the whole aggregate, so a stale messages list is written back over the committed rows: a column
        // another transaction set meanwhile is reset, and the appended message takes a list position a row missing
        // from the stale list already holds. Saving the message on its own is not an option either, because
        // @OrderColumn on IrisSession#messages is maintained from the owner side, so a standalone insert leaves
        // iris_message_order null and the next read fails with "Illegal null value for list index". Reloading alone
        // only narrows the window, so lock, reload, append and save happen in one transaction.
        var locked = sessions.findByIdWithWriteLockElseThrow(sessionId);
        // Flush first: the refresh overwrites the entity with database state and would otherwise discard changes the
        // caller has not flushed. Hibernate's auto-flush covers this today, but the ordering is too easy to break.
        sessions.flush();
        // Which re-read is needed depends on what the lock handed back. A managed instance is not refreshed by a
        // query, so the fetch join would return the same stale collection and the lock would protect nothing. A
        // session the lock loaded fresh carries no collection yet, and there the fetch join is the read.
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
