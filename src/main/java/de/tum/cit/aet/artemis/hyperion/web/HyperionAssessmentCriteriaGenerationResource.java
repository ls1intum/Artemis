package de.tum.cit.aet.artemis.hyperion.web;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.AssessmentCriteriaGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.AssessmentCriteriaGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionAssessmentCriteriaGenerationService;

/**
 * REST resource for AI-generated assessment criteria.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@FeatureUsage("authoring-assistance/assessment-criteria-generation")
@RestController
@RequestMapping("api/hyperion/")
public class HyperionAssessmentCriteriaGenerationResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionAssessmentCriteriaGenerationResource.class);

    private final CourseRepository courseRepository;

    private final HyperionAssessmentCriteriaGenerationService generationService;

    public HyperionAssessmentCriteriaGenerationResource(CourseRepository courseRepository, HyperionAssessmentCriteriaGenerationService generationService) {
        this.courseRepository = courseRepository;
        this.generationService = generationService;
    }

    /**
     * Generates structured assessment criteria for an unsaved exercise.
     *
     * @param courseId course containing the exercise
     * @param request  current exercise context
     * @return generated criteria, without persisting them
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/assessment-criteria/generate")
    public ResponseEntity<AssessmentCriteriaGenerationResponseDTO> generateAssessmentCriteria(@PathVariable long courseId,
            @Valid @RequestBody AssessmentCriteriaGenerationRequestDTO request) {
        log.debug("REST request to generate assessment criteria for course [{}]", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(generationService.generateAssessmentCriteria(course, request));
    }
}
