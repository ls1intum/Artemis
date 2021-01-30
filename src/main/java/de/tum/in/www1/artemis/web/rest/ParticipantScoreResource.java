package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ParticipantScoreRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreDTO;

@RestController
@RequestMapping("/api")
public class ParticipantScoreResource {

    private final Logger log = LoggerFactory.getLogger(ParticipantScoreResource.class);

    private final ParticipantScoreRepository participantScoreRepository;

    private final CourseRepository courseRepository;

    private final ExamRepository examRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public ParticipantScoreResource(ParticipantScoreRepository participantScoreRepository, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository,
            ExamRepository examRepository) {
        this.participantScoreRepository = participantScoreRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
    }

    /**
     * GET /courses/:courseId/participant-scores  gets the participant scores of the course
     *
     * @param courseId   the id of the course for which to get the participant score
     * @param pageable   pageable object
     * @param getUnpaged if set all participant scores of the course will be loaded (paging deactivated)
     * @return the ResponseEntity with status 200 (OK) and with the participant scores in the body
     */
    @GetMapping("/courses/{courseId}/participant-scores")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ParticipantScoreDTO>> getParticipantScoresOfCourse(@PathVariable Long courseId, Pageable pageable,
            @RequestParam(value = "getUnpaged", required = false, defaultValue = "false") boolean getUnpaged) {
        log.debug("REST request to get participant scores for course : {}", courseId);
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        if (course == null) {
            return notFound();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }
        Set<Exercise> exercisesOfCourse = course.getExercises();
        if (getUnpaged) {
            pageable = Pageable.unpaged();
        }

        List<ParticipantScoreDTO> results = participantScoreRepository.findAllByExerciseIn(exercisesOfCourse, pageable).stream()
                .map(participantScore -> ParticipantScoreDTO.generateFromParticipantScore(participantScore)).collect(Collectors.toList());
        return ResponseEntity.ok().body(results);
    }

    /**
     * GET /courses/:examId/participant-scores  gets the participant scores of the exam
     *
     * @param examId     the id of the exam for which to get the participant score
     * @param pageable   pageable object
     * @param getUnpaged if set all participant scores of the exam will be loaded (paging deactivated)
     * @return the ResponseEntity with status 200 (OK) and with the participant scores in the body
     */
    @GetMapping("/exams/{examId}/participant-scores")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ParticipantScoreDTO>> getParticipantScoresOfExam(@PathVariable Long examId, Pageable pageable,
            @RequestParam(value = "getUnpaged", required = false, defaultValue = "false") boolean getUnpaged) {
        log.debug("REST request to get participant scores for course : {}", examId);
        Optional<Exam> examOptional = examRepository.findWithExerciseGroupsAndExercisesById(examId);
        if (examOptional.isEmpty()) {
            return notFound();
        }
        Exam exam = examOptional.get();
        if (!authorizationCheckService.isAtLeastInstructorInCourse(exam.getCourse(), null)) {
            return forbidden();
        }
        Set<Exercise> exercisesOfExam = new HashSet<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            exercisesOfExam.addAll(exerciseGroup.getExercises());
        }
        if (getUnpaged) {
            pageable = Pageable.unpaged();
        }
        List<ParticipantScoreDTO> results = participantScoreRepository.findAllByExerciseIn(exercisesOfExam, pageable).stream()
                .map(participantScore -> ParticipantScoreDTO.generateFromParticipantScore(participantScore)).collect(Collectors.toList());
        return ResponseEntity.ok().body(results);
    }

}
