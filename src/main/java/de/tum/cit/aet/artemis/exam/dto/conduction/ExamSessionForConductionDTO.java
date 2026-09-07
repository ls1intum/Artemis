package de.tum.cit.aet.artemis.exam.dto.conduction;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExamSession;

/**
 * Projection of an {@link ExamSession} for the conduction payload.
 * <p>
 * Only the non-sensitive fields the client needs are carried: the {@code sessionToken} the client echoes back on
 * reconnect and the {@code initialSession} flag. The fingerprint / IP / user-agent details are already stripped from
 * the entity by {@link ExamSession#hideDetails()} before serialization, so they are simply not modelled here.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamSessionForConductionDTO(long id, String sessionToken, boolean initialSession, Instant createdDate) {

    /**
     * Converts an ExamSession into an ExamSessionForConductionDTO.
     *
     * @param examSession the exam session to convert
     * @return the converted DTO, or null if the exam session is null
     */
    public static ExamSessionForConductionDTO of(ExamSession examSession) {
        if (examSession == null) {
            return null;
        }
        return new ExamSessionForConductionDTO(examSession.getId(), examSession.getSessionToken(), examSession.isInitialSession(), examSession.getCreatedDate());
    }
}
