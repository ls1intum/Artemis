package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.ResultListener;
import de.tum.cit.aet.artemis.assessment.dto.score.ScoreDTO;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeInformationDTO;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.dto.StudentDTO;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/assessment/")
public class ParticipantScoreResource {

    private static final Logger log = LoggerFactory.getLogger(ParticipantScoreResource.class);

    private final ParticipantScoreService participantScoreService;

    private final AuthorizationCheckService authorizationCheckService;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final CourseRepository courseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final UserRepository userRepository;

    public ParticipantScoreResource(AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository, Optional<ExamRepositoryApi> examRepositoryApi,
            ParticipantScoreService participantScoreService, StudentParticipationRepository studentParticipationRepository, UserRepository userRepository) {
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.examRepositoryApi = examRepositoryApi;
        this.participantScoreService = participantScoreService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.userRepository = userRepository;
    }

    /**
     * GET /courses/:courseId/course-scores gets the course scores of the course
     * <p>
     * This method represents a server based way to calculate a students achieved points / score in a course.
     * <p>
     * Currently both this server based calculation method and the traditional client side calculation method is used
     * side-by-side in course-scores.component.ts.
     * <p>
     * The goal is to switch completely to this much faster server based calculation if the {@link ResultListener}
     * has been battle tested enough.
     *
     * @param courseId the id of the course for which to calculate the course scores
     * @return list of scores for every member of the course
     */
    @GetMapping("courses/{courseId}/course-scores")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<ScoreDTO>> getScoresOfCourse(@PathVariable Long courseId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get course scores for course : {}", courseId);
        Course course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        List<ScoreDTO> scoreDTOS = participantScoreService.calculateCourseScores(course);
        log.info("getScoresOfCourse took {}ms", System.currentTimeMillis() - start);
        return ResponseEntity.ok().body(scoreDTOS);
    }

    /**
     * GET /exams/:examId/exam-scores gets the exam scores of the exam
     * <p>
     * This method represents a server based way to calculate a students achieved points / score in an exam.
     * <p>
     * Currently both this server based calculation method and the traditional client side calculation method is used
     * side-by-side in exam-scores.component.ts.
     * <p>
     * The goal is to switch completely to this much faster server based calculation if the {@link ResultListener}
     * has been battle tested enough.
     *
     * @param examId the id of the exam for which to calculate the exam scores
     * @return list of scores for every registered user in the exam or 404 not found if scores are empty
     */
    @GetMapping("exams/{examId}/exam-scores")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<ScoreDTO>> getScoresOfExam(@PathVariable Long examId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get exam scores for exam : {}", examId);
        ExamRepositoryApi api = examRepositoryApi.orElseThrow(() -> new ExamApiNotPresentException(ExamRepositoryApi.class));

        Exam exam = api.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(examId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, exam.getCourse(), null);
        List<ScoreDTO> scoreDTOS = participantScoreService.calculateExamScores(exam);
        log.info("getScoresOfExam took {}ms", System.currentTimeMillis() - start);
        return scoreDTOS.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok().body(scoreDTOS);
    }

    /**
     * GET /courses/:courseId/grade-scores : get all grade information (scores) for a course
     *
     * @param courseId The id of the course
     * @return a {@link CourseGradeInformationDTO}
     */
    @GetMapping("courses/{courseId}/grade-scores")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<CourseGradeInformationDTO> getGradeScoresForCourse(@PathVariable long courseId) {
        log.info("REST request to get grade scores for Course {}", courseId);
        long start = System.nanoTime();
        Set<CourseGradeScoreDTO> gradeScores = studentParticipationRepository.findGradeScoresForAllExercisesForCourse(courseId);
        Set<Long> userIds = gradeScores.stream().map(CourseGradeScoreDTO::userId).collect(Collectors.toSet());
        List<StudentDTO> students = userIds.isEmpty() ? List.of() : userRepository.findAllStudentsByIdIn(userIds);
        log.info("Found {} grade scores, {} students, in {}", gradeScores.size(), students.size(), TimeLogUtil.formatDurationFrom(start));

        return ResponseEntity.ok().body(new CourseGradeInformationDTO(gradeScores, students));
    }
}
