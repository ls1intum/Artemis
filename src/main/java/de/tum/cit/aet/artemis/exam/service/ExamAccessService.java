package de.tum.cit.aet.artemis.exam.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * Service implementation to check exam access.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamAccessService {

    private static final String ENTITY_NAME = "Exam";

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    private final CourseRepository courseRepository;

    private final ExamRegistrationService examRegistrationService;

    private final StudentExamService studentExamService;

    private final ExamDateService examDateService;

    public ExamAccessService(ExamRepository examRepository, StudentExamRepository studentExamRepository, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository, CourseRepository courseRepository, ExamRegistrationService examRegistrationService, StudentExamService studentExamService,
            ExamDateService examDateService) {
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.examRegistrationService = examRegistrationService;
        this.studentExamService = studentExamService;
        this.examDateService = examDateService;
    }

    /**
     * Checks if the user is allowed to see the exam result if:
     * - the current user is at least teaching assistant in the course
     * - OR if the examExercise is not part of an exam
     * - OR if the exam is a test exam
     * - OR if the exam has not ended (including individual working time extensions)
     * - OR if the exam has already ended and the results were published
     * Otherwise, throws a {@link AccessForbiddenException}.
     *
     * @param examExercise         - Exercise that the result is requested for
     * @param studentParticipation - used to retrieve the individual exam working time
     * @param user                 - User that requests the result
     * @throws ConflictException if examExercise does not belong to an exam
     */
    public void checkIfAllowedToGetExamResult(Exercise examExercise, StudentParticipation studentParticipation, User user) {
        if (!examExercise.isExamExercise()) {
            throw new ConflictException("Given examExercise does not belong to an exam", "Exercise", "notExamExercise");
        }

        if (authorizationCheckService.isAtLeastTeachingAssistantInCourse(examExercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
            return;
        }
        Exam exam = examExercise.getExam();

        if (!examDateService.isExerciseWorkingPeriodOver(examExercise, studentParticipation)) {
            // students can always see their results during the exam.
            return;
        }
        if (exam.isTestExam()) {
            // results for test exams are always visible
            return;
        }
        if (exam.resultsPublished()) {
            return;
        }
        throw new AccessForbiddenException();
    }

    /**
     * TODO: we should distinguish the whole method between test exam and real exam to improve the readability of the code
     * Real Exams: Checks if the current user is allowed to see the requested exam. If he is allowed the student exam will be returned (Fallback: create a new one)
     * Test Exams: Either retrieves an existing StudentExam from the Database or generates a new StudentExam
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     * @return a ResponseEntity with the exam
     */
    public StudentExam getOrCreateStudentExamElseThrow(Long courseId, Long examId) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();

        // Check that the current user is at least student in the course.
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, currentUser);

        Exam exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);
        checkExamBelongsToCourseElseThrow(courseId, exam);

        // Check that the exam is visible
        if (exam.getVisibleDate() != null && exam.getVisibleDate().isAfter(ZonedDateTime.now())) {
            throw new AccessForbiddenException(ENTITY_NAME, examId);
        }

        StudentExam studentExam;
        if (exam.isTestExam()) {
            studentExam = getOrCreateTestExam(exam, course, currentUser);
        }
        else if (this.authorizationCheckService.isAtLeastInstructorInCourse(course, currentUser)) {
            throw new AccessForbiddenAlertException(ErrorConstants.DEFAULT_TYPE, "Instructors or administrators cannot participate in exams.", ENTITY_NAME,
                    "cannotParticipateInExams", true);
        }
        else {
            studentExam = getOrCreateNormalExam(exam, currentUser);
        }

        if (!examId.equals(studentExam.getExam().getId())) {
            throw new ConflictException("The provided examId does not match with the examId of the studentExam", ENTITY_NAME, "examIdMismatch");
        }

        return studentExam;
    }

    private StudentExam getOrCreateNormalExam(Exam exam, User currentUser) {
        // Check that the student exam exists
        Optional<StudentExam> optionalStudentExam = studentExamRepository.findByExamIdAndUserId(exam.getId(), currentUser.getId());

        StudentExam studentExam;
        // If an studentExam can be found, we can immediately proceed
        if (optionalStudentExam.isPresent()) {
            studentExam = optionalStudentExam.get();
        }
        else {

            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime unlockDate = ExamDateService.getExamProgrammingExerciseUnlockDate(exam);

            // An exam can be started 5 minutes before the start time, which is when programming exercises are unlocked
            boolean canExamBeStarted = now.isAfter(unlockDate);
            boolean isUserRegistered = examRegistrationService.isUserRegisteredForExam(exam.getId(), currentUser.getId());
            boolean isExamEnded = ZonedDateTime.now().isAfter(exam.getEndDate());
            // Generate a student exam if the following conditions are met:
            // 1. The exam has not ended.
            // 2. User is registered and can click the start button.
            // Allowing student exams to be generated only when students can click the start button prevents inconsistencies.
            // For example, this avoids a scenario where a student generates an exam and an instructor adds an exercise group afterward.

            if (isExamEnded) {
                throw new BadRequestAlertException("The exam has already ended. Cannot generate student exam.", ENTITY_NAME, "examEnded", true);
            }
            if (!isUserRegistered) {
                throw new AccessForbiddenException("User is not registered for the exam. Cannot generate student exam.");
            }
            if (!canExamBeStarted) {
                throw new AccessForbiddenException("The exam cannot be started yet. Cannot generate student exam.");
            }
            // Proceed only if the exam has not ended and the user meets the conditions
            else {
                studentExam = studentExamService.generateIndividualStudentExam(exam, currentUser);
                studentExam.setExercises(null);
            }
        }
        return studentExam;
    }

    private StudentExam getOrCreateTestExam(Exam exam, Course course, User currentUser) {
        StudentExam studentExam;

        if (exam.getEndDate().isBefore(ZonedDateTime.now())) {
            throw new BadRequestAlertException("Test exam has already ended", ENTITY_NAME, "examHasAlreadyEnded", true);
        }

        List<StudentExam> unfinishedStudentExams = studentExamRepository.findStudentExamsForTestExamsByUserIdAndExamId(currentUser.getId(), exam.getId()).stream()
                .filter(attempt -> !attempt.isFinished()).toList();

        if (unfinishedStudentExams.isEmpty()) {
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime unlockDate = ExamDateService.getExamProgrammingExerciseUnlockDate(exam);

            // An exam can be started 5 minutes before the start time, which is when programming exercises are unlocked
            boolean canExamBeStarted = now.isAfter(unlockDate);
            if (!canExamBeStarted) {
                throw new AccessForbiddenException("The exam cannot be started yet. Cannot generate student exam.");
            }

            studentExam = studentExamService.generateIndividualStudentExam(exam, currentUser);
            // For the start of the exam, the exercises are not needed. They are later loaded via StudentExamResource
            studentExam.setExercises(null);
        }
        else if (unfinishedStudentExams.size() == 1) {
            studentExam = unfinishedStudentExams.getFirst();
        }
        else {
            throw new IllegalStateException(
                    "User " + currentUser.getId() + " has " + unfinishedStudentExams.size() + " unfinished test exams for exam " + exam.getId() + " in course " + course.getId());
        }
        // Check that the current user is registered for the test exam. Otherwise, the student can self-register
        examRegistrationService.checkRegistrationOrRegisterStudentToTestExam(course, exam.getId(), currentUser);
        return studentExam;
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course.
     *
     * @param courseId The id of the course
     */
    public void checkCourseAccessForEditorElseThrow(Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastEditorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to manage exams in this course!");
        }
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course.
     *
     * @param courseId The id of the course
     */
    public void checkCourseAccessForInstructorElseThrow(Long courseId) {
        // TODO: move this check directly into the database for performance reasons
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to manage exams in this course!");
        }
    }

    /**
     * Checks if the current user is allowed to access the exam as teaching assistant.
     *
     * @param courseId The id of the course
     */
    public void checkCourseAccessForTeachingAssistantElseThrow(Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to access exams in this course!");
        }
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists and that the exam
     * belongs to the given course.
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     */
    public void checkCourseAndExamAccessForEditorElseThrow(Long courseId, Long examId) {
        checkCourseAccessForEditorElseThrow(courseId);
        checkExamBelongsToCourseElseThrow(courseId, examId);
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists and that the exam
     * belongs to the given course.
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     */
    public void checkCourseAndExamAccessForInstructorElseThrow(Long courseId, Long examId) {
        checkCourseAccessForInstructorElseThrow(courseId);
        checkExamBelongsToCourseElseThrow(courseId, examId);
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists and that the exam
     * belongs to the given course.
     *
     * @param courseId The id of the course
     * @param exam     The exam
     */
    public void checkCourseAndExamAccessForInstructorElseThrow(Long courseId, Exam exam) {
        checkCourseAccessForInstructorElseThrow(courseId);
        checkExamBelongsToCourseElseThrow(courseId, exam);
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists and that the exam
     * belongs to the given course.
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     */
    public void checkCourseAndExamAccessForTeachingAssistantElseThrow(Long courseId, Long examId) {
        checkCourseAccessForTeachingAssistantElseThrow(courseId);
        checkExamBelongsToCourseElseThrow(courseId, examId);
    }

    /**
     * Checks if the current user is has a student exam in the given exam of the given course and that the exam
     * belongs to the given course.
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     */
    public void checkCourseAndExamAccessForStudentElseThrow(Long courseId, Long examId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, currentUser);
        if (authorizationCheckService.isAtLeastInstructorInCourse(course, currentUser)) {
            checkExamBelongsToCourseElseThrow(courseId, examId);
        }
        else if (!studentExamRepository.existsByExam_CourseIdAndExamIdAndUserId(courseId, examId, currentUser.getId())) {
            throw new AccessForbiddenException("You are not allowed to access this exam!");
        }
    }

    /**
     * Checks if the current user is eligible to access the example solutions for the given exercise and
     * if the example solution is published.
     * Eligibility conditions vary based on user roles.
     *
     * @param examExercise The exercise which must belong to an exam
     */
    public void checkExamExerciseForExampleSolutionAccessElseThrow(Exercise examExercise) {
        if (!examExercise.isExamExercise()) {
            throw new ConflictException("Given exercise does not belong to an exam", "Exercise", "notExamExercise");
        }
        Exam exam = examExercise.getExerciseGroup().getExam();
        if (!authorizationCheckService.isAtLeastTeachingAssistantInCourse(exam.getCourse(), null)) {
            checkCourseAndExamAccessForStudentElseThrow(exam.getCourse().getId(), exam.getId());
        }
        if (!examExercise.isExampleSolutionPublished()) {
            throw new AccessForbiddenException("Example solution for exam is not published yet!");
        }
    }

    private void checkExamBelongsToCourseElseThrow(Long courseId, Long examId) {
        // TODO: move this check directly into the database for performance reasons
        Optional<Exam> exam = examRepository.findById(examId);
        if (exam.isEmpty()) {
            throw new EntityNotFoundException(ENTITY_NAME, examId);
        }
        else {
            checkExamBelongsToCourseElseThrow(courseId, exam.get());
        }
    }

    private void checkExamBelongsToCourseElseThrow(Long courseId, Exam exam) {
        if (!exam.getCourse().getId().equals(courseId)) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists,
     * that the exam belongs to the given course and the exercise group belongs to the given exam.
     *
     * @param role          The role of the callee
     * @param courseId      The id of the course
     * @param examId        The id of the exam
     * @param exerciseGroup The exercise group
     */
    public void checkCourseAndExamAndExerciseGroupAccessElseThrow(Role role, Long courseId, Long examId, ExerciseGroup exerciseGroup) {
        switch (role) {
            case INSTRUCTOR -> checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
            case EDITOR -> checkCourseAndExamAccessForEditorElseThrow(courseId, examId);
            default -> throw new AccessForbiddenException(ENTITY_NAME, examId);
        }

        Exam exam = exerciseGroup.getExam();
        if (exam == null || !exam.getId().equals(examId)) {
            throw new ConflictException("Invalid exam id", ENTITY_NAME, "noIdMatch");
        }
        checkExamBelongsToCourseElseThrow(courseId, exam);
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists,
     * that the exam belongs to the given course and the student exam belongs to the given exam.
     *
     * @param courseId      The id of the course
     * @param examId        The id of the exam
     * @param studentExamId The id of the student exam
     */
    public void checkCourseAndExamAndStudentExamAccessElseThrow(Long courseId, Long examId, Long studentExamId) {
        checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        checkStudentExamExistsAndBelongsToExamElseThrow(studentExamId, examId);
    }

    /**
     * Checks if the given student exam exists and belongs to the given exam.
     *
     * @param studentExamId The id of the student exam
     * @param examId        The id of the exam
     */
    public void checkStudentExamExistsAndBelongsToExamElseThrow(Long studentExamId, Long examId) {
        Optional<StudentExam> studentExam = studentExamRepository.findById(studentExamId);
        if (studentExam.isEmpty()) {
            throw new EntityNotFoundException(ENTITY_NAME, examId);
        }
        if (!studentExam.get().getExam().getId().equals(examId)) {
            throw new ConflictException("Invalid exam id", ENTITY_NAME, "noIdMatch");
        }
    }
}
