package de.tum.cit.aet.artemis.videosource.domain.event;

/**
 * Published right before Artemis revokes a course's TUM.Live grant, while the grant still works. Listeners run synchronously in the revoking request and must
 * not throw: what they do with the grant is best effort and never stops the revocation.
 *
 * @param courseId      the Artemis course that is being disconnected
 * @param integrationId the TUM.Live integration
 * @param grantId       the grant about to be revoked
 */
public record GocastGrantRevokingEvent(long courseId, long integrationId, long grantId) {
}
