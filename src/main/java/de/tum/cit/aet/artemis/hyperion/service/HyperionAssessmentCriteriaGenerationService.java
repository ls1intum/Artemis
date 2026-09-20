package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeExerciseContent;

import java.util.ArrayList;
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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.AssessmentCriteriaGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.AssessmentCriteriaGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedAssessmentCriterionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedAssessmentInstructionDTO;
import io.micrometer.observation.annotation.Observed;

/**
 * Generates structured assessment criteria for exercises.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionAssessmentCriteriaGenerationService {

    static final String GENERATION_PIPELINE_ID = "HYPERION_ASSESSMENT_CRITERIA_GENERATION";

    private static final Logger log = LoggerFactory.getLogger(HyperionAssessmentCriteriaGenerationService.class);

    private static final String SYSTEM_PROMPT = "/prompts/hyperion/generate_assessment_criteria_system.st";

    private static final String USER_PROMPT = "/prompts/hyperion/generate_assessment_criteria_user.st";

    private static final int MAX_DATABASE_VARCHAR_LENGTH = 255;

    private static final int REQUIRED_INSTRUCTION_COUNT = 3;

    private static final double POINT_COMPARISON_TOLERANCE = 0.000_001;

    private static final JsonMapper OBJECT_MAPPER = JsonObjectMapper.get();

    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    public HyperionAssessmentCriteriaGenerationService(@Nullable ChatClient chatClient, HyperionPromptTemplateService templateService, LLMTokenUsageService llmTokenUsageService,
            UserRepository userRepository) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
    }

    /**
     * Generates assessment criteria without persisting them.
     *
     * @param course  course used for authorization and token attribution
     * @param request current unsaved exercise context
     * @return validated, ordered generated criteria
     */
    @Observed(name = "hyperion.assessment-criteria.generate", contextualName = "assessment criteria generation", lowCardinalityKeyValues = { "ai.span", "true" })
    public AssessmentCriteriaGenerationResponseDTO generateAssessmentCriteria(Course course, AssessmentCriteriaGenerationRequestDTO request) {
        if (chatClient == null) {
            throw generationError("AI chat client is not configured", "chatClientNotConfigured");
        }

        var outputConverter = new BeanOutputConverter<>(GeneratedCriteriaOutput.class);
        String systemPrompt = templateService.render(SYSTEM_PROMPT, Map.of());
        String userPrompt = templateService.renderObject(USER_PROMPT,
                Map.of("problemStatement", sanitizeExerciseContent(request.problemStatement()), "maxPoints", request.maxPoints(), "bonusPoints", request.bonusPoints(),
                        "gradingInstructions", sanitizeExerciseContent(request.gradingInstructions()), "exampleSolution", sanitizeExerciseContent(request.exampleSolution()),
                        "additionalContext", sanitizeExerciseContent(request.additionalContext()), "format", outputConverter.getFormat()));

        ChatResponse chatResponse;
        try {
            chatResponse = chatClient.prompt().system(systemPrompt).user(userPrompt).call().chatResponse();
        }
        catch (Exception e) {
            log.error("Failed to generate assessment criteria for course [{}]", course.getId(), e);
            throw generationError("Failed to generate assessment criteria", "generationFailed");
        }

        Long userId = HyperionUtils.resolveCurrentUserId(userRepository);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, GENERATION_PIPELINE_ID,
                builder -> builder.withCourse(course.getId()).withUser(userId));

        String responseText = LLMTokenUsageService.extractResponseText(chatResponse);
        if (responseText == null || responseText.isBlank()) {
            throw generationError("LLM returned an empty response", "emptyResponse");
        }

        GeneratedCriteriaOutput output;
        try {
            if (OBJECT_MAPPER.readTree(responseText).findValue("id") != null) {
                throw generationError("Generated assessment criteria must not contain IDs", "invalidResponse");
            }
            output = outputConverter.convert(responseText);
        }
        catch (Exception e) {
            log.error("Failed to parse generated assessment criteria for course [{}]", course.getId(), e);
            throw generationError("Generated assessment criteria are malformed", "invalidResponse");
        }
        return mapAndValidate(output, request.maxPoints(), request.bonusPoints());
    }

    private AssessmentCriteriaGenerationResponseDTO mapAndValidate(@Nullable GeneratedCriteriaOutput output, double maximumPoints, double bonusPoints) {
        if (output == null || output.criteria() == null || output.criteria().isEmpty()) {
            throw generationError("Generated assessment criteria are empty", "emptyResponse");
        }

        List<GeneratedAssessmentCriterionDTO> criteria = new ArrayList<>();
        double regularFullCreditSum = 0;
        double bonusFullCreditSum = 0;
        for (GeneratedCriterionOutput criterion : output.criteria()) {
            if (criterion == null || criterion.bonus() == null) {
                throw generationError("Generated assessment criterion is null or has an invalid bonus discriminator", "invalidResponse");
            }
            String title = sanitizeExerciseContent(criterion.title());
            if (title.isBlank() || title.length() > MAX_DATABASE_VARCHAR_LENGTH || criterion.structuredGradingInstructions() == null
                    || criterion.structuredGradingInstructions().size() != REQUIRED_INSTRUCTION_COUNT) {
                throw generationError("Generated assessment criterion is invalid", "invalidResponse");
            }

            List<GeneratedAssessmentInstructionDTO> instructions = new ArrayList<>();
            for (GeneratedInstructionOutput instruction : criterion.structuredGradingInstructions()) {
                instructions.add(mapAndValidateInstruction(instruction));
            }
            validateCreditLevels(instructions);
            if (criterion.bonus()) {
                bonusFullCreditSum += instructions.getFirst().credits();
            }
            else {
                regularFullCreditSum += instructions.getFirst().credits();
            }
            criteria.add(new GeneratedAssessmentCriterionDTO(title, criterion.bonus(), instructions));
        }
        if (!approximatelyEqual(regularFullCreditSum, maximumPoints) || !approximatelyEqual(bonusFullCreditSum, bonusPoints)) {
            throw generationError("Generated regular or bonus full-credit values do not add up to their respective maximum points", "invalidResponse");
        }
        return new AssessmentCriteriaGenerationResponseDTO(criteria);
    }

    private GeneratedAssessmentInstructionDTO mapAndValidateInstruction(@Nullable GeneratedInstructionOutput instruction) {
        if (instruction == null || !Double.isFinite(instruction.credits()) || instruction.credits() < 0 || instruction.usageCount() != 1) {
            throw generationError("Generated assessment instruction has invalid numeric values", "invalidResponse");
        }
        String gradingScale = sanitizeExerciseContent(instruction.gradingScale());
        String description = sanitizeExerciseContent(instruction.instructionDescription());
        String feedback = sanitizeExerciseContent(instruction.feedback());
        if (gradingScale.isBlank() || gradingScale.length() > MAX_DATABASE_VARCHAR_LENGTH || description.isBlank() || feedback.isBlank()) {
            throw generationError("Generated assessment instruction has invalid text", "invalidResponse");
        }
        return new GeneratedAssessmentInstructionDTO(instruction.credits(), gradingScale, description, feedback, instruction.usageCount());
    }

    private void validateCreditLevels(List<GeneratedAssessmentInstructionDTO> instructions) {
        double fullCredit = instructions.get(0).credits();
        double partialCredit = instructions.get(1).credits();
        double noCredit = instructions.get(2).credits();
        if (fullCredit <= 0 || partialCredit <= 0 || partialCredit >= fullCredit || !approximatelyEqual(noCredit, 0)) {
            throw generationError("Generated assessment criterion has invalid credit levels", "invalidResponse");
        }
    }

    private boolean approximatelyEqual(double first, double second) {
        return Math.abs(first - second) <= POINT_COMPARISON_TOLERANCE;
    }

    private InternalServerErrorAlertException generationError(String message, String errorKey) {
        return new InternalServerErrorAlertException(message, "AssessmentCriteriaGeneration", "AssessmentCriteriaGeneration." + errorKey);
    }

    record GeneratedCriteriaOutput(@JsonPropertyDescription("Ordered assessment criteria") List<GeneratedCriterionOutput> criteria) {
    }

    record GeneratedCriterionOutput(@JsonPropertyDescription("Nonempty criterion title, at most 255 characters") String title,
            @JsonPropertyDescription("False for a regular criterion and true for a bonus criterion") Boolean bonus,
            @JsonPropertyDescription("Exactly three grading instructions ordered as full credit, partial credit, and no credit") List<GeneratedInstructionOutput> structuredGradingInstructions) {
    }

    record GeneratedInstructionOutput(@JsonPropertyDescription("Finite nonnegative credits") double credits,
            @JsonPropertyDescription("Translated full-credit, partial-credit, or no-credit label, at most 255 characters") String gradingScale,
            @JsonPropertyDescription("Nonempty instruction description") String instructionDescription, @JsonPropertyDescription("Nonempty useful feedback") String feedback,
            @JsonPropertyDescription("Usage limit; must be exactly 1") int usageCount) {
    }
}
