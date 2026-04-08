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

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionQuizQuestionGenerationService;

/**
 * REST controller for Hyperion quiz question generation.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/")
public class HyperionQuizQuestionGenerationResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionQuizQuestionGenerationResource.class);

    private final CourseRepository courseRepository;

    private final HyperionQuizQuestionGenerationService quizQuestionGenerationService;

    public HyperionQuizQuestionGenerationResource(CourseRepository courseRepository, HyperionQuizQuestionGenerationService quizQuestionGenerationService) {
        this.courseRepository = courseRepository;
        this.quizQuestionGenerationService = quizQuestionGenerationService;
    }

    /**
     * POST /courses/{courseId}/quiz-exercises/generate-questions : Generate quiz questions from a configuration.
     *
     * @param courseId the id of the course
     * @param request  generation configuration
     * @return generated quiz questions
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/quiz-exercises/generate-questions")
    public ResponseEntity<QuizQuestionGenerationResponseDTO> generateQuizQuestions(@PathVariable long courseId, @Valid @RequestBody QuizQuestionGenerationRequestDTO request) {
        log.debug("REST request to Hyperion generate quiz questions for course [{}]", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        var result = quizQuestionGenerationService.generateQuizQuestions(course, request);
        return ResponseEntity.ok(result);
    }
}
