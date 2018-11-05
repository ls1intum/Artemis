package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST controller for managing Result.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
public class ResultResource {

    private final Logger log = LoggerFactory.getLogger(ResultResource.class);

    private static final String ENTITY_NAME = "result";

    private final ResultRepository resultRepository;
    private final Optional<LtiService> ltiService;
    private final CourseService courseService;
    private final ParticipationService participationService;
    private final ResultService resultService;
    private final ExerciseService exerciseService;
    private final AuthorizationCheckService authCheckService;
    private final FeedbackService feedbackService;
    private final UserService userService;

    public ResultResource(UserService userService,
                          ResultRepository resultRepository,
                          Optional<LtiService> ltiService,
                          ParticipationService participationService,
                          ResultService resultService,
                          AuthorizationCheckService authCheckService,
                          FeedbackService feedbackService,
                          ExerciseService exerciseService,
                          CourseService courseService) {

        this.userService = userService;
        this.resultRepository = resultRepository;
        this.ltiService = ltiService;
        this.participationService = participationService;
        this.resultService = resultService;
        this.courseService = courseService;
        this.feedbackService = feedbackService;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
    }

    /**
     * POST  /results : Create a new manual result.
     *
     * @param result the result to create
     * @return the ResponseEntity with status 201 (Created) and with body the new result, or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/results")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> createResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to save Result : {}", result);
        Participation participation = result.getParticipation();
        Course course = participation.getExercise().getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
             !authCheckService.isInstructorInCourse(course, user) &&
             !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

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

        if(!result.getFeedbacks().isEmpty()) {
            result.setHasFeedback(true);
        }

        Result savedResult = resultRepository.save(result);
        try {
            participation.addResult(savedResult);
            participationService.save(participation);
        } catch (NullPointerException e) {
            log.warn("Unable to load result list for participation");
        }
        result.getFeedbacks().forEach(feedback -> {
            feedback.setResult(savedResult);
            feedbackService.save(feedback);
        });

        ltiService.ifPresent(ltiService -> ltiService.onNewBuildResult(savedResult.getParticipation()));
        return ResponseEntity.created(new URI("/api/results/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * POST  /results/:planKey : Notify the application about a new build result for a programming exercise
     * This API is invoked by the CI Server at the end of the build/test result
     *
     * @param planKey the plan key of the plan which is notifying about a new result
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "/results/{planKey}")
    @Transactional
    public ResponseEntity<?> notifyResult(@PathVariable("planKey") String planKey) {
        if (planKey.toLowerCase().endsWith("base") || planKey.toLowerCase().endsWith("solution")) {
            //TODO: can we do this check more precise and compare it with the saved values from the exercises?
            //In the future we also might want to save these results in the database
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        List<Participation> participations = participationService.findByBuildPlanIdAndInitializationState(planKey, InitializationState.INITIALIZED);
        if (participations.size() > 0) {
            Participation participation = participations.get(0);
            if (participations.size() > 1) {
                //in the rare case of multiple participations, take the latest one.
                for (Participation otherParticipation : participations) {
                    if (otherParticipation.getInitializationDate().isAfter(participation.getInitializationDate())) {
                        participation = otherParticipation;
                    }
                }
            }
            resultService.onResultNotified(participation);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
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
    @PutMapping("/results")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> updateResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to update Result : {}", result);
        Participation participation = result.getParticipation();
        Course course = participation.getExercise().getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
             !authCheckService.isInstructorInCourse(course, user) &&
             !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (result.getId() == null) {
            return createResult(result);
        }
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
    @Timed
    public ResponseEntity<List<Result>> getResultsForParticipation(@PathVariable Long courseId,
                                                                   @PathVariable Long exerciseId,
                                                                   @PathVariable Long participationId,
                                                                   @RequestParam(defaultValue = "true") boolean showAllResults,
                                                                   @RequestParam(defaultValue = "false") boolean ratedOnly) {
        log.debug("REST request to get Results for Participation : {}", participationId);

        List<Result> results = new ArrayList<>();
        Participation participation = participationService.findOne(participationId);

        if (!authCheckService.isOwnerOfParticipation(participation)) {
            Course course = participation.getExercise().getCourse();
            User user = userService.getUserWithGroupsAndAuthorities();
             if(!authCheckService.isTeachingAssistantInCourse(course, user) &&
                !authCheckService.isInstructorInCourse(course, user) &&
                !authCheckService.isAdmin()) {
                 return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
    @Timed
    @Transactional(readOnly = true)
    public ResponseEntity<List<Result>> getResultsForExercise(@PathVariable Long courseId,
                                                              @PathVariable Long exerciseId,
                                                              @RequestParam(defaultValue = "false") boolean ratedOnly,
                                                              @RequestParam(defaultValue = "false") boolean withSubmissions,
                                                              @RequestParam(defaultValue = "false") boolean withAssessors) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get Results for Exercise : {}", exerciseId);

        Exercise exercise = exerciseService.findOneLoadParticipations(exerciseId);
        Course course = exercise.getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
             !authCheckService.isInstructorInCourse(course, user) &&
             !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        //TODO use rated only in case the given request param is true

        List<Result> results = new ArrayList<>();

        List<Participation> participations = participationService.findByExerciseIdWithEagerResults(exerciseId);

        for (Participation participation : participations) {

            Result relevantResult = exercise.findLatestRelevantResult(participation);

            if (relevantResult == null) {
                continue;
            }

            relevantResult.setSubmissionCount(new Long(participation.getResults().size()));
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
        });

        return ResponseEntity.ok().body(results);
    }

    /**
     * GET  /courses/:courseId/results : get the successful results for a course, ordered ascending by build completion date.
     *
     * @param courseId the id of the course for which to retrieve the results
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @GetMapping(value = "/courses/{courseId}/results")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<List<Result>> getResultsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get Results for Course : {}", courseId);
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
             !authCheckService.isInstructorInCourse(course, user) &&
             !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<Result> results = resultRepository.findEarliestSuccessfulResultsForCourse(courseId);

        //remove unnecessary elements in the json response
        results.forEach(result -> {
            result.getParticipation().setExercise(null);
            result.getParticipation().setResults(null);
            result.getParticipation().setSubmissions(null);
        });

        return ResponseEntity.ok().body(results);
    }


    /**
     * GET  /results/:id : get the "id" result.
     *
     * @param id the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping("/results/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> getResult(@PathVariable Long id) {
        log.debug("REST request to get Result : {}", id);
        Optional<Result> result = resultRepository.findById(id);
        if (result.isPresent()) {
            Participation participation = result.get().getParticipation();
            Course course = participation.getExercise().getCourse();
            User user = userService.getUserWithGroupsAndAuthorities();
            if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
                !authCheckService.isInstructorInCourse(course, user) &&
                !authCheckService.isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return result.map(foundResult -> new ResponseEntity<>(foundResult, HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * GET  /results/:id/details : get the build result details from Bamboo for the "id" result.
     * This method is only invoked if the result actually includes details (e.g. feedback or build errors)
     *
     * @param id the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping(value = "/results/{id}/details")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    @Transactional
    public ResponseEntity<List<Feedback>> getResultDetails(@PathVariable Long id, @RequestParam(required = false) String username, Authentication authentication) {
        log.debug("REST request to get Result : {}", id);
        Optional<Result> result = resultRepository.findById(id);
        if (!result.isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Participation participation = result.get().getParticipation();
        Course course = participation.getExercise().getCourse();
        if (!authCheckService.isOwnerOfParticipation(participation)) {

            User user = userService.getUserWithGroupsAndAuthorities();
            if(!authCheckService.isTeachingAssistantInCourse(course, user) &&
                !authCheckService.isInstructorInCourse(course, user) &&
                !authCheckService.isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        try {
            List<Feedback> feedbackItems = feedbackService.getFeedbackForBuildResult(result.get());
            return Optional.ofNullable(feedbackItems)
                .map(resultDetails -> new ResponseEntity<>(feedbackItems, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
        } catch (Exception e) {
            log.error("REST request to get Result failed : {}", id, e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * DELETE  /results/:id : delete the "id" result.
     *
     * @param id the id of the result to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/results/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteResult(@PathVariable Long id) {
        log.debug("REST request to delete Result : {}", id);
        Optional<Result> result = resultRepository.findById(id);
        if (result.isPresent()) {
            Participation participation = result.get().getParticipation();
            Course course = participation.getExercise().getCourse();
            User user = userService.getUserWithGroupsAndAuthorities();
            if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
                !authCheckService.isInstructorInCourse(course, user) &&
                !authCheckService.isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            resultRepository.deleteById(id);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
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
    @Timed
    public ResponseEntity<Result> getResultForSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get Result for submission : {}", submissionId);
        Optional<Result> result = resultRepository.findDistinctBySubmissionId(submissionId);
        return ResponseUtil.wrapOrNotFound(result);
    }
}
