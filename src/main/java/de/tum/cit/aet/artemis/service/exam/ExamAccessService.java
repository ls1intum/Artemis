package de.tum.cit.aet.artemis.service.exam;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.ConflictException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service implementation to check exam access.
 */
@Profile(PROFILE_CORE)
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

    public ExamAccessService(ExamRepository examRepository, StudentExamRepository studentExamRepository, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository, CourseRepository courseRepository, ExamRegistrationService examRegistrationService, StudentExamService studentExamService) {
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.examRegistrationService = examRegistrationService;
        this.studentExamService = studentExamService;
    }

    /**
     * Real Exams: Checks if the current user is allowed to see the requested exam. If he is allowed the exam will be returned.
     * Test Exams: Either retrieves an existing StudentExam from the Database or generates a new StudentExam
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     * @return a ResponseEntity with the exam
     */
    public StudentExam getExamInCourseElseThrow(Long courseId, Long examId) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();

        // TODO: we should distinguish the whole method between test exam and real exam to improve the readability of the code
        // Check that the current user is at least student in the course.
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, currentUser);

        // Check that the student exam exists
        Optional<StudentExam> optionalStudentExam = studentExamRepository.findByExamIdAndUserId(examId, currentUser.getId());

        StudentExam studentExam;
        // If an studentExam can be fund, we can proceed
        if (optionalStudentExam.isPresent()) {
            studentExam = optionalStudentExam.get();
        }
        else {
            // Only Test Exams can be self-created by the user.
            Exam examWithExerciseGroupsAndExercises = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);

            if (!examWithExerciseGroupsAndExercises.isTestExam()) {
                // We skip the alert since this can happen when a tutor sees the exam card or the user did not participate yet is registered for the exam
                throw new BadRequestAlertException("The requested Exam is no test exam and thus no student exam can be created", ENTITY_NAME,
                        "StudentExamGenerationOnlyForTestExams", true);
            }
            studentExam = studentExamService.generateTestExam(examWithExerciseGroupsAndExercises, currentUser);
            // For the start of the exam, the exercises are not needed. They are later loaded via StudentExamResource
            studentExam.setExercises(null);
        }

        Exam exam = studentExam.getExam();

        checkExamBelongsToCourseElseThrow(courseId, exam);

        if (!examId.equals(exam.getId())) {
            throw new BadRequestAlertException("The provided examId does not match with the examId of the studentExam", ENTITY_NAME, "examIdMismatch");
        }

        // Check that the exam is visible
        if (exam.getVisibleDate() != null && exam.getVisibleDate().isAfter(ZonedDateTime.now())) {
            throw new AccessForbiddenException(ENTITY_NAME, examId);
        }

        if (exam.isTestExam()) {
            // Check that the current user is registered for the test exam. Otherwise, the student can self-register
            examRegistrationService.checkRegistrationOrRegisterStudentToTestExam(course, exam.getId(), currentUser);
        }
        // NOTE: the check examRepository.isUserRegisteredForExam is not necessary because we already checked before that there is a student exam in this case for the current user

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
     * @param studentExamId The if of the student exam
     */
    public void checkCourseAndExamAndStudentExamAccessElseThrow(Long courseId, Long examId, Long studentExamId) {
        checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        Optional<StudentExam> studentExam = studentExamRepository.findById(studentExamId);
        if (studentExam.isEmpty()) {
            throw new EntityNotFoundException(ENTITY_NAME, examId);
        }
        if (!studentExam.get().getExam().getId().equals(examId)) {
            throw new ConflictException("Invalid exam id", ENTITY_NAME, "noIdMatch");
        }
    }
}
