package de.tum.in.www1.artemis.service.exam;

import java.security.SecureRandom;
import java.util.*;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.*;
import de.tum.in.www1.artemis.repository.ExamSessionRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.web.rest.dto.*;
import inet.ipaddr.IPAddress;

/**
 * Service Implementation for managing ExamSession.
 */
@Service
public class ExamSessionService {

    private final Logger log = LoggerFactory.getLogger(ExamSessionService.class);

    private final ExamSessionRepository examSessionRepository;

    private final StudentExamRepository studentExamRepository;

    public ExamSessionService(ExamSessionRepository examSessionRepository, StudentExamRepository studentExamRepository) {
        this.examSessionRepository = examSessionRepository;
        this.studentExamRepository = studentExamRepository;
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
     * An exam session is suspicious if it has the same browser fingerprint or ip address and belongs to a different student exam
     *
     * @param examId id of the exam for which suspicious exam sessions shall be retrieved
     * @return set of suspicious exam sessions
     */
    public Set<SuspiciousExamSessionsDTO> retrieveAllSuspiciousExamSessionsByExamId(long examId) {
        Set<SuspiciousExamSessions> suspiciousExamSessions = new HashSet<>();
        Set<ExamSession> examSessions = examSessionRepository.findAllExamSessionsByExamId(examId);
        examSessions = filterEqualExamSessionsForSameStudentExam(examSessions);
        // first step find all sessions that have matching browser fingerprint and ip address
        findSuspiciousSessionsForGivenCriteria(examSessions, examId, examSessionRepository::findAllExamSessionsWithTheSameIpAddressAndBrowserFingerprintByExamIdAndExamSession,
                suspiciousExamSessions);
        // second step find all sessions that have only matching browser fingerprint
        findSuspiciousSessionsForGivenCriteria(examSessions, examId, examSessionRepository::findAllExamSessionsWithTheSameBrowserFingerprintByExamIdAndExamSession,
                suspiciousExamSessions);
        // third step find all sessions that have only matching ip address
        findSuspiciousSessionsForGivenCriteria(examSessions, examId, examSessionRepository::findAllExamSessionsWithTheSameIpAddressByExamIdAndExamSession, suspiciousExamSessions);

        return convertSuspiciousSessionsToDTO(suspiciousExamSessions);
    }

    /**
     * Finds suspicious exam sessions according to the criteria given and adds them to the set of suspicious exam sessions
     *
     * @param examSessions           set of exam sessions to be processed
     * @param examId                 id of the exam for which suspicious exam sessions shall be retrieved
     * @param criteriaFilter         function that returns a set of exam sessions that match the given criteria
     * @param suspiciousExamSessions set of suspicious exam sessions to which the found suspicious exam sessions shall be added
     */
    private void findSuspiciousSessionsForGivenCriteria(Set<ExamSession> examSessions, long examId, BiFunction<Long, ExamSession, Set<ExamSession>> criteriaFilter,
            Set<SuspiciousExamSessions> suspiciousExamSessions) {

        for (var examSession : examSessions) {
            Set<ExamSession> relatedExamSessions = criteriaFilter.apply(examId, examSession);
            relatedExamSessions = filterEqualRelatedExamSessionsOfSameStudentExam(relatedExamSessions);

            if (!relatedExamSessions.isEmpty() && !isSubsetOfFoundSuspiciousSessions(relatedExamSessions, suspiciousExamSessions)) {
                addSuspiciousReasons(examSession, relatedExamSessions);
                relatedExamSessions.add(examSession);
                suspiciousExamSessions.add(new SuspiciousExamSessions(relatedExamSessions));
            }
        }
    }

    /**
     * Checks if the given set of exam sessions is a subset of suspicious exam sessions that have already been found.
     * This is necessary as we want to avoid duplicate results.
     * E.g. if we have exam session A,B,C and they are suspicious because of the same browser fingerprint AND the same IP address,
     * we do not want to include the same tuple of sessions again with only the reason same browser fingerprint or same IP address.
     *
     * @param relatedExamSessions    a set of exam sessions that are suspicious
     * @param suspiciousExamSessions a set of suspicious exam sessions that have already been found
     * @return true if the given set of exam sessions is a subset of suspicious exam sessions that have already been found, otherwise false.
     */
    private boolean isSubsetOfFoundSuspiciousSessions(Set<ExamSession> relatedExamSessions, Set<SuspiciousExamSessions> suspiciousExamSessions) {
        for (var suspiciousExamSession : suspiciousExamSessions) {
            if (suspiciousExamSession.examSessions().containsAll(relatedExamSessions)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Filters out exam sessions that have the same student exam id and the same browser fingerprint, ip address and user agent
     * This is necessary as the same student exam can have multiple exam sessions (e.g. if the student has to re-enter the exam)
     * As they are the same for parameters we compare, they only need to be included once and lead to duplicate results otherwise
     *
     * @param examSessions exam sessions to filter
     * @return filtered exam sessions
     */
    private Set<ExamSession> filterEqualExamSessionsForSameStudentExam(Set<ExamSession> examSessions) {
        Set<ExamSession> filteredSessions = new HashSet<>();
        Set<String> processedSessionKeys = new HashSet<>();

        for (ExamSession session : examSessions) {
            // calculating this key avoids using a second loop. We cannot rely on equals as the standard equals method inherited from DomainObject just takes the id into account
            // and overriding the equals method to only use the fields we are interested in leads to an unintuitive equals method we want to avoid
            String sessionKey = session.getBrowserFingerprintHash() + "_" + session.getIpAddress() + "_" + session.getUserAgent() + "_" + session.getStudentExam().getId();

            if (!processedSessionKeys.contains(sessionKey)) {
                filteredSessions.add(session);
                processedSessionKeys.add(sessionKey);
            }
        }
        return filteredSessions;
    }

    /**
     * Filters out exam sessions that have the same student exam id, only used if they are flagged as suspicious in comparison to another exam session
     *
     * @param examSessions exam sessions to filter
     * @return filtered exam sessions
     */
    private Set<ExamSession> filterEqualRelatedExamSessionsOfSameStudentExam(Set<ExamSession> examSessions) {
        Set<ExamSession> filteredSessions = new HashSet<>();
        Set<Long> processedSessionsStudentExamIds = new HashSet<>();

        for (ExamSession session : examSessions) {
            // calculating this key avoids using a second loop. We cannot rely on equals as the standard equals method inherited from DomainObject just takes the id into account
            // and overriding the equals method to only use the fields we are interested in leads to an unintuitive equals method we want to avoid
            long sessionKey = session.getStudentExam().getId();

            if (!processedSessionsStudentExamIds.contains(sessionKey)) {
                filteredSessions.add(session);
                processedSessionsStudentExamIds.add(sessionKey);
            }
        }
        return filteredSessions;
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
                examSessionDTOs.add(new ExamSessionDTO(examSession.getId(), examSession.getBrowserFingerprintHash(), examSession.getIpAddress(), examSession.getSuspiciousReasons(),
                        examSession.getCreatedDate(), studentExamDTO));
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

        }
    }

}
