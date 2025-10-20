package de.tum.cit.aet.artemis.quiz.web;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.config.AiFeatureFlagConfiguration;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.ai.AiQuizImportService;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuizImportRequestDTO;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuizImportResponseDTO;

@RestController
@RequestMapping("api/quiz-exercises/{exerciseId}/ai")
public class AiQuizImportResource {

    private final QuizExerciseRepository quizExerciseRepository;

    private final AuthorizationCheckService authCheck;

    private final AiQuizImportService importService;

    private final AiFeatureFlagConfiguration flags;

    public AiQuizImportResource(QuizExerciseRepository quizExerciseRepository, AuthorizationCheckService authCheck, AiQuizImportService importService,
            AiFeatureFlagConfiguration flags) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.authCheck = authCheck;
        this.importService = importService;
        this.flags = flags;
    }

    @PostMapping("/import")
    public ResponseEntity<AiQuizImportResponseDTO> importQuestions(@PathVariable Long exerciseId, @Valid @RequestBody AiQuizImportRequestDTO req, Principal principal) {

        if (!flags.isEnabled())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI quiz generation is disabled");

        QuizExercise exercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(exerciseId);

        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (!(authCheck.isAdmin() || authCheck.isAtLeastEditorInCourse(principal.getName(), course.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to import AI questions for this exercise.");
        }
        List<Long> created = importService.importQuestions(exercise, req.questions(), principal.getName());
        return ResponseEntity.ok(new AiQuizImportResponseDTO(created));
    }
}
