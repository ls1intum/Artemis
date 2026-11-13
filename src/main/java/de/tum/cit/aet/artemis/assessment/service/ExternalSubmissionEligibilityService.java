package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.ExternalSubmissionExerciseRejection;
import de.tum.cit.aet.artemis.assessment.domain.ExternalSubmissionIneligibility;
import de.tum.cit.aet.artemis.assessment.dto.ExternalSubmissionCandidateDTO;
import de.tum.cit.aet.artemis.assessment.repository.ExternalSubmissionResultRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.dto.PagedSearchDTO;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * Decides whether an exercise can take an external submission result and which students can receive one. The endpoint that creates the result and the student search of the
 * external submission dialog both use this service, so the suggestions and the save always apply the same rules.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExternalSubmissionEligibilityService {

    /**
     * The minimum length of a search term for candidates. Mirrored by {@code EXTERNAL_SUBMISSION_SEARCH_MIN_LENGTH} in
     * {@code external-submission-student-search.component.ts}; change both together.
     */
    public static final int MIN_SEARCH_TERM_LENGTH = 3;

    private final UserRepository userRepository;

    private final ExternalSubmissionResultRepository externalSubmissionResultRepository;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ExamDateApi> examDateApi;

    private final Optional<StudentExamApi> studentExamApi;

    public ExternalSubmissionEligibilityService(UserRepository userRepository, ExternalSubmissionResultRepository externalSubmissionResultRepository,
            AuthorizationCheckService authCheckService, Optional<ExamDateApi> examDateApi, Optional<StudentExamApi> studentExamApi) {
        this.userRepository = userRepository;
        this.externalSubmissionResultRepository = externalSubmissionResultRepository;
        this.authCheckService = authCheckService;
        this.examDateApi = examDateApi;
        this.studentExamApi = studentExamApi;
    }

    /**
     * Checks whether the exercise can take an external submission result at all, independent of the student.
     *
     * @param exercise the exercise
     * @return the reason the exercise rejects external submissions, or empty if it accepts them
     */
    public Optional<ExternalSubmissionExerciseRejection> checkExerciseAcceptsExternalSubmissions(Exercise exercise) {
        if (!exercise.isExamExercise()) {
            if (exercise.getDueDate() == null || ZonedDateTime.now().isBefore(exercise.getDueDate())) {
                return Optional.of(ExternalSubmissionExerciseRejection.BEFORE_DUE_DATE);
            }
        }
        else {
            ExamDateApi api = examDateApi.orElseThrow(() -> new ExamApiNotPresentException(ExamDateApi.class));
            ZonedDateTime latestIndividualExamEndDate = api.getLatestIndividualExamEndDate(exercise.getExerciseGroup().getExam());
            if (latestIndividualExamEndDate == null || ZonedDateTime.now().isBefore(latestIndividualExamEndDate)) {
                return Optional.of(ExternalSubmissionExerciseRejection.BEFORE_EXAM_END);
            }
        }
        if (exercise instanceof QuizExercise) {
            return Optional.of(ExternalSubmissionExerciseRejection.QUIZ_EXERCISE);
        }
        return Optional.empty();
    }

    /**
     * Checks whether a user can receive an external submission result for the exercise. Exercise-level conditions are checked separately by
     * {@link #checkExerciseAcceptsExternalSubmissions(Exercise)}.
     *
     * @param exercise the exercise
     * @param user     the user the result would be created for
     * @return the reason the user cannot receive a result, or empty if they can
     */
    public Optional<ExternalSubmissionIneligibility> checkEligibility(Exercise exercise, User user) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow();
        if (!authCheckService.isAtLeastStudentInCourse(course, user)) {
            return Optional.of(ExternalSubmissionIneligibility.NOT_IN_COURSE);
        }
        return Optional.ofNullable(findIneligibilities(exercise, Set.of(user.getId())).get(user.getId()));
    }

    /**
     * Searches the students of the exercise's course by login, name, email or registration number and reports for each whether they can receive an external submission
     * result for the exercise. Tutors, editors and instructors are not included.
     *
     * @param exercise   the exercise
     * @param searchTerm the text entered in the dialog
     * @param page       zero-based page index
     * @param pageSize   number of students per page
     * @return a page of candidates, sorted by name
     */
    public Page<ExternalSubmissionCandidateDTO> searchCandidates(Exercise exercise, String searchTerm, int page, int pageSize) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow();
        var search = new PagedSearchDTO(page, pageSize, SortingOrder.ASCENDING, "name", searchTerm, null);
        Page<User> students = userRepository.searchUsersInCourseRole(PageRequest.of(page, pageSize), search, course.getId(), CourseRole.STUDENT);
        List<Long> studentIds = students.getContent().stream().map(User::getId).toList();
        Map<Long, ExternalSubmissionIneligibility> ineligibilities = studentIds.isEmpty() ? Map.of() : findIneligibilities(exercise, studentIds);
        return students.map(student -> new ExternalSubmissionCandidateDTO(student.getLogin(), student.getName(), ineligibilities.get(student.getId())));
    }

    /**
     * Determines the reasons why course members cannot receive an external submission result for the exercise, with a fixed number of queries for all of them.
     *
     * @param exercise the exercise
     * @param userIds  the ids of users that are members of the exercise's course
     * @return the reason per user id; users that can receive a result are absent
     */
    private Map<Long, ExternalSubmissionIneligibility> findIneligibilities(Exercise exercise, Collection<Long> userIds) {
        boolean examExercise = exercise.isExamExercise();
        Set<Long> withStudentExam = Set.of();
        Set<Long> withExerciseInStudentExam = Set.of();
        if (examExercise) {
            StudentExamApi api = studentExamApi.orElseThrow(() -> new ExamApiNotPresentException(StudentExamApi.class));
            long examId = exercise.getExerciseGroup().getExam().getId();
            withStudentExam = api.findUserIdsWithWorkingPeriod(examId, userIds);
            withExerciseInStudentExam = api.findUserIdsWithExerciseInNewestStudentExam(examId, exercise.getId(), userIds);
        }
        Set<Long> withResult = externalSubmissionResultRepository.findParticipantIdsWithResultForExercise(exercise.getId(), userIds);

        Map<Long, ExternalSubmissionIneligibility> ineligibilities = new HashMap<>();
        for (Long userId : userIds) {
            ineligibilityFrom(true, withStudentExam.contains(userId), withExerciseInStudentExam.contains(userId), withResult.contains(userId), examExercise)
                    .ifPresent(reason -> ineligibilities.put(userId, reason));
        }
        return ineligibilities;
    }

    /**
     * Reduces the facts about a user to the one reason that is reported, in the priority order of {@link ExternalSubmissionIneligibility}. The exam facts are only
     * considered for exam exercises.
     *
     * @param inCourse              whether the user is a member of the exercise's course
     * @param hasStudentExam        whether the user has a student exam in the exam
     * @param exerciseInStudentExam whether the newest student exam of the user contains the exercise
     * @param hasResult             whether the user already has a result in the exercise
     * @param examExercise          whether the exercise belongs to an exam
     * @return the reason the user cannot receive a result, or empty if they can
     */
    static Optional<ExternalSubmissionIneligibility> ineligibilityFrom(boolean inCourse, boolean hasStudentExam, boolean exerciseInStudentExam, boolean hasResult,
            boolean examExercise) {
        if (!inCourse) {
            return Optional.of(ExternalSubmissionIneligibility.NOT_IN_COURSE);
        }
        if (examExercise && !hasStudentExam) {
            return Optional.of(ExternalSubmissionIneligibility.NO_STUDENT_EXAM);
        }
        if (examExercise && !exerciseInStudentExam) {
            return Optional.of(ExternalSubmissionIneligibility.EXERCISE_NOT_IN_STUDENT_EXAM);
        }
        if (hasResult) {
            return Optional.of(ExternalSubmissionIneligibility.RESULT_ALREADY_EXISTS);
        }
        return Optional.empty();
    }
}
