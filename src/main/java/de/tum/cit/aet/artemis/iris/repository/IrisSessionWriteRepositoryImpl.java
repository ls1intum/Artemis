package de.tum.cit.aet.artemis.iris.repository;

import java.time.ZonedDateTime;

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
 *
 * <p>
 * Nothing here merges the session aggregate, so a message list the persistence context holds in a stale state is
 * never written back and {@code orphanRemoval} cannot delete a row it does not know about. The session write lock
 * remains, because {@code iris_message_order} is allocated as one past the current highest.
 */
public class IrisSessionWriteRepositoryImpl implements IrisSessionWriteRepository {

    private static final Logger log = LoggerFactory.getLogger(IrisSessionWriteRepositoryImpl.class);

    private final ObjectProvider<IrisSessionRepository> irisSessionRepository;

    private final IrisMessageRepository irisMessageRepository;

    public IrisSessionWriteRepositoryImpl(ObjectProvider<IrisSessionRepository> irisSessionRepository, IrisMessageRepository irisMessageRepository) {
        this.irisSessionRepository = irisSessionRepository;
        this.irisMessageRepository = irisMessageRepository;
    }

    @Override
    public IrisMessage appendMessage(long sessionId, IrisMessage message, IrisMessageSender sender) {
        return appendInCurrentTransaction(sessionId, message, sender);
    }

    @Override
    public @Nullable IrisMessage switchContextAndAppendMarker(long sessionId, IrisChatMode newMode, long newEntityId, long expectedCourseId, String entityName) {
        var sessions = irisSessionRepository.getObject();
        // Marker append and context update under one session write lock. If the append were the only holder it would
        // release the lock on its own commit, and the context update would then run against a session a concurrent
        // switch may already have moved on.
        sessions.findByIdWithWriteLockElseThrow(sessionId);
        // Flush first, so the read below sees what the caller has pending.
        sessions.flush();
        // The transition is described by what the lock found, never by the caller's copy.
        var context = sessions.findContextById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Context can only be switched on a chat session, but session " + sessionId + " is not one"));
        if (context.chatMode() == newMode && context.entityId() == newEntityId) {
            return null;   // the other switch picked the same target; there is no transition left to record
        }
        if (expectedCourseId != context.courseId()) {
            // Thrown from inside the boundary: the flush above already wrote what the caller had pending.
            throw new ConflictException("New context must belong to the same course as the session", "Iris", "irisCourseMismatch");
        }

        var marker = IrisContextSwitchMarker.forSwitch(context.chatMode(), newMode, newEntityId, entityName);
        IrisMessage markerMessage = new IrisMessage();
        markerMessage.addContent(new IrisJsonMessageContent(JsonObjectMapper.get().valueToTree(marker)));
        IrisMessage saved = appendInCurrentTransaction(sessionId, markerMessage, IrisMessageSender.CTXSWAP);

        int updated = sessions.updateContext(sessionId, newMode, newEntityId);
        if (updated != 1) {
            throw new IllegalStateException("Context switch did not update session " + sessionId + ", " + updated + " rows changed");
        }
        return saved;
    }

    @Override
    public @Nullable IrisMessage appendProactiveMessage(long sessionId, long exerciseId, String text, @Nullable String episodeId) {
        var sessions = irisSessionRepository.getObject();
        sessions.findByIdWithWriteLockElseThrow(sessionId);
        sessions.flush();
        var context = sessions.findContextById(sessionId).orElse(null);
        if (context == null) {
            log.info("Dropping proactive message: session {} is not a chat session, and the append was for exercise {}", sessionId, exerciseId);
            return null;
        }
        if (context.chatMode() != IrisChatMode.PROGRAMMING_EXERCISE_CHAT || context.entityId() != exerciseId) {
            log.info("Dropping proactive message: session {} is at mode={} entity={} before the append for exercise {}", sessionId, context.chatMode(), context.entityId(),
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
        return appendInCurrentTransaction(sessionId, message, IrisMessageSender.LLM);
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

    // A plain call, so it joins whatever boundary is already open. It re-takes the session's write lock even when the
    // caller holds it: the lock is re-entrant for the holder, and it is what makes the index allocation below safe on
    // its own for callers that take no outer lock.
    private IrisMessage appendInCurrentTransaction(long sessionId, IrisMessage message, IrisMessageSender sender) {
        var sessions = irisSessionRepository.getObject();
        IrisSession locked = sessions.findByIdWithWriteLockElseThrow(sessionId);
        // Hibernate does not auto-flush for the native query below, which names a table rather than an entity, so a
        // row the caller inserted would not count towards the highest index.
        sessions.flush();
        int listIndex = irisMessageRepository.findHighestListIndexForUpdate(sessionId).map(highest -> highest + 1).orElse(0);

        message.setSender(sender);
        message.setSentAt(ZonedDateTime.now());
        message.setSession(locked);
        message.getContent().forEach(content -> content.setMessage(message));

        // Through the message side rather than through session.getMessages(), which would merge the whole aggregate
        // back. saveAndFlush, because the row has to exist before the index update names its id.
        IrisMessage saved = irisMessageRepository.saveAndFlush(message);
        int indexed = irisMessageRepository.setListIndex(saved.getId(), listIndex);
        if (indexed != 1) {
            // A null index fails the next read of the list, so the whole append goes back.
            throw new IllegalStateException("Appended message " + saved.getId() + " of session " + sessionId + " did not receive a list index");
        }
        return saved;
    }
}
