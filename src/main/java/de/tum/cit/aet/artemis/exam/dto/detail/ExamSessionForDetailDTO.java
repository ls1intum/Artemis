package de.tum.cit.aet.artemis.exam.dto.detail;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExamSession;

/**
 * Projection of an {@link ExamSession} as nested inside the instructor-facing student-exam detail payload.
 * <p>
 * Unlike the student-facing conduction projection (whose sessions are {@code hideDetails()}-masked, so only the token
 * and the initial-session flag survive), the instructor detail renders a proctoring table with the full session
 * fingerprint: user agent, browser fingerprint hash, instance id and IP address. These are therefore preserved here
 * and merely dropped (NON_EMPTY) when null.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamSessionForDetailDTO(long id, String sessionToken, String userAgent, String browserFingerprintHash, String instanceId, String ipAddress, boolean initialSession,
        Instant createdDate) {

    /**
     * Converts an ExamSession into an ExamSessionForDetailDTO.
     *
     * @param examSession the exam session to convert
     * @return the converted DTO, or null if the exam session is null
     */
    public static ExamSessionForDetailDTO of(ExamSession examSession) {
        if (examSession == null) {
            return null;
        }
        return new ExamSessionForDetailDTO(examSession.getId(), examSession.getSessionToken(), examSession.getUserAgent(), examSession.getBrowserFingerprintHash(),
                examSession.getInstanceId(), examSession.getIpAddress(), examSession.isInitialSession(), examSession.getCreatedDate());
    }
}
