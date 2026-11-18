package de.tum.cit.aet.artemis.iris.dao;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

/**
 * The context a chat session points at, as read by {@code IrisSessionRepository#findContextById}.
 *
 * @param chatMode the mode the session is in
 * @param entityId the entity that mode points at
 * @param courseId the course the session belongs to
 */
public record IrisSessionContextDAO(IrisChatMode chatMode, long entityId, long courseId) {
}
