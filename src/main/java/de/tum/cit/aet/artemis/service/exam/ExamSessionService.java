package de.tum.cit.aet.artemis.service.exam;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.exam.ExamSession;
import de.tum.cit.aet.artemis.domain.exam.StudentExam;
import de.tum.cit.aet.artemis.domain.exam.SuspiciousExamSessions;
import de.tum.cit.aet.artemis.domain.exam.SuspiciousSessionReason;
import de.tum.cit.aet.artemis.domain.exam.SuspiciousSessionsAnalysisOptions;
import de.tum.cit.aet.artemis.repository.ExamSessionRepository;
import de.tum.cit.aet.artemis.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.web.rest.dto.CourseWithIdDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExamSessionDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExamWithIdAndCourseDTO;
import de.tum.cit.aet.artemis.web.rest.dto.StudentExamWithIdAndExamAndUserDTO;
import de.tum.cit.aet.artemis.web.rest.dto.SuspiciousExamSessionsDTO;
import de.tum.cit.aet.artemis.web.rest.dto.UserWithIdAndLoginDTO;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

/**
 * Service Implementation for managing ExamSession.
 */
@Profile(PROFILE_CORE)
@Service
public class ExamSessionService {

    private static final Logger log = LoggerFactory.getLogger(ExamSessionService.class);

    private final ExamSessionRepository examSessionRepository;

    private final StudentExamRepository studentExamRepository;

    private final BiFunction<Long, ExamSession, Set<ExamSession>> filterSessionsForIpAndFingerprint;

    private final BiFunction<Long, ExamSession, Set<ExamSession>> filterSessionsForIp;

    private final BiFunction<Long, ExamSession, Set<ExamSession>> filterSessionsForFingerprint;

    public ExamSessionService(ExamSessionRepository examSessionRepository, StudentExamRepository studentExamRepository) {
        this.examSessionRepository = examSessionRepository;
        this.studentExamRepository = studentExamRepository;

        this.filterSessionsForIpAndFingerprint = (Long internalExamId, ExamSession examSession) -> examSessionRepository
                .findAllExamSessionsWithTheSameIpAddressAndBrowserFingerprintByExamIdAndExamSession(internalExamId, examSession.getId(), examSession.getStudentExam().getId(),
                        examSession.getIpAddress(), examSession.getBrowserFingerprintHash());
        this.filterSessionsForIp = (Long internalExamId, ExamSession examSession) -> examSessionRepository
                .findAllExamSessionsWithTheSameIpAddressAndBrowserFingerprintByExamIdAndExamSession(internalExamId, examSession.getId(), examSession.getStudentExam().getId(),
                        examSession.getIpAddress(), null);
        this.filterSessionsForFingerprint = (Long internalExamId, ExamSession examSession) -> examSessionRepository
                .findAllExamSessionsWithTheSameIpAddressAndBrowserFingerprintByExamIdAndExamSession(internalExamId, examSession.getId(), examSession.getStudentExam().getId(), null,
                        examSession.getBrowserFingerprintHash());
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
     * For a detailed description of the criteria, see {@link SuspiciousSessionsAnalysisOptions}
     *
     * @param examId          id of the exam for which suspicious exam sessions shall be retrieved
     * @param analysisOptions options for the analysis of suspicious sessions
     * @param ipSubnet        subnet for the analysis of suspicious sessions
     * @return set of suspicious exam sessions
     */
    public Set<SuspiciousExamSessionsDTO> retrieveAllSuspiciousExamSessionsByExamId(long examId, SuspiciousSessionsAnalysisOptions analysisOptions, Optional<String> ipSubnet) {
        Set<SuspiciousExamSessions> suspiciousExamSessions = new HashSet<>();
        Set<ExamSession> examSessions = examSessionRepository.findAllExamSessionsByExamId(examId);
        Set<ExamSession> filteredSessions = filterEqualExamSessionsForSameStudentExam(examSessions);
        Set<StudentExam> studentExams = new HashSet<>();
        boolean studentExamsFetched = false;    // flag to avoid fetching student exams twice
        analyzeSessionsOfDifferentStudentExams(examId, analysisOptions, suspiciousExamSessions, filteredSessions);
        analyzeSessionsOfTheSameStudentExam(examId, analysisOptions, suspiciousExamSessions, studentExams, studentExamsFetched);
        analyzeIpAddressesOutsideOfRange(analysisOptions, ipSubnet, suspiciousExamSessions, examSessions);
        return convertSuspiciousSessionsToDTO(suspiciousExamSessions);
    }

    /**
     * Finds all exam sessions that have a IP address outside a given subnet
     *
     * @param analysisOptions        options for the analysis of suspicious sessions
     * @param ipSubnet               subnet for the analysis of suspicious sessions
     * @param suspiciousExamSessions set of suspicious exam sessions
     * @param examSessions           set of exam sessions to analyze
     */
    private void analyzeIpAddressesOutsideOfRange(SuspiciousSessionsAnalysisOptions analysisOptions, Optional<String> ipSubnet, Set<SuspiciousExamSessions> suspiciousExamSessions,
            Set<ExamSession> examSessions) {
        Set<ExamSession> filteredSessions;
        if (analysisOptions.ipAddressOutsideOfRange()) {
            // seventh step find all sessions that have ip address outside of range
            filteredSessions = filterEqualExamSessionsForSameStudentExam(examSessions);
            findSessionsWithIPAddressOutsideOfRange(filteredSessions, ipSubnet.orElseThrow(), suspiciousExamSessions);
        }
    }

    /**
     * Finds all suspicious exam sessions that belong to the same student exam
     *
     * @param examId                 id of the exam for which suspicious exam sessions shall be retrieved
     * @param analysisOptions        options for the analysis of suspicious sessions
     * @param suspiciousExamSessions set of suspicious exam sessions
     * @param studentExams           set of student exams
     * @param studentExamsFetched    flag to avoid fetching student exams twice
     */
    private void analyzeSessionsOfTheSameStudentExam(long examId, SuspiciousSessionsAnalysisOptions analysisOptions, Set<SuspiciousExamSessions> suspiciousExamSessions,
            Set<StudentExam> studentExams, boolean studentExamsFetched) {
        if (analysisOptions.differentIpAddressesSameStudentExam() && analysisOptions.differentBrowserFingerprintsSameStudentExam()) {
            studentExams = studentExamRepository.findByExamIdWithSessions(examId);
            studentExamsFetched = true;
            // fourth step find all sessions that belong to the same student exam but have different browser fingerprints and ip addresses
            analyzeSessionOfStudentExamsForGivenCriteria(studentExams, true, true, suspiciousExamSessions);
        }
        if (analysisOptions.differentBrowserFingerprintsSameStudentExam()) {
            if (!studentExamsFetched) {
                studentExams = studentExamRepository.findByExamIdWithSessions(examId);
                studentExamsFetched = true;
            }
            // fifth step find all sessions that belong to the same student exam but have different browser fingerprints
            analyzeSessionOfStudentExamsForGivenCriteria(studentExams, false, true, suspiciousExamSessions);
        }
        if (analysisOptions.differentIpAddressesSameStudentExam()) {
            if (!studentExamsFetched) {
                studentExams = studentExamRepository.findByExamIdWithSessions(examId);
            }
            // sixth step find all sessions that belong to the same student exam but have different ip addresses
            analyzeSessionOfStudentExamsForGivenCriteria(studentExams, true, false, suspiciousExamSessions);
        }
    }

    /**
     * Finds all suspicious exam sessions that belong to different student exams
     *
     * @param examId                 id of the exam for which suspicious exam sessions shall be retrieved
     * @param analysisOptions        options for the analysis of suspicious sessions
     * @param suspiciousExamSessions set of suspicious exam sessions
     * @param filteredSessions       set of exam sessions to analyze
     */
    private void analyzeSessionsOfDifferentStudentExams(long examId, SuspiciousSessionsAnalysisOptions analysisOptions, Set<SuspiciousExamSessions> suspiciousExamSessions,
            Set<ExamSession> filteredSessions) {
        if (analysisOptions.sameIpAddressDifferentStudentExams() && analysisOptions.sameBrowserFingerprintDifferentStudentExams()) {
            // first step find all sessions that have matching browser fingerprint and ip address
            findSuspiciousSessionsForGivenCriteria(filteredSessions, examId, filterSessionsForIpAndFingerprint, suspiciousExamSessions, true, true);
        }
        if (analysisOptions.sameBrowserFingerprintDifferentStudentExams()) {
            // second step find all sessions that have only matching browser fingerprint
            findSuspiciousSessionsForGivenCriteria(filteredSessions, examId, filterSessionsForFingerprint, suspiciousExamSessions, false, true);
        }
        if (analysisOptions.sameIpAddressDifferentStudentExams()) {
            // third step find all sessions that have only matching ip address
            findSuspiciousSessionsForGivenCriteria(filteredSessions, examId, filterSessionsForIp, suspiciousExamSessions, true, false);
        }
    }

    /**
     * Finds all suspicious exam sessions that belong to the same student exam
     *
     * @param studentExams                set of student exams
     * @param analyzeDifferentIp          true if the ip addresses shall be compared, otherwise false
     * @param analyzeDifferentFingerprint true if the browser fingerprints shall be compared, otherwise false
     * @param suspiciousExamSessions      set of suspicious exam sessions
     */
    private static void analyzeSessionOfStudentExamsForGivenCriteria(Set<StudentExam> studentExams, boolean analyzeDifferentIp, boolean analyzeDifferentFingerprint,
            Set<SuspiciousExamSessions> suspiciousExamSessions) {
        for (var studentExam : studentExams) {
            analyzeSessionsOfStudentExam(analyzeDifferentIp, analyzeDifferentFingerprint, suspiciousExamSessions, studentExam);
        }
    }

    /**
     * Finds all suspicious exam sessions that belong to the same student exam
     *
     * @param analyzeDifferentIp          true if the ip addresses shall be compared, otherwise false
     * @param analyzeDifferentFingerprint true if the browser fingerprints shall be compared, otherwise false
     * @param suspiciousExamSessions      set of suspicious exam sessions
     * @param studentExam                 student exam for which the exam sessions shall be analyzed
     */
    private static void analyzeSessionsOfStudentExam(boolean analyzeDifferentIp, boolean analyzeDifferentFingerprint, Set<SuspiciousExamSessions> suspiciousExamSessions,
            StudentExam studentExam) {
        var relatedSuspiciousExamSessions = new HashSet<ExamSession>();
        Set<ExamSession> examSessions = studentExam.getExamSessions();
        Set<ExamSession> filteredSessions = filterEqualExamSessionsForSameStudentExam(examSessions);
        for (var examSession : filteredSessions) {
            for (var examSession2 : filteredSessions) {
                compareStudentExamSessions(analyzeDifferentIp, analyzeDifferentFingerprint, relatedSuspiciousExamSessions, examSession, examSession2);
            }
        }
        if (!relatedSuspiciousExamSessions.isEmpty() && !isSubsetOfFoundSuspiciousSessions(relatedSuspiciousExamSessions, suspiciousExamSessions)) {
            suspiciousExamSessions.add(new SuspiciousExamSessions(relatedSuspiciousExamSessions));
        }
    }

    /**
     * Compares two exam sessions of the same student exam and adds the suspicious reasons to the exam sessions if they are suspicious
     *
     * @param analyzeDifferentIp            true if the ip addresses shall be compared, otherwise false
     * @param analyzeDifferentFingerprint   true if the browser fingerprints shall be compared, otherwise false
     * @param relatedSuspiciousExamSessions set of related suspicious exam sessions
     * @param examSession                   first exam session to compare
     * @param examSession2                  second exam session to compare
     */
    private static void compareStudentExamSessions(boolean analyzeDifferentIp, boolean analyzeDifferentFingerprint, HashSet<ExamSession> relatedSuspiciousExamSessions,
            ExamSession examSession, ExamSession examSession2) {
        if (Objects.equals(examSession.getId(), examSession2.getId())) {
            return;
        }
        if (analyzeDifferentIp && !Objects.equals(examSession.getIpAddress(), examSession2.getIpAddress())) {
            examSession.addSuspiciousReason(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES);
            examSession2.addSuspiciousReason(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES);
            relatedSuspiciousExamSessions.add(examSession);
            relatedSuspiciousExamSessions.add(examSession2);

        }
        if (analyzeDifferentFingerprint && !Objects.equals(examSession.getBrowserFingerprintHash(), examSession2.getBrowserFingerprintHash())) {
            examSession.addSuspiciousReason(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS);
            examSession2.addSuspiciousReason(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS);
            relatedSuspiciousExamSessions.add(examSession);
            relatedSuspiciousExamSessions.add(examSession2);
        }
    }

    private void findSessionsWithIPAddressOutsideOfRange(Set<ExamSession> examSessions, String ipSubnet, Set<SuspiciousExamSessions> suspiciousExamSessions) {
        var examSessionsWithIPAddressOutsideOfRange = new HashSet<ExamSession>();
        for (var examSession : examSessions) {
            if (!checkIPIsInGivenRange(ipSubnet, examSession.getIpAddress())) {
                examSession.setSuspiciousReasons(new HashSet<>());
                examSession.addSuspiciousReason(SuspiciousSessionReason.IP_ADDRESS_OUTSIDE_OF_RANGE);
                examSessionsWithIPAddressOutsideOfRange.add(examSession);
            }
        }
        if (!examSessionsWithIPAddressOutsideOfRange.isEmpty()) {
            suspiciousExamSessions.add(new SuspiciousExamSessions(examSessionsWithIPAddressOutsideOfRange));
        }
    }

    private boolean checkIPIsInGivenRange(String ipSubnet, String ipAddress) {
        IpAddressMatcher ipAddressMatcher = new IpAddressMatcher(ipSubnet);
        // if they are not both IPv4 or IPv6, we cannot check if the address is in the subnet and return true
        if (!checkIfSubnetAndAddressHaveTheSameVersion(ipSubnet, ipAddress, IPAddress.IPVersion.IPV4)
                && !checkIfSubnetAndAddressHaveTheSameVersion(ipSubnet, ipAddress, IPAddress.IPVersion.IPV6)) {
            log.debug("IP address {} and subnet {} have different versions", ipAddress, ipSubnet);
            return true;
        }
        return ipAddressMatcher.matches(ipAddress);

    }

    private static boolean checkIfSubnetAndAddressHaveTheSameVersion(String ipSubnet, String ipAddress, IPAddress.IPVersion version) {
        var address = new IPAddressString(ipAddress).getAddress(version);
        var ipSubnetAddress = new IPAddressString(ipSubnet).getAddress(version);
        // return false if one of the addresses is null, not the expected version (IPv4 or IPv6)
        return address != null && ipSubnetAddress != null;
    }

    /**
     * Finds suspicious exam sessions according to the criteria given and adds them to the set of suspicious exam sessions
     *
     * @param examSessions                                       set of exam sessions to be processed
     * @param examId                                             id of the exam for which suspicious exam sessions shall be retrieved
     * @param criteriaFilter                                     function that returns a set of exam sessions that match the given criteria
     * @param suspiciousExamSessions                             set of suspicious exam sessions to which the found suspicious exam sessions shall be added
     * @param analyzeDifferentStudentExamsSameIp                 true if the ip addresses shall be compared, otherwise false
     * @param analyzeDifferentStudentExamsSameBrowserFingerprint true if the browser fingerprints shall be compared, otherwise false
     */
    private static void findSuspiciousSessionsForGivenCriteria(Set<ExamSession> examSessions, long examId, BiFunction<Long, ExamSession, Set<ExamSession>> criteriaFilter,
            Set<SuspiciousExamSessions> suspiciousExamSessions, boolean analyzeDifferentStudentExamsSameIp, boolean analyzeDifferentStudentExamsSameBrowserFingerprint) {
        for (var examSession : examSessions) {
            Set<ExamSession> relatedExamSessions = criteriaFilter.apply(examId, examSession);
            relatedExamSessions = filterEqualRelatedExamSessionsOfSameStudentExam(relatedExamSessions);

            if (!relatedExamSessions.isEmpty() && !isSubsetOfFoundSuspiciousSessions(relatedExamSessions, suspiciousExamSessions)) {
                var session = addSuspiciousReasons(examSession, relatedExamSessions, analyzeDifferentStudentExamsSameIp, analyzeDifferentStudentExamsSameBrowserFingerprint);
                relatedExamSessions.add(session);
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
    private static boolean isSubsetOfFoundSuspiciousSessions(Set<ExamSession> relatedExamSessions, Set<SuspiciousExamSessions> suspiciousExamSessions) {
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
    private static Set<ExamSession> filterEqualExamSessionsForSameStudentExam(Set<ExamSession> examSessions) {
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
    private static Set<ExamSession> filterEqualRelatedExamSessionsOfSameStudentExam(Set<ExamSession> examSessions) {
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

    private static Set<SuspiciousExamSessionsDTO> convertSuspiciousSessionsToDTO(Set<SuspiciousExamSessions> suspiciousExamSessions) {
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
     * @param session                                            exam session we compare with
     * @param relatedExamSessions                                related exam sessions
     * @param analyzeDifferentStudentExamsSameIp                 true if the ip addresses shall be compared, otherwise false
     * @param analyzeDifferentStudentExamsSameBrowserFingerprint true if the browser fingerprints shall be compared, otherwise false
     * @return exam session we compare with suspicious reasons added
     */
    private static ExamSession addSuspiciousReasons(ExamSession session, Set<ExamSession> relatedExamSessions, boolean analyzeDifferentStudentExamsSameIp,
            boolean analyzeDifferentStudentExamsSameBrowserFingerprint) {
        ExamSession sessionCopy = new ExamSession();
        sessionCopy.setId(session.getId());
        sessionCopy.setSuspiciousReasons(new HashSet<>());
        sessionCopy.setBrowserFingerprintHash(session.getBrowserFingerprintHash());
        sessionCopy.setIpAddress(session.getIpAddress());
        sessionCopy.setUserAgent(session.getUserAgent());
        sessionCopy.setStudentExam(session.getStudentExam());
        sessionCopy.setCreatedDate(session.getCreatedDate());
        sessionCopy.setInstanceId(session.getInstanceId());
        sessionCopy.setSessionToken(session.getSessionToken());

        for (var relatedExamSession : relatedExamSessions) {
            relatedExamSession.setSuspiciousReasons(new HashSet<>());
            // if we do not check the analysis criteria, we might add reasons that should not be analyzed
            if (relatedExamSession.hasSameBrowserFingerprint(session) && analyzeDifferentStudentExamsSameBrowserFingerprint) {
                relatedExamSession.addSuspiciousReason(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT);
                sessionCopy.addSuspiciousReason(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT);
            }
            if (relatedExamSession.hasSameIpAddress(session) && analyzeDifferentStudentExamsSameIp) {
                relatedExamSession.addSuspiciousReason(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS);
                sessionCopy.addSuspiciousReason(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS);
            }

        }
        return sessionCopy;
    }

}
