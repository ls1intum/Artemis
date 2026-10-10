package de.tum.cit.aet.artemis.notification.service.notifications;

/**
 * How urgently a mail has to be delivered, which only matters while mails are waiting for another attempt.
 */
public enum MailPriority {

    /**
     * A mail that one person is waiting for or that guards their account: activation, password reset, security notifications.
     * It is tried again before any bulk mail and may take the place of a queued bulk mail when the queue is full.
     */
    TRANSACTIONAL,

    /**
     * A mail that goes to many people at once, such as the e-mail channel of a course notification. It must not delay a
     * transactional mail behind a backlog of thousands of announcements.
     */
    BULK
}
