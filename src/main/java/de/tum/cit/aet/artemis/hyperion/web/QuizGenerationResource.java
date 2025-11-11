package de.tum.cit.aet.artemis.hyperion.web;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.AiQuizGenerationService;

@Profile({ "localci", "hyperion" })
@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/quizzes/")
public class QuizGenerationResource {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerationResource.class);

    private final AiQuizGenerationService aiQuizGenerationService;

    private final CourseRepository courseRepository;

    public QuizGenerationResource(AiQuizGenerationService aiQuizGenerationService, CourseRepository courseRepository) {
        this.aiQuizGenerationService = aiQuizGenerationService;
        this.courseRepository = courseRepository;
    }

    /**
     * POST /courses/{courseId}/generate : Generate an AI-based quiz for the given course.
     *
     * @param courseId         the id of the course
     * @param generationParams the parameters defining how the quiz should be generated
     * @return the generated quiz in JSON format
     */
    @PostMapping("courses/{courseId}/generate")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<AiQuizGenerationResponseDTO> generate(@PathVariable long courseId, @Valid @RequestBody AiQuizGenerationRequestDTO generationParams) {
        log.debug("REST request to generate AI quiz for course {}", courseId);
        courseRepository.findById(courseId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course with id " + courseId + " not found"));

        AiQuizGenerationResponseDTO response = aiQuizGenerationService.generate(courseId, generationParams);

        return ResponseEntity.ok(response);
    }
}
