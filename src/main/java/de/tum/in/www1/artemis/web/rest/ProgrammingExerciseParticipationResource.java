package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseParticipationService;

@RestController
@RequestMapping("/api")
public class ProgrammingExerciseParticipationResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipationResource.class);

    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    private ResultRepository resultRepository;

    public ProgrammingExerciseParticipationResource(ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository, TemplateProgrammingExerciseParticipationRepository templateParticipationRepository,
            ResultRepository resultRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.templateParticipationRepository = templateParticipationRepository;
        this.studentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping(value = "/programming-exercises-student-participation/{participationId}/participation-with-latest-result-and-feedbacks")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Participation> getParticipationWithLatestResultForStudentParticipation(@PathVariable Long participationId) {
        Optional<ProgrammingExerciseStudentParticipation> participation = studentParticipationRepository.findByIdWithLatestResultAndFeedbacks(participationId);
        if (!participation.isPresent()) {
            return notFound();
        }
        if (!programmingExerciseParticipationService.canAccessParticipation(participation.get())) {
            return forbidden();
        }
        return ResponseEntity.ok(participation.get());
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping(value = "/programming-exercises-solution-participation/{participationId}/latest-result-with-feedbacks")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getLatestResultWithFeedbacksForSolutionParticipation(@PathVariable Long participationId) {
        Optional<SolutionProgrammingExerciseParticipation> participation = solutionParticipationRepository.findById(participationId);
        if (!participation.isPresent()) {
            return notFound();
        }
        return getLatestResultWithFeedbacks(participation.get());
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping(value = "/programming-exercises-template-participation/{participationId}/latest-result-with-feedbacks")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getLatestResultWithFeedbacksForTemplateParticipation(@PathVariable Long participationId) {
        Optional<TemplateProgrammingExerciseParticipation> participation = templateParticipationRepository.findById(participationId);
        if (!participation.isPresent()) {
            return notFound();
        }
        return getLatestResultWithFeedbacks(participation.get());
    }

    private ResponseEntity<Result> getLatestResultWithFeedbacks(ProgrammingExerciseParticipation participation) {
        if (!programmingExerciseParticipationService.canAccessParticipation(participation)) {
            return forbidden();
        }
        Optional<Result> result = resultRepository.findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDesc(participation.getId());
        if (!result.isPresent()) {
            return notFound();
        }
        // avoid circular serialization issue.
        result.get().setParticipation(null);
        return ResponseEntity.ok(result.get());
    }

}
