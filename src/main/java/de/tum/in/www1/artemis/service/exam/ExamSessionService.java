package de.tum.in.www1.artemis.service.exam;

import java.security.SecureRandom;
import java.util.*;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.*;
import de.tum.in.www1.artemis.repository.ExamSessionRepository;
import de.tum.in.www1.artemis.web.rest.dto.*;
import inet.ipaddr.IPAddress;

/**
 * Service Implementation for managing ExamSession.
 */
@Service
public class ExamSessionService {

    private final Logger log = LoggerFactory.getLogger(ExamSessionService.class);

    private final ExamSessionRepository examSessionRepository;

    public ExamSessionService(ExamSessionRepository examSessionRepository) {
        this.examSessionRepository = examSessionRepository;
    }

    /**
     * Creates and saves an exam session for given student exam
     *
     * @param studentExam student exam for which an exam session shall be created
     * @param fingerprint the browser fingerprint reported by the client, can be null
     * @param userAgent   the user agent of the client, can be null
     * @param instanceId  the instance id of the client, can be null
     * @param ipAddress   the ip address of the client, can be null
     * @return the newly create exam session
     */
    public ExamSession startExamSession(StudentExam studentExam, @Nullable String fingerprint, @Nullable String userAgent, @Nullable String instanceId,
            @Nullable IPAddress ipAddress) {
        log.debug("Exam session started");
        String sessionToken = generateSafeToken();
        ExamSession examSession = new ExamSession();
        examSession.setSessionToken(sessionToken);
        examSession.setStudentExam(studentExam);
        examSession.setBrowserFingerprintHash(fingerprint);
        examSession.setUserAgent(userAgent);
        examSession.setInstanceId(instanceId);
        examSession.setIpAddressFromIpAddress(ipAddress);
        examSession = examSessionRepository.save(examSession);
        return examSession;
    }

    private String generateSafeToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String token = encoder.encodeToString(bytes);
        return token.substring(0, 16);
    }

    /**
     * Checks the number of exam sessions for student exam and decides if the current session is initial or not
     *
     * @param studentExamId student exam id which is associated with exam session
     * @return true if there is only one exam session for this student exam in the database, otherwise returns false
     */
    public boolean checkExamSessionIsInitial(Long studentExamId) {
        long examSessionCount = examSessionRepository.findExamSessionCountByStudentExamId(studentExamId);
        return (examSessionCount == 1);
    }

    /**
     * Retrieves all suspicious exam sessions for given exam id
     * For a more detailed explanation see {@link ExamSessionRepository#findAllSuspiciousExamSessionsByExamIdAndExamSession(long, ExamSession)}
     *
     * @param examId id of the exam for which suspicious exam sessions shall be retrieved
     * @return set of suspicious exam sessions
     */
    public Set<SuspiciousExamSessionsDTO> retrieveAllSuspiciousExamSessionsByExamId(long examId) {
        Set<SuspiciousExamSessions> suspiciousExamSessions = new HashSet<>();
        Set<ExamSession> examSessions = examSessionRepository.findAllExamSessionsByExamId(examId);

        for (var examSession : examSessions) {
            boolean alreadyContained = false;
            for (var suspiciousExamSession : suspiciousExamSessions) {
                if (suspiciousExamSession.examSessions().contains(examSession)) {
                    alreadyContained = true;
                    break;
                }
            }
            if (alreadyContained) {
                continue;
            }
            Set<ExamSession> relatedExamSessions = examSessionRepository.findAllSuspiciousExamSessionsByExamIdAndExamSession(examId, examSession);
            if (!relatedExamSessions.isEmpty()) {
                addSuspiciousReasons(examSession, relatedExamSessions);
                relatedExamSessions.add(examSession);
                suspiciousExamSessions.add(new SuspiciousExamSessions(relatedExamSessions));
            }
        }

        return convertSuspiciousSessionsToDTO(suspiciousExamSessions);
    }

    private Set<SuspiciousExamSessionsDTO> convertSuspiciousSessionsToDTO(Set<SuspiciousExamSessions> suspiciousExamSessions) {
        Set<SuspiciousExamSessionsDTO> suspiciousExamSessionsDTO = new HashSet<>();
        for (var suspiciousExamSession : suspiciousExamSessions) {
            Set<ExamSessionDTO> examSessionDTOs = new HashSet<>();
            for (var examSession : suspiciousExamSession.examSessions()) {
                var userDTO = new UserWithIdAndLoginDTO(examSession.getStudentExam().getUser().getId(), examSession.getStudentExam().getUser().getLogin());
                var courseDTO = new CourseWithIdDTO(examSession.getStudentExam().getExam().getCourse().getId());
                var examDTO = new ExamWithIdAndCourseDTO(examSession.getStudentExam().getExam().getId(), courseDTO);
                var studentExamDTO = new StudentExamWithIdAndExamAndUserDTO(examSession.getStudentExam().getId(), examDTO, userDTO);
                examSessionDTOs.add(new ExamSessionDTO(examSession.getId(), examSession.getSessionToken(), examSession.getBrowserFingerprintHash(), examSession.getUserAgent(),
                        examSession.getInstanceId(), examSession.getIpAddress(), examSession.getSuspiciousReasons(), examSession.getCreatedDate(), studentExamDTO));
            }
            suspiciousExamSessionsDTO.add(new SuspiciousExamSessionsDTO(examSessionDTOs));
        }
        return suspiciousExamSessionsDTO;
    }

    /**
     * Adds suspicious reasons to exam session we compare with and the related exam sessions.
     * We already know that the exam sessions are suspicious, but we still have to determine what's the reason for that.
     *
     * @param session             exam session we compare with
     * @param relatedExamSessions related exam sessions
     */
    private void addSuspiciousReasons(ExamSession session, Set<ExamSession> relatedExamSessions) {
        for (var relatedExamSession : relatedExamSessions) {
            if (relatedExamSession.hasSameBrowserFingerprint(session)) {
                relatedExamSession.addSuspiciousReason(SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT);
                session.addSuspiciousReason(SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT);
            }
            if (relatedExamSession.hasSameIpAddress(session)) {
                relatedExamSession.addSuspiciousReason(SuspiciousSessionReason.SAME_IP_ADDRESS);
                session.addSuspiciousReason(SuspiciousSessionReason.SAME_IP_ADDRESS);
            }
            if (relatedExamSession.hasSameUserAgent(session)) {
                relatedExamSession.addSuspiciousReason(SuspiciousSessionReason.SAME_USER_AGENT);
                session.addSuspiciousReason(SuspiciousSessionReason.SAME_USER_AGENT);
            }
        }
    }

}
