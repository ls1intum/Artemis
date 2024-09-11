package de.tum.cit.aet.artemis.web.rest.plagiarism;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.plagiarism.PlagiarismCaseService;
import de.tum.cit.aet.artemis.web.rest.dto.plagiarism.PlagiarismCaseInfoDTO;
import de.tum.cit.aet.artemis.web.rest.dto.plagiarism.PlagiarismVerdictDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing Plagiarism Cases.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class PlagiarismCaseResource {

    private static final String ENTITY_NAME = "plagiarismCase";

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authenticationCheckService;

    private final UserRepository userRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private static final Logger log = LoggerFactory.getLogger(PlagiarismCaseResource.class);

    public PlagiarismCaseResource(CourseRepository courseRepository, AuthorizationCheckService authenticationCheckService, UserRepository userRepository,
            PlagiarismCaseService plagiarismCaseService, PlagiarismCaseRepository plagiarismCaseRepository) {
        this.courseRepository = courseRepository;
        this.authenticationCheckService = authenticationCheckService;
        this.userRepository = userRepository;
        this.plagiarismCaseService = plagiarismCaseService;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
    }

    /**
     * Retrieves all plagiarism cases related to a course for the instructor view.
     *
     * @param courseId the id of the course
     * @return all plagiarism cases of the course
     */
    @GetMapping("courses/{courseId}/plagiarism-cases/for-instructor")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<PlagiarismCase>> getPlagiarismCasesForCourseForInstructor(@PathVariable long courseId) {
        log.debug("REST request to get all plagiarism cases for instructor in course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, userRepository.getUserWithGroupsAndAuthorities())) {
            throw new AccessForbiddenException("Only instructors of this course have access to its plagiarism cases.");
        }
        var plagiarismCases = plagiarismCaseRepository.findByCourseIdWithPlagiarismSubmissionsAndComparison(courseId);
        return getPlagiarismCasesResponseEntity(plagiarismCases);
    }

    /**
     * Retrieves all plagiarism cases related to an exam for the instructor view.
     *
     * @param courseId the course id to which the exam belongs
     * @param examId   the id of the exam
     * @return all plagiarism cases of the course
     */
    @GetMapping("courses/{courseId}/exams/{examId}/plagiarism-cases/for-instructor")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<PlagiarismCase>> getPlagiarismCasesForExamForInstructor(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request to get all plagiarism cases for instructor in exam with id: {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authenticationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        var plagiarismCases = plagiarismCaseRepository.findByExamIdWithPlagiarismSubmissionsAndComparison(examId);
        if (!plagiarismCases.isEmpty()) {
            var plagiarismCase = plagiarismCases.getFirst();
            var exam = plagiarismCase.getExercise().getExerciseGroup().getExam();
            if (!exam.getCourse().getId().equals(courseId)) {
                throw new ConflictException("Exam with id " + exam.getId() + " is not related to the given course id " + courseId, ENTITY_NAME, "courseMismatch");
            }
        }
        return getPlagiarismCasesResponseEntity(plagiarismCases);
    }

    /**
     * Retrieves the plagiarism case with the given ID for the instructor view.
     *
     * @param courseId         the id of the course
     * @param plagiarismCaseId the id of the plagiarism case
     * @return all plagiarism cases of the course
     */
    @GetMapping("courses/{courseId}/plagiarism-cases/{plagiarismCaseId}/for-instructor")
    @EnforceAtLeastInstructor
    public ResponseEntity<PlagiarismCase> getPlagiarismCaseForInstructor(@PathVariable long courseId, @PathVariable long plagiarismCaseId) {
        log.debug("REST request to get plagiarism case for instructor with id: {}", plagiarismCaseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, userRepository.getUserWithGroupsAndAuthorities())) {
            throw new AccessForbiddenException("Only instructors of this course have access to its plagiarism cases.");
        }
        var plagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCaseId);
        return getPlagiarismCaseResponseEntity(plagiarismCase);
    }

    private ResponseEntity<PlagiarismCase> getPlagiarismCaseResponseEntity(PlagiarismCase plagiarismCase) {
        for (var submission : plagiarismCase.getPlagiarismSubmissions()) {
            submission.getPlagiarismComparison().getPlagiarismResult().setExercise(null);
            submission.getPlagiarismComparison().setSubmissionA(null);
            submission.getPlagiarismComparison().setSubmissionB(null);
        }
        return ResponseEntity.ok(plagiarismCase);
    }

    /**
     * GET /courses/{courseId}/exercises/{exerciseId}/plagiarism-cases-count : Counts the number of plagiarism cases for the given exercise.
     *
     * @param courseId   the id of the course
     * @param exerciseId the id of the exercise
     * @return the ResponseEntity with status 200 (OK) and with body the number of plagiarism cases for the given exercise
     */
    @GetMapping("courses/{courseId}/exercises/{exerciseId}/plagiarism-cases-count")
    @EnforceAtLeastInstructor
    public ResponseEntity<Long> getNumberOfPlagiarismCasesForExercise(@PathVariable long courseId, @PathVariable long exerciseId) {
        log.debug("REST request to get number of plagiarism cases for exercise with id: {}", exerciseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, null)) {
            throw new AccessForbiddenException("Only instructors of this course have access to its plagiarism cases.");
        }
        return ResponseEntity.ok(plagiarismCaseRepository.countByExerciseId(exerciseId));
    }

    /**
     * Update the verdict of the plagiarism case with the given ID.
     *
     * @param courseId             the id of the course
     * @param plagiarismCaseId     the id of the plagiarism case
     * @param plagiarismVerdictDTO the verdict of the plagiarism case
     * @return the updated plagiarism case
     */
    @PutMapping("courses/{courseId}/plagiarism-cases/{plagiarismCaseId}/verdict")
    @EnforceAtLeastInstructor
    public ResponseEntity<PlagiarismCase> savePlagiarismCaseVerdict(@PathVariable long courseId, @PathVariable long plagiarismCaseId,
            @RequestBody PlagiarismVerdictDTO plagiarismVerdictDTO) {
        log.debug("REST request to save plagiarism verdict for plagiarism case with id: {}", plagiarismCaseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authenticationCheckService.isAtLeastInstructorInCourse(course, userRepository.getUserWithGroupsAndAuthorities())) {
            throw new AccessForbiddenException("Only instructors of this course have access to its plagiarism cases.");
        }
        var plagiarismCase = plagiarismCaseService.updatePlagiarismCaseVerdict(plagiarismCaseId, plagiarismVerdictDTO);
        return ResponseEntity.ok(plagiarismCase);
    }

    /**
     * Retrieves the plagiarismCase related to an exercise for the student if the plagiarism comparison was confirmed and the student was notified
     *
     * @param courseId   the id of the course
     * @param exerciseId the id of the exercise
     * @return the plagiarism case id for the exercise and student if and only if the comparison was confirmed and the student was notified
     */
    @GetMapping("courses/{courseId}/exercises/{exerciseId}/plagiarism-case")
    @EnforceAtLeastStudent
    public ResponseEntity<PlagiarismCaseInfoDTO> getPlagiarismCaseForExerciseForStudent(@PathVariable long courseId, @PathVariable long exerciseId) {
        log.debug("REST request to all plagiarism cases for student and exercise with id: {}", exerciseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authenticationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        return ResponseEntity.ok(plagiarismCaseService.getPlagiarismCaseInfoForExerciseAndUser(exerciseId, user.getId()).orElse(null));
    }

    /**
     * Retrieves plagiarismCases related to given exercise ids for the student for the exercises where the plagiarism comparison was confirmed and the student was notified
     *
     * @param courseId    the id of the course
     * @param exerciseIds the ids of the exercises
     * @return a list of plagiarism case id and verdict values for the exercises only for the exercises where the comparison was confirmed and the student was notified
     */
    @GetMapping("courses/{courseId}/plagiarism-cases")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<Long, PlagiarismCaseInfoDTO>> getPlagiarismCasesForExercisesForStudent(@PathVariable long courseId,
            @RequestParam(name = "exerciseId") Set<Long> exerciseIds) {
        log.debug("REST request to all plagiarism cases for student and exercises with ids: {}", exerciseIds);
        Course course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authenticationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        List<PlagiarismCase> plagiarismCasePerExerciseList = plagiarismCaseRepository.findByStudentIdAndExerciseIdsWithPost(user.getId(), exerciseIds);
        for (PlagiarismCase plagiarismCase : plagiarismCasePerExerciseList) {
            var plagiarismCaseCourse = plagiarismCase.getExercise().getCourseViaExerciseGroupOrCourseMember();
            if (!plagiarismCaseCourse.getId().equals(courseId)) {
                throw new ConflictException("Plagiarism case with id " + plagiarismCase.getId() + " is not related to the given course id " + courseId, ENTITY_NAME,
                        "courseMismatch");
            }
        }
        Map<Long, PlagiarismCaseInfoDTO> plagiarismCaseInfoDTOs = plagiarismCasePerExerciseList.stream()
                // the following line is already checked in the SQL statement, but we want to ensure it 100%
                .filter(plagiarismCase -> plagiarismCase.getPost() != null)
                .collect(Collectors.toMap(plagiarismCase -> plagiarismCase.getExercise().getId(),
                        plagiarismCase -> new PlagiarismCaseInfoDTO(plagiarismCase.getId(), plagiarismCase.getVerdict(), plagiarismCase.isCreatedByContinuousPlagiarismControl()),
                        (case1, case2) -> case1));

        return ResponseEntity.ok(plagiarismCaseInfoDTOs);
    }

    private ResponseEntity<List<PlagiarismCase>> getPlagiarismCasesResponseEntity(List<PlagiarismCase> plagiarismCases) {
        for (var plagiarismCase : plagiarismCases) {
            if (plagiarismCase.getPost() != null) {
                plagiarismCase.getPost().setPlagiarismCase(null);
            }
            for (var submission : plagiarismCase.getPlagiarismSubmissions()) {
                submission.getPlagiarismComparison().getPlagiarismResult().setExercise(null);
                submission.getPlagiarismComparison().setSubmissionA(null);
                submission.getPlagiarismComparison().setSubmissionB(null);
            }
        }
        return ResponseEntity.ok(plagiarismCases);
    }

    /**
     * Retrieves the plagiarism case with the given ID for the student view.
     *
     * @param courseId         the id of the course
     * @param plagiarismCaseId the id of the plagiarism case
     * @return all plagiarism cases of the course
     */
    @GetMapping("courses/{courseId}/plagiarism-cases/{plagiarismCaseId}/for-student")
    @EnforceAtLeastStudent
    public ResponseEntity<PlagiarismCase> getPlagiarismCaseForStudent(@PathVariable long courseId, @PathVariable long plagiarismCaseId) {
        log.debug("REST request to get plagiarism case for student with id: {}", plagiarismCaseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authenticationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        var plagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsAndPlagiarismDetectionConfigElseThrow(plagiarismCaseId);
        if (!plagiarismCase.getStudent().getLogin().equals(user.getLogin())) {
            throw new AccessForbiddenException("Students only have access to plagiarism cases by which they are affected");
        }

        // hide potentially sensitive data
        plagiarismCase.getExercise().filterSensitiveInformation();
        Optional.ofNullable(plagiarismCase.getExercise().getPlagiarismDetectionConfig()).ifPresent(PlagiarismDetectionConfig::filterSensitiveInformation);

        return getPlagiarismCaseResponseEntity(plagiarismCase);
    }
}
