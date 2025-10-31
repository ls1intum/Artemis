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
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.AiQuizGenerationService;

@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/quizzes/")
public class QuizGenerationResource {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerationResource.class);

    private final AiQuizGenerationService aiQuizGenerationService;

    public QuizGenerationResource(AiQuizGenerationService aiQuizGenerationService) {
        this.aiQuizGenerationService = aiQuizGenerationService;
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
    public ResponseEntity<AiQuizGenerationResponseDTO> generate(@PathVariable Long courseId, @Valid @RequestBody AiQuizGenerationRequestDTO generationParams) {
        log.debug("REST request to generate AI quiz for course {}", courseId);
        return ResponseEntity.ok(aiQuizGenerationService.generate(courseId, generationParams));
    }
}
