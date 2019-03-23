package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

/**
 * REST controller for managing Result.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
public class ResultResource {

    private final Logger log = LoggerFactory.getLogger(ResultResource.class);

    private static final String ENTITY_NAME = "result";

    @Value("${artemis.bamboo.authentication-token}")
    private String CI_AUTHENTICATION_TOKEN = "";

    private final ResultRepository resultRepository;
    private final ParticipationService participationService;
    private final ResultService resultService;
    private final ExerciseService exerciseService;
    private final AuthorizationCheckService authCheckService;
    private final FeedbackService feedbackService;
    private final UserService userService;
    private final ContinuousIntegrationService continuousIntegrationService;
    private final ProgrammingExerciseService programmingExerciseService;

    public ResultResource(UserService userService,
                          ResultRepository resultRepository,
                          ParticipationService participationService,
                          ResultService resultService,
                          AuthorizationCheckService authCheckService,
                          FeedbackService feedbackService,
                          ExerciseService exerciseService,
                          ContinuousIntegrationService continuousIntegrationService,
                          ProgrammingExerciseService programmingExerciseService) {

        this.userService = userService;
        this.resultRepository = resultRepository;
        this.participationService = participationService;
        this.resultService = resultService;
        this.feedbackService = feedbackService;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseService = programmingExerciseService;
    }

    /**
     * POST  /results : Create a new manual result for a programming exercise (Do NOT use it for other exercise types)
     * NOTE: we deviate from the standard URL scheme to avoid conflicts with a different POST request on results
     *
     * @param result the result to create
     * @return the ResponseEntity with status 201 (Created) and with body the new result, or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/manual-results")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> createResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to save Result : {}", result);
        Participation participation = result.getParticipation();
        Course course = participation.getExercise().getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!userHasPermissions(course, user)) return forbidden();

        if (result.getId() != null) {
            throw new BadRequestAlertException("A new result cannot already have an ID.", ENTITY_NAME, "idexists");
        } else if (result.getResultString() == null) {
            throw new BadRequestAlertException("Result string is required.", ENTITY_NAME, "resultStringNull");
        } else if (result.getScore() == null) {
            throw new BadRequestAlertException("Score is required.", ENTITY_NAME, "scoreNull");
        } else if(result.getScore() != 100 && result.isSuccessful()) {
            throw new BadRequestAlertException("Only result with score 100% can be successful.", ENTITY_NAME, "scoreAndSuccessfulNotMatching");
        } else if(!result.getFeedbacks().isEmpty() && result.getFeedbacks().stream()
                .filter(feedback -> feedback.getText() == null).count() != 0) {
            throw new BadRequestAlertException("In case feedback is present, feedback text and detail text are mandatory.", ENTITY_NAME, "feedbackTextOrDetailTextNull");
        }

        resultService.createNewResult(result, true);

        return ResponseEntity.created(new URI("/api/results/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * POST  /results/:planKey : Notify the application about a new build result for a programming exercise
     * This API is invoked by the CI Server at the end of the build/test result and does not need any security
     *
     * @param planKey the plan key of the plan which is notifying about a new result
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "/results/{planKey}")
    @Transactional
    @Deprecated
    public ResponseEntity<?> notifyResultOld(@PathVariable("planKey") String planKey) {
        if (planKey.toLowerCase().endsWith("base") || planKey.toLowerCase().endsWith("solution")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Optional<Participation> participation = getParticipation(planKey);
        if (participation.isPresent()) {
            resultService.onResultNotifiedOld(participation.get());
            return ResponseEntity.ok().build();
        } else {
            return notFound();
        }
    }

    @PostMapping(value = Constants.NEW_RESULT_RESOURCE_PATH)
    @Transactional
    public ResponseEntity<?> notifyResultNew(@RequestHeader("Authorization") String token, @RequestBody Object requestBody) {
        if (token == null || !token.equals(CI_AUTHENTICATION_TOKEN)) {
            return forbidden(); // Only allow endpoint when using correct token
        }

        try {
            String planKey = continuousIntegrationService.getPlanKey(requestBody);
            Optional<Participation> optionalParticipation = getParticipation(planKey);
            if (optionalParticipation.isPresent()) {
                Participation participation = optionalParticipation.get();
                if (planKey.toLowerCase().contains("-base")) { // TODO: transfer this into constants
                    participation.setExercise(programmingExerciseService.getExerciseForTemplateParticipation(participation));
                } else if (planKey.toLowerCase().contains("-solution")) { // TODO: transfer this into constants
                    participation.setExercise(programmingExerciseService.getExerciseForSolutionParticipation(participation));
                }
                resultService.onResultNotifiedNew(participation, requestBody);
                return ResponseEntity.ok().build();
            } else {
                //return ok so that Bamboo does not think it was an error
                return ResponseEntity.ok().build();
            }

        } catch (Exception e) {
            return badRequest();
        }

    }

    private Optional<Participation> getParticipation(String planKey) {
        List<Participation> participations = participationService.findByBuildPlanIdAndInitializationState(planKey, InitializationState.INITIALIZED);
        Optional<Participation> participation = Optional.empty();
        if (participations.size() > 0) {
            participation = Optional.of(participations.get(0));
            if (participations.size() > 1) {
                //in the rare case of multiple participations, take the latest one.
                for (Participation otherParticipation : participations) {
                    if (otherParticipation.getInitializationDate().isAfter(participation.get().getInitializationDate())) {
                        participation = Optional.of(otherParticipation);
                    }
                }
            }
        }

        return participation;
    }


    /**
     * PUT  /results : Updates an existing result.
     *
     * @param result the result to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated result,
     * or with status 400 (Bad Request) if the result is not valid,
     * or with status 500 (Internal Server Error) if the result couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/manual-results")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to update Result : {}", result);
        Participation participation = result.getParticipation();
        Course course = participation.getExercise().getCourse();
        if (!userHasPermissions(course)) return forbidden();
        if (result.getId() == null) {
            return createResult(result);
        }
        // have a look how quiz-exercise handles this case with the contained questions
        resultRepository.save(result);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * GET  /courses/:courseId/exercises/:exerciseId/participations/:participationId/results : get all the results for "id" participation.
     *
     * @param courseId        only included for API consistency, not actually used
     * @param exerciseId      only included for API consistency, not actually used
     * @param participationId the id of the participation for which to retrieve the results
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @GetMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participations/{participationId}/results")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Result>> getResultsForParticipation(@PathVariable Long courseId,
                                                                   @PathVariable Long exerciseId,
                                                                   @PathVariable Long participationId,
                                                                   @RequestParam(defaultValue = "true") boolean showAllResults,
                                                                   @RequestParam(defaultValue = "false") boolean ratedOnly) {
        log.debug("REST request to get Results for Participation : {}", participationId);

        List<Result> results = new ArrayList<>();
        Participation participation = participationService.findOne(participationId);

        if (participation.getStudent() == null) {
            // If the student is null, then participation is a template/solution participation -> check for instructor role
            if (!authCheckService.isAtLeastInstructorForCourse(participation.getExercise().getCourse(), null)) {
                return forbidden();
            }
        } else {
            if (!authCheckService.isOwnerOfParticipation(participation)) {
                Course course = participation.getExercise().getCourse();
                if (!userHasPermissions(course)) return forbidden();
            }
        }

        if (participation != null) {
            // if exercise is quiz => only give out results if quiz is over
            if (participation.getExercise() instanceof QuizExercise) {
                QuizExercise quizExercise = (QuizExercise) participation.getExercise();
                if (quizExercise.shouldFilterForStudents()) {
                    // return empty list
                    return ResponseEntity.ok().body(results);
                }
            }
            if (showAllResults) {
                if (ratedOnly) {
                    results = resultRepository.findByParticipationIdAndRatedOrderByCompletionDateDesc(participationId, true);
                } else {
                    results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId);
                }
            } else {
                if (ratedOnly) {
                    results = resultRepository.findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(participationId, true)
                        .map(Arrays::asList)
                        .orElse(new ArrayList<>());
                } else {
                    results = resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participationId)
                        .map(Arrays::asList)
                        .orElse(new ArrayList<>());
                }

            }
        }
        //remove unnecessary elements in the json response
        results.forEach(result -> {
            result.getParticipation().setExercise(null);
            result.getParticipation().setResults(null);
            result.getParticipation().setSubmissions(null);
        });
        return ResponseEntity.ok().body(results);
    }

    /**
     * GET  /courses/:courseId/exercises/:exerciseId/results : get the successful results for an exercise, ordered ascending by build completion date.
     *
     * @param courseId   only included for API consistency, not actually used
     * @param exerciseId the id of the exercise for which to retrieve the results
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @GetMapping(value = "/courses/{courseId}/exercises/{exerciseId}/results")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Result>> getResultsForExercise(@PathVariable Long courseId,
                                                              @PathVariable Long exerciseId,
                                                              @RequestParam(defaultValue = "false") boolean ratedOnly,
                                                              @RequestParam(defaultValue = "false") boolean withSubmissions,
                                                              @RequestParam(defaultValue = "false") boolean withAssessors) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get Results for Exercise : {}", exerciseId);

        //TODO: why do we even have this call, when we load all participations below anyway?
        Exercise exercise = exerciseService.findOneLoadParticipations(exerciseId);
        Course course = exercise.getCourse();
        if (!userHasPermissions(course)) return forbidden();

        //TODO use rated only in case the given request param is true

        List<Result> results = new ArrayList<>();

        List<Participation> participations = participationService.findByExerciseIdWithEagerResults(exerciseId);

        for (Participation participation : participations) {
            // Filter out participations without Students
            // These participations are used e.g. to store template and solution build plans in programming exercises
            if (participation.getStudent() == null) {
                continue;
            }

            Result relevantResult;
            if (ratedOnly == true) {
                relevantResult = exercise.findLatestRatedResultWithCompletionDate(participation);
            }
            else {
                relevantResult = participation.findLatestResult();
            }
            if (relevantResult == null) {
                continue;
            }

            relevantResult.setSubmissionCount((long) participation.getResults().size());
            results.add(relevantResult);
        }

        log.info("getResultsForExercise took " + (System.currentTimeMillis() - start) + "ms for " + results.size() + " results.");

        if (withSubmissions) {
            results.forEach(result -> {
                Hibernate.initialize(result.getSubmission()); // eagerly load the association
            });
            results = results.stream().filter(result -> result.getSubmission() != null && result.getSubmission().isSubmitted()).collect(Collectors.toList());
        }

        if (withAssessors) {
            results.forEach(result -> {
                Hibernate.initialize(result.getAssessor()); // eagerly load the association
            });
        }

        //remove unnecessary elements in the json response
        results.forEach(result -> {
            result.getParticipation().setResults(null);
            result.getParticipation().setSubmissions(null);
            result.getParticipation().setExercise(null);
        });

        return ResponseEntity.ok().body(results);
    }

    /**
     * GET  /results/:id : get the "id" result.
     *
     * @param resultId the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping("/results/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getResult(@PathVariable Long resultId) {
        log.debug("REST request to get Result : {}", resultId);
        Optional<Result> result = resultRepository.findById(resultId);
        if (result.isPresent()) {
            Participation participation = result.get().getParticipation();
            Course course = participation.getExercise().getCourse();
            if (!userHasPermissions(course)) return forbidden();
        }
        return result.map(foundResult -> new ResponseEntity<>(foundResult, HttpStatus.OK))
            .orElse(notFound());
    }

    /**
     * GET  /results/:id/details : get the build result details from Bamboo for the "id" result.
     * This method is only invoked if the result actually includes details (e.g. feedback or build errors)
     *
     * @param resultId the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping(value = "/results/{resultId}/details")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<List<Feedback>> getResultDetails(@PathVariable Long resultId) {
        log.debug("REST request to get Result : {}", resultId);
        Optional<Result> result = resultRepository.findById(resultId);
        if (!result.isPresent()) {
            return notFound();
        }
        Participation participation = result.get().getParticipation();
        Course course = participation.getExercise().getCourse();

        if (participation.getStudent() == null) {
            // If the student is null, then we participation is a template/solution participation -> check for instructor role
            if (!authCheckService.isAtLeastInstructorForCourse(participation.getExercise().getCourse(), null)) {
                return forbidden();
            }
        } else {
            if (!authCheckService.isOwnerOfParticipation(participation)) {
                if (!userHasPermissions(course)) return forbidden();
            }
        }

        try {
            List<Feedback> feedbackItems = feedbackService.getFeedbackForBuildResult(result.get());
            return Optional.ofNullable(feedbackItems)
                .map(resultDetails -> new ResponseEntity<>(feedbackItems, HttpStatus.OK))
                .orElse(notFound());
        } catch (Exception e) {
            log.error("REST request to get Result failed : {}", resultId, e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * DELETE  /results/:id : delete the "id" result.
     *
     * @param resultId the id of the result to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/results/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteResult(@PathVariable Long resultId) {
        log.debug("REST request to delete Result : {}", resultId);
        Optional<Result> result = resultRepository.findById(resultId);
        if (result.isPresent()) {
            Participation participation = result.get().getParticipation();
            Course course = participation.getExercise().getCourse();
            if (!userHasPermissions(course)) return forbidden();
            resultRepository.deleteById(resultId);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, resultId.toString())).build();
        }
        return  ResponseEntity.notFound().build();
    }

    /**
     * GET  /results/submission/{submissionId} : get the result for a submission id
     *
     * @param submissionId the id of the submission
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @GetMapping(value = "/results/submission/{submissionId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getResultForSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get Result for submission : {}", submissionId);
        Optional<Result> result = resultRepository.findDistinctBySubmissionId(submissionId);
        return ResponseUtil.wrapOrNotFound(result);
    }

    private boolean userHasPermissions(Course course, User user) {
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return false;
        }
        return true;
    }
    
    private boolean userHasPermissions(Course course) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return userHasPermissions(course, user);
    }
}
