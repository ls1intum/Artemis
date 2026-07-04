package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseImportService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * Capability adapters for quiz-exercise variants (plan Sections 2.7.1 and 4, Student B focus).
 * Simpler than programming — no repos, no CI; validation is synchronous and cheap, so agent iterations are fast.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class QuizVariantAdapters implements VariantTypeAdapters {

    private static final Logger log = LoggerFactory.getLogger(QuizVariantAdapters.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizExerciseImportService quizExerciseImportService;

    private final QuizExerciseService quizExerciseService;

    private final VariantPlacementService variantPlacementService;

    private final ExerciseVariantJobService jobService;

    private final ObjectMapper objectMapper;

    public QuizVariantAdapters(QuizExerciseRepository quizExerciseRepository, QuizExerciseImportService quizExerciseImportService, QuizExerciseService quizExerciseService,
            VariantPlacementService variantPlacementService, ExerciseVariantJobService jobService, ObjectMapper objectMapper) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizExerciseImportService = quizExerciseImportService;
        this.quizExerciseService = quizExerciseService;
        this.variantPlacementService = variantPlacementService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExerciseType supportedExerciseType() {
        return ExerciseType.QUIZ;
    }

    @Override
    public String renderContext(Exercise source) {
        // Serialize the quiz in the editor's own JSON format (questions, options, mappings, scoring types) —
        // deterministic and identical to what the updateQuestion tool exchanges (plan Section 4, ANALYZING row).
        // Image binaries are never included; DnD questions reference their images by file path only.
        QuizExercise quiz = quizExerciseRepository.findByIdWithQuestionsElseThrow(source.getId());
        StringBuilder context = new StringBuilder();
        context.append("Quiz exercise: ").append(quiz.getTitle()).append('\n');
        context.append("Max points: ").append(quiz.getMaxPoints()).append(", duration (s): ").append(quiz.getDuration()).append(", quiz mode: ").append(quiz.getQuizMode())
                .append(", questions: ").append(quiz.getQuizQuestions().size()).append('\n');
        if (quiz.getProblemStatement() != null && !quiz.getProblemStatement().isBlank()) {
            context.append("Description: ").append(quiz.getProblemStatement()).append('\n');
        }
        context.append("\nQuestions (JSON, editor format):\n");
        try {
            context.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(quiz.getQuizQuestions()));
        }
        catch (Exception e) {
            throw new IllegalStateException("Could not serialize the quiz questions of exercise " + source.getId(), e);
        }
        return context.toString();
    }

    @Override
    public Exercise provision(Exercise source, VariantGenerationRequestDTO request, VariantJob job) {
        ChangePlan plan = job.getChangePlan();
        if (plan == null) {
            throw new IllegalStateException("Cannot provision a variant without a change plan");
        }
        // Same eager graph as the REST import path — the import service deep-copies questions (incl. DnD images
        // via copyDragItemFile), mappings, and batches from this instance (plan Section 4, PROVISIONING row).
        QuizExercise original = quizExerciseRepository.findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(source.getId())
                .orElseThrow(() -> new EntityNotFoundException("QuizExercise", source.getId()));
        // The detached instance doubles as the "imported exercise carrying the new values": only the fields the
        // variant changes are overwritten; course/exam group, dates, mode, and duration are copied as-is.
        original.setTitle(plan.variantTitle());
        if (request.targetDifficulty() != null) {
            original.setDifficulty(request.targetDifficulty());
        }
        if (plan.problemStatement() != null && !plan.problemStatement().isBlank()) {
            original.setProblemStatement(plan.problemStatement());
        }
        try {
            QuizExercise variant = quizExerciseImportService.importQuizExercise(original, original, null);
            log.debug("Provisioned quiz variant {} from source {}", variant.getId(), source.getId());
            return variant;
        }
        catch (Exception e) {
            throw new RuntimeException("Importing the quiz variant clone failed: " + e.getMessage(), e);
        }
    }

    @Override
    public VariantToolset createTools(Exercise variant, VariantJob job) {
        return new QuizVariantTools(variant.getId(), job.getJobId(), jobService, quizExerciseRepository, quizExerciseService, objectMapper);
    }

    @Override
    public VerificationReport verify(Exercise variant, ChangePlan plan) {
        QuizExercise quiz = quizExerciseRepository.findByIdWithQuestionsElseThrow(variant.getId());
        List<VerificationReport.VerificationFinding> findings = new ArrayList<>();

        // Gate 2 (plan Section 2.6): structural validity — every question valid (correct mappings, ≥1 correct MC
        // option, valid SA spots/solutions), quiz-level title/duration/question checks.
        List<QuizQuestion> questions = quiz.getQuizQuestions();
        for (int i = 0; i < questions.size(); i++) {
            QuizQuestion question = questions.get(i);
            if (!question.isValid()) {
                findings.add(new VerificationReport.VerificationFinding("QUIZ_VALIDITY", "Question " + i + " (" + question.getClass().getSimpleName() + ", \"" + question.getTitle()
                        + "\") is invalid. " + QuizVariantTools.renderValidationReport(quiz)));
            }
        }
        if (findings.isEmpty() && !quiz.isValid()) {
            findings.add(new VerificationReport.VerificationFinding("QUIZ_VALIDITY", "The quiz is invalid at the exercise level (title, duration, or question list)."));
        }

        // DnD file references must resolve (images are carried over by the import, so all paths must exist).
        try {
            quizExerciseService.validateQuizExerciseFiles(quiz, List.of());
        }
        catch (Exception e) {
            findings.add(new VerificationReport.VerificationFinding("QUIZ_FILES", "Drag-and-drop file validation failed: " + e.getMessage()));
        }

        // TODO (Sonnet): LLM self-critique pass reusing the refine_quiz_question prompts as a critique step
        // ("is the distractor set plausible? is exactly the requested change applied?") and the shared semantic
        // consistency gate against the ChangePlan invariants (plan Sections 2.6 step 3 and 4, VERIFYING row).
        // Deterministic gates above run first and are authoritative.
        return new VerificationReport(findings.isEmpty(), List.copyOf(findings));
    }

    @Override
    public void finalizeVariant(Exercise variant, VariantGenerationRequestDTO request) {
        // Same shared placement logic as programming (plan Section 4, FINALIZING row: "same as programming").
        variantPlacementService.place(variant, request);
    }
}
