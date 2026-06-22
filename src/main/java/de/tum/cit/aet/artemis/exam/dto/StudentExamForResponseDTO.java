package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.dto.CourseWithIdDTO;
import de.tum.cit.aet.artemis.exam.domain.ExamSession;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseForStudentExamDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamForResponseDTO(Long id, Boolean submitted, Integer workingTime, Boolean started, ZonedDateTime startedDate, ZonedDateTime submissionDate, Boolean testRun,
        Boolean ended, StudentExamUserDTO user, ExamForStudentExamDTO exam, List<Object> exercises, Set<ExamSessionForStudentExamDTO> examSessions) {

    /**
     * Creates the student exam response for exam conduction. The response includes the current exam
     * session so the client can persist the active session token.
     *
     * @param studentExam      the student exam to map
     * @param includeSolutions whether quiz solution fields should be included in the response
     * @return the response DTO
     */
    public static StudentExamForResponseDTO forConduction(StudentExam studentExam, boolean includeSolutions) {
        return of(studentExam, includeSolutions, mapExamSessions(studentExam.getExamSessions()));
    }

    /**
     * Creates the student exam response for the summary page. Exam sessions are intentionally omitted
     * because the summary view does not need session tokens and may operate on detached student exams.
     *
     * @param studentExam      the student exam to map
     * @param includeSolutions whether quiz solution fields should be included in the response
     * @return the response DTO
     */
    public static StudentExamForResponseDTO forSummary(StudentExam studentExam, boolean includeSolutions) {
        return of(studentExam, includeSolutions, null);
    }

    private static StudentExamForResponseDTO of(StudentExam studentExam, boolean includeSolutions, Set<ExamSessionForStudentExamDTO> examSessions) {
        List<Object> exerciseDTOs = studentExam.getExercises().stream().map(exercise -> mapExercise(exercise, includeSolutions)).toList();
        return new StudentExamForResponseDTO(studentExam.getId(), studentExam.isSubmitted(), studentExam.getWorkingTime(), studentExam.isStarted(), studentExam.getStartedDate(),
                studentExam.getSubmissionDate(), studentExam.isTestRun(), studentExam.isEnded(), StudentExamUserDTO.of(studentExam.getUser()),
                ExamForStudentExamDTO.of(studentExam.getExam()), exerciseDTOs, examSessions);
    }

    private static Object mapExercise(Exercise exercise, boolean includeSolutions) {
        if (exercise instanceof QuizExercise quizExercise) {
            return QuizExerciseForStudentExamDTO.of(quizExercise, includeSolutions);
        }
        return exercise;
    }

    private static Set<ExamSessionForStudentExamDTO> mapExamSessions(Set<ExamSession> examSessions) {
        if (examSessions == null) {
            return Set.of();
        }
        return examSessions.stream().map(ExamSessionForStudentExamDTO::of).collect(Collectors.toSet());
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StudentExamUserDTO(Long id, String login, String firstName, String lastName, String email, Boolean activated, Boolean deleted, String langKey, Boolean internal,
            Boolean memirisEnabled, String participantIdentifier, String name, Boolean bot) {

        /**
         * Creates the user projection required by student exam conduction and summary responses.
         *
         * @param user the user to map
         * @return the response DTO
         */
        public static StudentExamUserDTO of(User user) {
            if (user == null) {
                return null;
            }
            return new StudentExamUserDTO(user.getId(), user.getLogin(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getActivated(), user.isDeleted(),
                    user.getLangKey(), user.isInternal(), user.isMemirisEnabled(), user.getParticipantIdentifier(), user.getName(), user.isBot());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamForStudentExamDTO(Long id, String title, Boolean testExam, Boolean examWithAttendanceCheck, ZonedDateTime visibleDate, ZonedDateTime startDate,
            ZonedDateTime endDate, ZonedDateTime publishResultsDate, ZonedDateTime examStudentReviewStart, ZonedDateTime examStudentReviewEnd,
            ZonedDateTime exampleSolutionPublicationDate, Integer workingTime, Integer gracePeriod, String startText, String endText, String confirmationStartText,
            String confirmationEndText, Integer examMaxPoints, Boolean randomizeExerciseOrder, Integer numberOfExercisesInExam, Integer numberOfCorrectionRoundsInExam,
            String examiner, String moduleNumber, String courseName, CourseWithIdDTO course, String examArchivePath, Long numberOfExamUsers, String channelName,
            Integer quizExamMaxPoints, Boolean visible, Boolean started) {

        /**
         * Creates the exam projection used inside student exam conduction and summary responses.
         *
         * @param exam the exam to map
         * @return the response DTO
         */
        public static ExamForStudentExamDTO of(de.tum.cit.aet.artemis.exam.domain.Exam exam) {
            if (exam == null) {
                return null;
            }
            CourseWithIdDTO course = exam.getCourse() != null ? new CourseWithIdDTO(exam.getCourse().getId()) : null;
            return new ExamForStudentExamDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), exam.isExamWithAttendanceCheck(), exam.getVisibleDate(), exam.getStartDate(),
                    exam.getEndDate(), exam.getPublishResultsDate(), exam.getExamStudentReviewStart(), exam.getExamStudentReviewEnd(), exam.getExampleSolutionPublicationDate(),
                    exam.getWorkingTime(), exam.getGracePeriod(), exam.getStartText(), exam.getEndText(), exam.getConfirmationStartText(), exam.getConfirmationEndText(),
                    exam.getExamMaxPoints(), exam.getRandomizeExerciseOrder(), exam.getNumberOfExercisesInExam(), exam.getNumberOfCorrectionRoundsInExam(), exam.getExaminer(),
                    exam.getModuleNumber(), exam.getCourseName(), course, exam.getExamArchivePath(), exam.getNumberOfExamUsers(), exam.getChannelName(),
                    exam.getQuizExamMaxPoints(), exam.isVisibleToStudents(), exam.isStarted());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamSessionForStudentExamDTO(Long id, String sessionToken, String userAgent, String browserFingerprintHash, String instanceId, String ipAddress,
            Boolean initialSession, Set<de.tum.cit.aet.artemis.exam.domain.SuspiciousSessionReason> suspiciousReasons) {

        /**
         * Creates the exam-session projection used inside student exam conduction and summary responses.
         *
         * @param examSession the exam session to map
         * @return the response DTO
         */
        public static ExamSessionForStudentExamDTO of(ExamSession examSession) {
            return new ExamSessionForStudentExamDTO(examSession.getId(), examSession.getSessionToken(), examSession.getUserAgent(), examSession.getBrowserFingerprintHash(),
                    examSession.getInstanceId(), examSession.getIpAddress(), examSession.isInitialSession(), examSession.getSuspiciousReasons());
        }
    }
}
