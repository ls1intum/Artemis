package de.tum.cit.aet.artemis.hyperion.service.variants;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;

/**
 * Capability adapters for quiz-exercise variants (plan Sections 2.7.1 and 4, Student B focus).
 * Simpler than programming — no repos, no CI; validation is synchronous and cheap, so agent iterations are fast.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class QuizVariantAdapters implements VariantTypeAdapters {

    // TODO (Sonnet): Inject via constructor (plan Section 4 table, "Existing code reused" column):
    // - QuizExerciseImportService (PROVISIONING deep copy incl. DnD images, mappings, batches)
    // - QuizExerciseService (validateQuizExerciseFiles) + QuizExerciseRepository (reload with questions)
    // - HyperionConsistencyCheckService (semantic gate, Section 2.6 step 3)
    // - HyperionPromptTemplateService + ChatClient IF the LLM self-critique verify pass is done here
    // (reusing the refine_quiz_question prompts as a critique step, Section 4 VERIFYING row)
    // - the shared variant-group placement helper (see ProgrammingVariantAdapters.finalizeVariant TODO)

    @Override
    public ExerciseType supportedExerciseType() {
        return ExerciseType.QUIZ;
    }

    @Override
    public String renderContext(Exercise source) {
        // TODO (Sonnet): New small renderer: serialize the quiz — every question with its type
        // (MultipleChoiceQuestion / DragAndDropQuestion / ShortAnswerQuestion), options/items/spots, correct
        // mappings, points, and scoring type — into a compact deterministic text/JSON block for the planner
        // (plan Section 4, ANALYZING row). Do not include image binaries; reference DnD images by file name.
        throw new UnsupportedOperationException("TODO (Sonnet): plan Section 4, ANALYZING row");
    }

    @Override
    public Exercise provision(Exercise source, VariantGenerationRequestDTO request, VariantJob job) {
        // TODO (Sonnet): Deep-copy via QuizExerciseImportService.importQuizExercise(...) — handles MC/DnD/SA copies,
        // copyDragItemFile, mappings (plan Section 4, PROVISIONING row). Title from job.getChangePlan().variantTitle().
        throw new UnsupportedOperationException("TODO (Sonnet): plan Section 4, PROVISIONING row");
    }

    @Override
    public VariantToolset createTools(Exercise variant, VariantJob job) {
        // TODO (Sonnet): Quiz toolset per plan Section 2.5 table + Section 4 TRANSFORMING row:
        // - getQuestions(): current serialized questions of the VARIANT.
        // - updateQuestion(index, questionJson): validate against the GeneratedQuizQuestionDTO JSON schema before
        // applying; schema violations returned to the model as the tool result (Section 6 row 2). DnD questions:
        // only text/mapping edits — background/item images are carried over unchanged (Section 1 non-goals).
        // - validateQuiz(): wraps QuizExercise.isValid() + per-question error details so the agent can self-check
        // as it goes (Section 4).
        // - finish(summary).
        throw new UnsupportedOperationException("TODO (Sonnet): plan Section 2.5 toolset table");
    }

    @Override
    public VerificationReport verify(Exercise variant, ChangePlan plan) {
        // TODO (Sonnet): Gate 2 (plan Section 2.6): QuizExercise.isValid() — every question valid: correct
        // mappings, ≥1 correct MC option, valid SA spots/solutions — plus
        // QuizExerciseService.validateQuizExerciseFiles for DnD file references. Then the LLM self-critique pass
        // reusing the existing quiz refinement prompts as critique ("is the distractor set plausible? is exactly the
        // requested change applied?", Section 4 VERIFYING row) and the shared semantic consistency gate against the
        // ChangePlan invariants (Section 2.6 step 3). Per-question errors become findings for the repair round.
        throw new UnsupportedOperationException("TODO (Sonnet): plan Sections 2.6 and 4, VERIFYING row");
    }

    @Override
    public void finalizeVariant(Exercise variant, VariantGenerationRequestDTO request) {
        // TODO (Sonnet): Persist, then the same shared placement logic as programming (plan Section 4 FINALIZING
        // row: "same as programming") — EXISTING_GROUP / NEW_GROUP / STANDALONE / SAME_EXAM_GROUP (Section 5.5).
        throw new UnsupportedOperationException("TODO (Sonnet): plan Section 4, FINALIZING row");
    }
}
