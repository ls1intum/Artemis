package de.tum.cit.aet.artemis.notification.service.notifications;

/**
 * What a failed attempt to hand a mail to the SMTP server says about trying again.
 */
public enum MailFailureKind {

    /**
     * Trying again cannot help: the server refused the message or an address, or the message could not be built.
     */
    PERMANENT,

    /**
     * The server accepts no more mail for the account for now, because a daily or hourly quota is used up. Every mail
     * fails the same way, so the node stops sending for a while instead of trying each mail.
     */
    QUOTA_EXCEEDED,

    /**
     * The server could not be reached or refused the credentials. Every mail fails the same way, as for the quota.
     */
    SERVER_UNAVAILABLE,

    /**
     * The server refused this message for now, or the failure is not understood. Trying the same mail again later may work.
     */
    TRANSIENT;

    /**
     * Whether the failure affects the whole connection to the SMTP server rather than the one mail.
     *
     * @return true if the node should stop sending for a while
     */
    public boolean pausesDelivery() {
        return this == QUOTA_EXCEEDED || this == SERVER_UNAVAILABLE;
    }
}
