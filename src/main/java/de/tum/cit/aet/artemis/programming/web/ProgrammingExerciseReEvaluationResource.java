package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.course.service.CourseService;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseReEvaluationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseUpdateMappingService;

/**
 * REST controller for re-evaluating a programming exercise together with an update of it.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.PROGRAMMING_AUTHORING)
@RestController
@RequestMapping("api/programming/")
public class ProgrammingExerciseReEvaluationResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseReEvaluationResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final ProgrammingExerciseUpdateMappingService programmingExerciseUpdateMappingService;

    private final ProgrammingExerciseReEvaluationService programmingExerciseReEvaluationService;

    private final RateLimitService rateLimitService;

    public ProgrammingExerciseReEvaluationResource(ProgrammingExerciseRepository programmingExerciseRepository, UserRepository userRepository, CourseService courseService,
            ProgrammingExerciseUpdateMappingService programmingExerciseUpdateMappingService, ProgrammingExerciseReEvaluationService programmingExerciseReEvaluationService,
            RateLimitService rateLimitService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.programmingExerciseUpdateMappingService = programmingExerciseUpdateMappingService;
        this.programmingExerciseReEvaluationService = programmingExerciseReEvaluationService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * PUT /programming-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an existing ProgrammingExercise.
     *
     * @param exerciseId                                  of the exercise
     * @param updateDTO                                   the DTO containing the ProgrammingExercise data to re-evaluate and update
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that indicates whether the associated feedback should be deleted or not
     * @param hyperionCompetencyIds                       IDs of links inferred through Hyperion's checklist
     * @return the ResponseEntity with status 200 (OK) and with body the updated ProgrammingExercise, or with status 400 (Bad Request) if the ProgrammingExercise is not valid,
     *         or with status 409 (Conflict) if given exerciseId is not same as in the object of the request body, or with status 429 (Too Many Requests) if the user requested
     *         too many re-evaluations in the last minute, or with status 500 (Internal Server Error) if the ProgrammingExercise couldn't be updated
     */
    @FeatureUsage(UserFeature.PROGRAMMING_REEVALUATION)
    @PutMapping("programming-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditorInExercise
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExerciseResponseDTO> reEvaluateAndUpdateProgrammingExercise(@PathVariable long exerciseId, @RequestBody UpdateProgrammingExerciseDTO updateDTO,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate,
            @RequestParam(name = "hyperionCompetencyId", required = false) Set<Long> hyperionCompetencyIds) {
        log.debug("REST request to re-evaluate ProgrammingExercise with id: {}", updateDTO.id());
        rateLimitService.enforceBudget(userRepository.getCurrentUserLogin(), RateLimitType.RE_EVALUATION);

        var authorizationExercise = programmingExerciseRepository.findForUpdateByIdElseThrow(exerciseId);

        if (updateDTO.id() == null || exerciseId != updateDTO.id()) {
            throw new ConflictException("Exercise id in path does not match id in request body", ENTITY_NAME, "idMismatch");
        }

        var user = userRepository.getUserWithAuthorities();
        // The exercise was loaded without its exercise group, which this initializes for an exam exercise so that its course can be read further down
        courseService.retrieveCourseOfLoadedExercise(authorizationExercise);
        programmingExerciseUpdateMappingService.validateCourseAndExerciseGroupUnchanged(updateDTO, authorizationExercise);

        return ResponseEntity.ok(programmingExerciseReEvaluationService.reEvaluateAndUpdate(exerciseId, authorizationExercise, updateDTO,
                deleteFeedbackAfterGradingInstructionUpdate, hyperionCompetencyIds, user));
    }
}
