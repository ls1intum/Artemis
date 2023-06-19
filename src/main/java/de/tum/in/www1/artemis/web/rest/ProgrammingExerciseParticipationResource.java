package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationAuthorizationCheckService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@RestController
@RequestMapping("/api")
public class ProgrammingExerciseParticipationResource {

    private static final String ENTITY_NAME = "programmingExerciseParticipation";

    private final ParticipationRepository participationRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ResultRepository resultRepository;

    private final ProgrammingSubmissionService submissionService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    private final ResultService resultService;

    public ProgrammingExerciseParticipationResource(ProgrammingExerciseParticipationService programmingExerciseParticipationService, ResultRepository resultRepository,
            ParticipationRepository participationRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingSubmissionService submissionService, ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            ResultService resultService, ParticipationAuthorizationCheckService participationAuthCheckService) {
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.participationRepository = participationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.resultRepository = resultRepository;
        this.submissionService = submissionService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.resultService = resultService;
        this.participationAuthCheckService = participationAuthCheckService;
    }

    /**
     * Get the given student participation with its latest result and feedbacks.
     *
     * @param participationId for which to retrieve the student participation with latest result and feedbacks.
     * @return the ResponseEntity with status 200 (OK) and the participation with its latest result in the body.
     */
    @GetMapping("/programming-exercise-participations/{participationId}/student-participation-with-latest-result-and-feedbacks")
    @EnforceAtLeastStudent
    public ResponseEntity<ProgrammingExerciseStudentParticipation> getParticipationWithLatestResultForStudentParticipation(@PathVariable Long participationId) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository
                .findStudentParticipationWithLatestResultAndFeedbacksAndRelatedSubmissions(participationId)
                .orElseThrow(() -> new EntityNotFoundException("Participation", participationId));
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);

        // hide details that should not be shown to the students
        resultService.filterSensitiveInformationIfNecessary(participation, participation.getResults());
        return ResponseEntity.ok(participation);
    }

    /**
     * Get the latest result for a given programming exercise participation including its result.
     *
     * @param participationId for which to retrieve the programming exercise participation with latest result and feedbacks.
     * @param withSubmission  flag determining whether the corresponding submission should also be returned
     * @return the ResponseEntity with status 200 (OK) and the latest result with feedbacks in its body, 404 if the participation can't be found or 403 if the user is not allowed
     *         to access the participation.
     */
    @GetMapping(value = "/programming-exercise-participations/{participationId}/latest-result-with-feedbacks")
    @EnforceAtLeastStudent
    public ResponseEntity<Result> getLatestResultWithFeedbacksForProgrammingExerciseParticipation(@PathVariable Long participationId,
            @RequestParam(defaultValue = "false") boolean withSubmission) {
        var participation = participationRepository.findByIdElseThrow(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);

        Optional<Result> result = resultRepository.findLatestResultWithFeedbacksForParticipation(participation.getId(), withSubmission);
        result.ifPresent(value -> resultService.filterSensitiveInformationIfNecessary(participation, value));

        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok(null));
    }

    /**
     * Check if the participation has a result yet.
     *
     * @param participationId of the participation to check.
     * @return the ResponseEntity with status 200 (OK) with true if there is a result, otherwise false.
     */
    @GetMapping(value = "/programming-exercise-participations/{participationId}/has-result")
    @EnforceAtLeastStudent
    public ResponseEntity<Boolean> checkIfParticipationHashResult(@PathVariable Long participationId) {
        boolean hasResult = resultRepository.existsByParticipationId(participationId);
        return ResponseEntity.ok(hasResult);
    }

    /**
     * GET /programming-exercise-participation/:id/latest-pending-submission : get the latest pending submission for the participation.
     * A pending submission is one that does not have a result yet.
     *
     * @param participationId the id of the participation get the latest submission for
     * @param lastGraded      if true will not try to find the latest pending submission, but the latest GRADED pending submission.
     * @return the ResponseEntity with the last pending submission if it exists or null with status Ok (200). Will return notFound (404) if there is no participation for the given
     *         id and forbidden (403) if the user is not allowed to access the participation.
     */
    @GetMapping("/programming-exercise-participations/{participationId}/latest-pending-submission")
    @EnforceAtLeastStudent
    public ResponseEntity<ProgrammingSubmission> getLatestPendingSubmission(@PathVariable Long participationId, @RequestParam(defaultValue = "false") boolean lastGraded) {
        Optional<ProgrammingSubmission> submissionOpt;
        try {
            submissionOpt = submissionService.getLatestPendingSubmission(participationId, lastGraded);
        }
        catch (IllegalArgumentException ex) {
            throw new EntityNotFoundException("participation", participationId);
        }
        // Remove participation, is not needed in the response.
        submissionOpt.ifPresent(submission -> submission.setParticipation(null));
        return ResponseEntity.ok(submissionOpt.orElse(null));
    }

    /**
     * For every student participation of a programming exercise, try to find a pending submission.
     *
     * @param exerciseId for which to search pending submissions.
     * @return a Map of {[participationId]: ProgrammingSubmission | null}. Will contain an entry for every student participation of the exercise and a submission object if a
     *         pending submission exists or null if not.
     */
    @GetMapping("/programming-exercises/{exerciseId}/latest-pending-submissions")
    @EnforceAtLeastTutor
    public ResponseEntity<Map<Long, Optional<ProgrammingSubmission>>> getLatestPendingSubmissionsByExerciseId(@PathVariable Long exerciseId) {
        ProgrammingExercise programmingExercise;
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise)) {
            throw new AccessForbiddenException("exercise", exerciseId);
        }
        Map<Long, Optional<ProgrammingSubmission>> pendingSubmissions = submissionService.getLatestPendingSubmissionsForProgrammingExercise(exerciseId);
        // Remove unnecessary data to make response smaller (exercise, student of participation).
        pendingSubmissions = pendingSubmissions.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            Optional<ProgrammingSubmission> submissionOpt = entry.getValue();
            // Remove participation, is not needed in the response.
            submissionOpt.ifPresent(submission -> submission.setParticipation(null));
            return submissionOpt;
        }));
        return ResponseEntity.ok(pendingSubmissions);
    }

    /**
     * Resets the specified repository to either the exercise template or graded participation
     *
     * @param participationId       the id of the programming participation that should be resetted
     * @param gradedParticipationId optional parameter that specifies that the repository should be set to the graded participation instead of the exercise template
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("/programming-exercise-participations/{participationId}/reset-repository")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> resetRepository(@PathVariable Long participationId, @RequestParam(required = false) Long gradedParticipationId)
            throws GitAPIException, IOException {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByStudentParticipationIdWithTemplateParticipation(participationId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise for Participation", participationId));
        participation.setProgrammingExercise(exercise);

        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
        if (participation.isLocked()) {
            throw new AccessForbiddenException("participation", participationId);
        }
        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("Cannot reset repository in an exam", ENTITY_NAME, "noRepoResetInExam");
        }

        VcsRepositoryUrl sourceURL;
        if (gradedParticipationId != null) {
            ProgrammingExerciseStudentParticipation gradedParticipation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(gradedParticipationId);
            participationAuthCheckService.checkCanAccessParticipationElseThrow(gradedParticipation);

            sourceURL = gradedParticipation.getVcsRepositoryUrl();
        }
        else {
            sourceURL = exercise.getVcsTemplateRepositoryUrl();
        }

        programmingExerciseParticipationService.resetRepository(participation.getVcsRepositoryUrl(), sourceURL);

        return ResponseEntity.ok().build();
    }
}
