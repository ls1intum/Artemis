package de.tum.cit.aet.artemis.iris.service;

import java.time.ZonedDateTime;

import jakarta.ws.rs.BadRequestException;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;

/**
 * Service for managing Iris messages.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisMessageService {

    private final IrisSessionRepository irisSessionRepository;

    private final TransactionTemplate transactionTemplate;

    public IrisMessageService(IrisSessionRepository irisSessionRepository, PlatformTransactionManager transactionManager) {
        this.irisSessionRepository = irisSessionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Saves a new message to the database. The method sets session and a sender to the message.
     * This method ensures that the message and the contents are saved to the session.
     *
     * @param message The message to save
     * @param session The session the message belongs to
     * @param sender  The sender of the message
     * @return The saved message
     */
    public IrisMessage saveMessage(IrisMessage message, IrisSession session, IrisMessageSender sender) {
        if (message.getContent().isEmpty()) {
            throw new BadRequestException("Message must have at least one content element");
        }

        // Reload immediately before the cascade, rather than trusting an already-initialized collection.
        // saveAndFlush below merges the whole session aggregate, so a stale messages list is written back over the
        // committed rows: a column another transaction has set in the meantime (proactiveOutcome) is reset to its
        // stale value, and because the collection declares orphanRemoval, a row missing from the stale list is
        // deleted outright. Saving the message on its own instead is not an option: @OrderColumn on
        // IrisSession#messages is maintained from the owner side, so a standalone insert leaves iris_message_order
        // null and the next read fails with "Illegal null value for list index".
        //
        // Reloading alone only narrows that window, it does not close it: the reload and the merge would be two
        // separate unlocked operations, so a concurrent append (a normal chat message racing a proactive callback or
        // an ambient reveal) can commit in between and then be deleted by the other writer's stale collection. Take a
        // write lock on the session row FIRST and do the reload, the append and the save in ONE transaction, so
        // concurrent appends to the same session serialize instead of overwriting each other.
        var callerSession = session;
        var savedSession = transactionTemplate.execute(status -> {
            var locked = irisSessionRepository.findByIdWithWriteLockElseThrow(callerSession.getId());
            // A query does NOT refresh an entity the persistence context already manages, and callers do reach this
            // method with the session already loaded inside the same transaction (revealAmbient resolves the session,
            // which can run applyContextChange and initialize the collection, before appending). Re-running the fetch
            // join would then hand back that same stale collection and the lock would protect nothing. Refresh
            // explicitly, under the lock, so the messages really are re-read from the database.
            //
            // Flush first: refresh overwrites the entity with database state and would otherwise DISCARD changes the
            // caller has made but not yet flushed - applyContextChange sets mode and entityId on the session right
            // before appending the context-switch marker. Hibernate's auto-flush before the lock query above happens
            // to cover this today, but the ordering is too easy to break to leave implicit.
            irisSessionRepository.flush();
            irisSessionRepository.refresh(locked);
            var lockedWithMessages = irisSessionRepository.findByIdWithMessagesElseThrow(locked.getId());

            message.setSender(sender);
            message.setSentAt(ZonedDateTime.now());
            message.setSession(lockedWithMessages);
            message.getContent().forEach(content -> content.setMessage(message));

            lockedWithMessages.getMessages().add(message);
            // saveAndFlush so the cascaded message has its generated id.
            return irisSessionRepository.saveAndFlush(lockedWithMessages);
        });
        if (savedSession == null) {
            // TransactionTemplate.execute is declared nullable; the callback above always returns the saved session,
            // so this only guards the impossible case rather than letting a null escape into the callers.
            throw new IllegalStateException("Saving the Iris message returned no session for session " + callerSession.getId());
        }
        if (Hibernate.isInitialized(callerSession.getMessages())) {
            // Keep the caller's own instance consistent, as before; an uninitialized one is left alone so it
            // still loads the committed state lazily.
            callerSession.setMessages(savedSession.getMessages());
        }

        return savedSession.getMessages().getLast();
    }
}
