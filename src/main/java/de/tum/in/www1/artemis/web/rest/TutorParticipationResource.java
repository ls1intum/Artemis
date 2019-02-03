package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

/**
 * REST controller for managing TutorParticipation.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasRole('ADMIN')")
public class TutorParticipationResource {

    private final Logger log = LoggerFactory.getLogger(TutorParticipationResource.class);
    private final TutorParticipationService tutorParticipationService;
    private final ExerciseService exerciseService;
    private final CourseService courseService;
    private final UserService userService;
    private final ExampleSubmissionRepository exampleSubmissionRepository;
    private final ExampleSubmissionService exampleSubmissionService;

    public TutorParticipationResource(TutorParticipationService tutorParticipationService,
                                      CourseService courseService,
                                      ExerciseService exerciseService,
                                      UserService userService,
                                      ExampleSubmissionRepository exampleSubmissionRepository,
                                      ExampleSubmissionService exampleSubmissionService) {
        this.tutorParticipationService = tutorParticipationService;
        this.exerciseService = exerciseService;
        this.courseService = courseService;
        this.userService = userService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.exampleSubmissionService = exampleSubmissionService;
    }

    /**
     * POST /exercises/:exerciseId/tutorParticipations : start the "id" exercise for the current user.
     *
     * @param exerciseId the id of the exercise for which to init a tutorParticipations
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PostMapping(value = "/exercises/{exerciseId}/tutorParticipations")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TutorParticipation> initTutorParticipation(@PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to start tutor participation : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        Course course = exercise.getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return forbidden();
        }

        TutorParticipation existingTutorParticipation = tutorParticipationService.findByExerciseAndTutor(exercise, user);
        if (existingTutorParticipation != null && existingTutorParticipation.getId() != null) {
            // tutorParticipations already exists
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("tutorParticipations", "tutorParticipationAlreadyExists", "There is already a tutorParticipations for the given exercise and user.")).body(null);
        }
        TutorParticipation tutorParticipation = tutorParticipationService.createNewParticipation(exercise, user);
        return ResponseEntity.created(new URI("/api/exercises/" + exerciseId + "tutorParticipations/" + tutorParticipation.getId()))
            .body(tutorParticipation);
    }

    /**
     * POST /exercises/:exerciseId/tutorParticipations/:participationId/exampleSubmission: add an example submission to the tutor participation
     *
     * @param exerciseId      the id of the exercise of the tutorParticipations
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PostMapping(value = "/exercises/{exerciseId}/exampleSubmission")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TutorParticipation> addExampleSubmission(@PathVariable Long exerciseId, @RequestBody ExampleSubmission exampleSubmission) throws URISyntaxException {
        log.debug("REST request to add example submission to exercise id : {}", exerciseId);
        Exercise exercise = this.exerciseService.findOneLoadTutorParticipations(exerciseId);
        Course course = exercise.getCourse();

        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return forbidden();
        }

        User user = userService.getUserWithGroupsAndAuthorities();

        TutorParticipation existingTutorParticipation = exercise.getTutorParticipationForExercise(user);
        // Do not trust the user input
        Optional<ExampleSubmission> exampleSubmissionFromDatabase = this.exampleSubmissionService.get(exampleSubmission.getId());

        if (existingTutorParticipation == null || !exampleSubmissionFromDatabase.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        ExampleSubmission originalExampleSubmission = exampleSubmissionFromDatabase.get();

        if (existingTutorParticipation.getStatus() != TutorParticipationStatus.REVIEWED_INSTRUCTIONS) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("tutorParticipations", "tutorParticipationInWrongStatus", "You cannot assess an example submission if you haven't read the grading instructions yet.")).body(null);
        }

        // Check if it is a tutorial or not
        boolean isTutorial = originalExampleSubmission.isUsedForTutorial();

        // If it is not a tutorial we check the assessment
        if (!isTutorial) {
            // Retrieve the example feedback created by the instructor
            List<Feedback> existingFeedback = this.exampleSubmissionService.getFeedbacksForExampleSubmission(exampleSubmission.getId());

            // Check if the result is the same
            // TODO: at the moment we check only the score +/10%, maybe we want to do something smarter?
            float instructorScore = calculateTotalScore(existingFeedback);
            float lowerInstructorScore = instructorScore - instructorScore / 10;
            float higherInstructorScore = instructorScore + instructorScore / 10;

            float tutorScore = calculateTotalScore(exampleSubmission.getSubmission().getResult().getFeedbacks());

            if (lowerInstructorScore > tutorScore || tutorScore > higherInstructorScore) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("result", "wrongScore", "Your score is too different from the instructor's one")).body(null);
            }
        }

        List<ExampleSubmission> alreadyAssessedSubmissions = new ArrayList<>(existingTutorParticipation.getTrainedExampleSubmissions());

        // If the example submission was already assessed, we do not assess it again
        if (alreadyAssessedSubmissions.contains(exampleSubmission)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("tutorParticipation", "alreadyAssessed", "You have already assesed this example submission")).body(null);
        }

        int numberOfExampleSubmissions = this.exampleSubmissionRepository.findAllByExerciseId(exerciseId).size();
        int numberOfAlreadyAssessedSubmissions = alreadyAssessedSubmissions.size() + 1;  // +1 because we haven't added yet the one we just did

        /*
          When the tutor has read and assessed all the exercises, the tutor status goes to the next step.
         */
        if (numberOfAlreadyAssessedSubmissions >= numberOfExampleSubmissions) {
            existingTutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        }

        existingTutorParticipation = existingTutorParticipation.addTrainedExampleSubmissions(exampleSubmission);
        exampleSubmissionService.save(exampleSubmission);
        tutorParticipationService.save(existingTutorParticipation);

        // Avoid infinite recursion for JSON
        existingTutorParticipation.getTrainedExampleSubmissions().forEach(t -> {
            t.setTutorParticipation(null);
            t.setExercise(null);
        });
        return ResponseEntity.ok().body(existingTutorParticipation);
    }

    private float calculateTotalScore(List<Feedback> feedbacks) {
        return (float) feedbacks.stream().mapToDouble(Feedback::getCredits).sum();
    }
}
