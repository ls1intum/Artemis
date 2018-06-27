package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.JsonAssessmentRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ModelingExerciseService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * REST controller for managing ModelingAssessment.
 */
@RestController
@RequestMapping("/api")
public class ModelingAssessmentResource {
    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentResource.class);

    private static final String ENTITY_NAME = "modelingAssessment";

    private final JsonAssessmentRepository jsonAssessmentRepository;
    private final ResultRepository resultRepository;
    private final ParticipationRepository participationRepository;
    private final CompassService compassService;
    private final ModelingExerciseService modelingExerciseService;
    private final UserService userService;
    private final AuthorizationCheckService authCheckService;
    private final CourseService courseService;

    public ModelingAssessmentResource(JsonAssessmentRepository jsonAssessmentRepository, ResultRepository resultRepository, ParticipationRepository participationRepository, CompassService compassService, ModelingExerciseService modelingExerciseService, UserService userService, AuthorizationCheckService authCheckService, CourseService courseService) {
        this.jsonAssessmentRepository = jsonAssessmentRepository;
        this.resultRepository = resultRepository;
        this.participationRepository = participationRepository;
        this.compassService = compassService;
        this.modelingExerciseService = modelingExerciseService;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.courseService = courseService;
    }

    @DeleteMapping("/modeling-assessments/exercise/{exerciseId}/optimal-models")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<String> resetOptimalModels(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        if (modelingExercise == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(modelingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        compassService.resetModelsWaitingForAssessment(exerciseId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @GetMapping("/modeling-assessments/exercise/{exerciseId}/optimal-models")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<String> getNextOptimalModels(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(modelingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Set<Long> optimalModels = compassService.getModelsWaitingForAssessment(exerciseId);
        JsonArray response = new JsonArray();
        for (Long optimalModel: optimalModels) {
            JsonObject entry = new JsonObject();
            response.add(entry);
            entry.addProperty("id", optimalModel);
        }
        return ResponseEntity.ok(response.toString());
    }

    @GetMapping("/modeling-assessments/exercise/{exerciseId}/submission/{submissionId}/partial-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<String> getPartialAssessment(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(modelingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        JsonObject partialAssessment = compassService.getPartialAssessment(exerciseId, submissionId);
        return ResponseEntity.ok(partialAssessment.get("assessments").toString());
    }

    @GetMapping("/modeling-assessments/participation/{participationId}/submission/{submissionId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<String> getAssessmentBySubmissionId(@PathVariable Long participationId, @PathVariable Long submissionId) {
        Participation participation = participationRepository.findOne(participationId);

        if (!courseService.userHasStudentPermissions(participation.getExercise().getCourse())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long exerciseId = participation.getExercise().getId();
        Long studentId = participation.getStudent().getId();
        if (jsonAssessmentRepository.exists(exerciseId, studentId, submissionId, true)) {
            JsonObject assessmentJson = jsonAssessmentRepository.readAssessment(exerciseId, studentId, submissionId, true);
            return ResponseEntity.ok(assessmentJson.get("assessments").toString());
        }
        if (jsonAssessmentRepository.exists(exerciseId, studentId, submissionId, false)) {
            JsonObject assessmentJson = jsonAssessmentRepository.readAssessment(exerciseId, studentId, submissionId, false);
            return ResponseEntity.ok(assessmentJson.get("assessments").toString());
        }
        return ResponseEntity.ok("");
    }

    @PutMapping("/modeling-assessments/exercise/{exerciseId}/result/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> saveModelingAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody String modelingAssessment) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(modelingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Result result = updateManualResult(resultId, exerciseId, modelingAssessment, false);

        return ResponseEntity.ok(result);
    }

    @PutMapping("/modeling-assessments/exercise/{exerciseId}/result/{resultId}/submit")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> submitModelingAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody String modelingAssessment) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(modelingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Result result = updateManualResult(resultId, exerciseId, modelingAssessment, true);
        Long submissionId = result.getSubmission().getId();
        compassService.addAssessment(exerciseId, submissionId, modelingAssessment);

        return ResponseEntity.ok(result);
    }

    //TODO: move more logic into a new ModelingAssessmentService

    public Result updateManualResult(Long resultId, Long exerciseId, String modelingAssessment, Boolean rated) {
        Result result = resultRepository.findOne(resultId);
        if (result.getAssessmentType() == AssessmentType.MANUAL && result.isRated()) {
            // if result is already manually rated, return it
            return result;
        }
        result.setRated(rated);
        result.setCompletionDate(ZonedDateTime.now());
        result.setAssessmentType(AssessmentType.MANUAL);

        User user = userService.getUser();
        result.setAssessor(user);

        Long studentId = result.getParticipation().getStudent().getId();
        Long submissionId = result.getSubmission().getId();

        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);

        jsonAssessmentRepository.writeAssessment(exerciseId, studentId, submissionId, true, modelingAssessment);

        if (rated) {
            // set result if rated
            JsonObject assessmentJson = jsonAssessmentRepository.readAssessment(exerciseId, studentId, submissionId, true);
            Double maxScore = modelingExercise.getMaxScore();
            Double totalScore = calculateTotalScore(assessmentJson);
            Double percentageScore = totalScore/maxScore*100;
            result.setScore(percentageScore.longValue());
            DecimalFormat formatter = new DecimalFormat("#.##"); // limit decimal places to 2
            result.setResultString(formatter.format(totalScore) + " of " + formatter.format(modelingExercise.getMaxScore()) + " points");
            result.setSuccessful(percentageScore.longValue() == 100L);
        }

        resultRepository.save(result);

        return result;
    }

    public Double calculateTotalScore(JsonObject assessmentJson) {
        Double totalScore = 0.0;
        JsonArray assessments = assessmentJson.get("assessments").getAsJsonArray();
        for (JsonElement assessment : assessments) {
            totalScore += assessment.getAsJsonObject().getAsJsonPrimitive("credits").getAsDouble();
        }
        return totalScore;
    }
}
