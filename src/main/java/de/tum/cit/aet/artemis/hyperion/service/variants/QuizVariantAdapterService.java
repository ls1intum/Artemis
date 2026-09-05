package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseImportService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * Capability adapters for quiz-exercise variants. Simpler than programming — no repos, no CI; validation is
 * synchronous and cheap, so agent iterations are fast.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class QuizVariantAdapterService implements VariantTypeAdapters {

    private static final Logger log = LoggerFactory.getLogger(QuizVariantAdapterService.class);

    /** Pipeline id for token-usage traces of the critique soft gate. */
    private static final String CRITIQUE_PIPELINE_ID = "exercise-variant-critique";

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizExerciseImportService quizExerciseImportService;

    private final QuizExerciseService quizExerciseService;

    private final VariantPlacementService variantPlacementService;

    private final ExerciseVariantJobService jobService;

    private final ObjectMapper objectMapper;

    private final HyperionPromptTemplateService templateService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    private final ExerciseDeletionService exerciseDeletionService;

    @Nullable
    private final ChatClient chatClient;

    public QuizVariantAdapterService(QuizExerciseRepository quizExerciseRepository, QuizExerciseImportService quizExerciseImportService, QuizExerciseService quizExerciseService,
            VariantPlacementService variantPlacementService, ExerciseVariantJobService jobService, ObjectMapper objectMapper, HyperionPromptTemplateService templateService,
            LLMTokenUsageService llmTokenUsageService, UserRepository userRepository, ExerciseDeletionService exerciseDeletionService, @Nullable ChatClient chatClient) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizExerciseImportService = quizExerciseImportService;
        this.quizExerciseService = quizExerciseService;
        this.variantPlacementService = variantPlacementService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
        this.templateService = templateService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
        this.exerciseDeletionService = exerciseDeletionService;
        this.chatClient = chatClient;
    }

    @Override
    public ExerciseType supportedExerciseType() {
        return ExerciseType.QUIZ;
    }

    /**
     * Drag-and-drop questions are not supported: their content lives in the background image and the drag-item /
     * drop-location geometry, none of which the agent can re-theme (image regeneration is explicit future work,
     * and the source images are carried over unchanged). A variant of such a quiz would keep the old theme's
     * pictures next to re-themed text, so the button is hidden and the request rejected instead.
     */
    @Override
    public boolean supportsExercise(Exercise exercise) {
        return quizExerciseRepository.findByIdWithQuestionsElseThrow(exercise.getId()).getQuizQuestions().stream().noneMatch(question -> question instanceof DragAndDropQuestion);
    }

    @Override
    public String renderContext(Exercise source) {
        // Serialize the quiz in the editor's own JSON format (questions, options, mappings, scoring types) —
        // deterministic and identical to what the updateQuestion tool exchanges.
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
            // Declared-type serialization keeps the "type" discriminator (see QuizVariantTools.serializeQuestions).
            context.append(QuizVariantTools.serializeQuestions(objectMapper, quiz.getQuizQuestions()));
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
        // via copyDragItemFile), mappings, and batches from this instance.
        QuizExercise original = quizExerciseRepository.findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(source.getId())
                .orElseThrow(() -> new EntityNotFoundException("QuizExercise", source.getId()));
        // A SECOND, separate instance of the same row plays the import's source role. The import resets the target's
        // batches before re-copying them from the source, so handing it one instance for both roles wiped the batches
        // it was about to copy and left every synchronized or batched variant without a single batch. Loaded outside a
        // transaction, so this is a distinct detached graph rather than the same object again.
        QuizExercise importSource = quizExerciseRepository.findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(source.getId())
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
        // Group placements require INDIVIDUAL mode — synchronized/batched quizzes have a single shared run and
        // cannot share a per-student group timeline, so FINALIZING would place a variant that can never join its
        // group. Switch the clone's mode and drop the copied batches (they belong to the source's run mode); the
        // source exercise itself stays untouched.
        VariantPlacementDTO placement = request.placement();
        boolean groupPlacement = placement != null
                && (placement.type() == VariantPlacementDTO.PlacementType.NEW_GROUP || placement.type() == VariantPlacementDTO.PlacementType.EXISTING_GROUP);
        if (groupPlacement && original.getQuizMode() != QuizMode.INDIVIDUAL) {
            log.debug("Switching quiz variant of exercise {} from {} to INDIVIDUAL mode for group placement", source.getId(), original.getQuizMode());
            original.setQuizMode(QuizMode.INDIVIDUAL);
            original.setQuizBatches(new HashSet<>());
            // Applied to the import source as well, because the import takes the quiz settings and the batches from
            // there — the clone's own mode and batches would otherwise be overwritten from the unchanged source.
            importSource.setQuizMode(QuizMode.INDIVIDUAL);
            importSource.setQuizBatches(new HashSet<>());
        }
        try {
            QuizExercise variant = quizExerciseImportService.importQuizExercise(original, importSource, null);
            log.debug("Provisioned quiz variant {} from source {}", variant.getId(), source.getId());
            // Return a copy WITHOUT the initialized question graph: the import result still carries the deep-copied
            // SOURCE questions, quizQuestions cascades ALL with orphanRemoval, and the pipeline saves this instance
            // again at FINALIZING (group placement) — which silently overwrote every question the agent had already
            // re-themed and saved during TRANSFORMING back to the source content.
            return quizExerciseRepository.findByIdElseThrow(variant.getId());
        }
        catch (Exception e) {
            // The import saves the quiz BEFORE creating its channel and updating competency progress, and the save
            // is identity-preserving, so a post-save failure leaves the new id on `original` while this method
            // never returns — the pipeline's own null-variant cleanup could never find that clone (same reasoning
            // as the programming provisioner's post-import cleanup).
            String message = "Importing the quiz variant clone failed: " + e.getMessage();
            Long provisionedId = original.getId();
            if (provisionedId != null && !provisionedId.equals(source.getId())) {
                try {
                    exerciseDeletionService.delete(provisionedId, true);
                }
                catch (Exception cleanupException) {
                    log.error("Failed to clean up partially provisioned quiz variant exercise {} after a provisioning failure", provisionedId, cleanupException);
                    // The clone survived: hand its id to the pipeline so the FAILED job keeps the deep link.
                    throw new LeftoverVariantExerciseException(provisionedId, message, e);
                }
            }
            throw new RuntimeException(message, e);
        }
    }

    @Override
    public VariantToolset createTools(Exercise variant, VariantJob job) {
        return new QuizVariantTools(variant.getId(), job.getJobId(), jobService, quizExerciseRepository, quizExerciseService, objectMapper);
    }

    @Override
    public VerificationReport verify(Exercise variant, ChangePlan plan, VariantJob job, VariantToolset toolset) {
        // Quiz verification is fully deterministic/synchronous (no CI builds) — nothing in the just-finished
        // round's toolset to reuse here; the parameter exists only to satisfy the shared VariantVerifier contract.
        QuizExercise quiz = quizExerciseRepository.findByIdWithQuestionsElseThrow(variant.getId());
        List<VerificationReport.VerificationFinding> findings = new ArrayList<>();

        // Gate 2: structural validity — every question valid (correct mappings, ≥1 correct MC
        // option, valid SA spots/solutions), quiz-level title/duration/question checks.
        List<QuizQuestion> questions = quiz.getQuizQuestions();
        for (int i = 0; i < questions.size(); i++) {
            QuizQuestion question = questions.get(i);
            if (question == null) {
                // Gap in the @OrderColumn list — a question row lost its exercise FK. Must be a finding, not an NPE.
                findings.add(new VerificationReport.VerificationFinding(VerificationReport.VerificationGate.QUIZ_VALIDITY,
                        "Question " + i + " is missing — the question list has a gap at this position."));
                continue;
            }
            if (!question.isValid()) {
                findings.add(new VerificationReport.VerificationFinding(VerificationReport.VerificationGate.QUIZ_VALIDITY, "Question " + i + " ("
                        + question.getClass().getSimpleName() + ", \"" + question.getTitle() + "\") is invalid. " + QuizVariantTools.renderValidationReport(quiz)));
            }
        }
        if (findings.isEmpty() && !quiz.isValid()) {
            findings.add(new VerificationReport.VerificationFinding(VerificationReport.VerificationGate.QUIZ_VALIDITY,
                    "The quiz is invalid at the exercise level (title, duration, or question list)."));
        }

        // DnD file references must resolve (images are carried over by the import, so all paths must exist).
        try {
            quizExerciseService.validateQuizExerciseFiles(quiz, List.of());
        }
        catch (Exception e) {
            findings.add(new VerificationReport.VerificationFinding(VerificationReport.VerificationGate.QUIZ_FILES, "Drag-and-drop file validation failed: " + e.getMessage()));
        }

        // Soft gate: LLM self-critique against the ChangePlan —
        // plan faithfulness, invariants, distractor plausibility. Runs ONLY when the deterministic gates above
        // passed (they are authoritative); critique errors never fail verification on their own.
        if (findings.isEmpty()) {
            findings.addAll(critique(quiz, plan, job));
        }
        return new VerificationReport(findings.isEmpty(), List.copyOf(findings));
    }

    /** Structured critique output — an empty findings list means the variant passes the soft gate. */
    record CritiqueReport(List<String> findings) {
    }

    private List<VerificationReport.VerificationFinding> critique(QuizExercise quiz, ChangePlan plan, VariantJob job) {
        if (chatClient == null) {
            log.debug("Skipping quiz variant critique for exercise {}: AI chat client is not configured", quiz.getId());
            return List.of();
        }
        try {
            String systemPrompt = templateService.render("prompts/hyperion/variants/critique_quiz_system.st",
                    Map.of("changePlan", renderPlanContract(plan), "variantContext", renderContext(quiz)));
            var outputConverter = new BeanOutputConverter<>(CritiqueReport.class);
            ChatResponse chatResponse = chatClient.prompt().system(systemPrompt).user("Review the variant quiz." + "\n\n" + outputConverter.getFormat()).call().chatResponse();
            trackCritiqueTokenUsage(quiz, job, chatResponse);
            CritiqueReport report = outputConverter.convert(LLMTokenUsageService.extractResponseText(chatResponse));
            if (report == null || report.findings() == null) {
                return List.of();
            }
            return report.findings().stream().filter(finding -> finding != null && !finding.isBlank())
                    .map(finding -> new VerificationReport.VerificationFinding(VerificationReport.VerificationGate.QUIZ_CRITIQUE, finding)).toList();
        }
        catch (Exception e) {
            // The soft gate must never block a structurally valid variant on infrastructure/parsing errors.
            log.warn("Quiz variant critique failed for exercise {} — skipping the soft gate: {}", quiz.getId(), e.getMessage());
            return List.of();
        }
    }

    /** Token accounting for the critique pass — same wiring as the pipeline's calls. */
    private void trackCritiqueTokenUsage(QuizExercise quiz, VariantJob job, ChatResponse chatResponse) {
        Long userId = userRepository.findOneByLogin(job.getInitiatorLogin()).map(User::getId).orElse(null);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, CRITIQUE_PIPELINE_ID,
                builder -> builder.withExercise(quiz.getId()).withUser(userId));
        if (chatResponse != null && chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null
                && chatResponse.getMetadata().getUsage().getTotalTokens() != null) {
            jobService.addTokensUsed(job.getJobId(), chatResponse.getMetadata().getUsage().getTotalTokens());
        }
    }

    private String renderPlanContract(ChangePlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("Title: ").append(plan.variantTitle()).append("\n\nIntended changes:\n");
        plan.intendedChanges().forEach(change -> builder.append("- ").append(change).append('\n'));
        builder.append("\nInvariants:\n");
        plan.invariants().forEach(invariant -> builder.append("- ").append(invariant).append('\n'));
        return builder.toString();
    }

    @Override
    public List<String> finalizeVariant(Exercise variant, VariantJob job) {
        // Same shared placement logic as programming.
        return variantPlacementService.place(variant, job.getSourceExerciseId(), job.getRequest());
    }
}
