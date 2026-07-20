package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeInput;

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
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * Generates structured assessment criteria for text and modeling exercises.
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

    private static final ObjectMapper OBJECT_MAPPER = JsonObjectMapper.get();

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
        String modelingContext = "";
        if (request.modelingContext() != null) {
            modelingContext = "Diagram type: " + sanitizeInput(request.modelingContext().diagramType()) + "\nCurrent example solution model:\n"
                    + sanitizeInput(request.modelingContext().exampleSolutionModel());
        }
        String userPrompt = templateService.renderObject(USER_PROMPT,
                Map.of("exerciseType", request.exerciseType().name(), "problemStatement", sanitizeInput(request.problemStatement()), "maxPoints", request.maxPoints(),
                        "bonusPoints", request.bonusPoints(), "gradingInstructions", sanitizeInput(request.gradingInstructions()), "modelingContext", modelingContext, "format",
                        outputConverter.getFormat()));

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
        return mapAndValidate(output);
    }

    private AssessmentCriteriaGenerationResponseDTO mapAndValidate(@Nullable GeneratedCriteriaOutput output) {
        if (output == null || output.criteria() == null || output.criteria().isEmpty()) {
            throw generationError("Generated assessment criteria are empty", "emptyResponse");
        }

        List<GeneratedAssessmentCriterionDTO> criteria = new ArrayList<>();
        for (GeneratedCriterionOutput criterion : output.criteria()) {
            if (criterion == null) {
                throw generationError("Generated assessment criterion is null", "invalidResponse");
            }
            String title = sanitizeInput(criterion.title());
            if (title.isBlank() || title.length() > MAX_DATABASE_VARCHAR_LENGTH || criterion.structuredGradingInstructions() == null
                    || criterion.structuredGradingInstructions().isEmpty()) {
                throw generationError("Generated assessment criterion is invalid", "invalidResponse");
            }

            List<GeneratedAssessmentInstructionDTO> instructions = new ArrayList<>();
            for (GeneratedInstructionOutput instruction : criterion.structuredGradingInstructions()) {
                instructions.add(mapAndValidateInstruction(instruction));
            }
            criteria.add(new GeneratedAssessmentCriterionDTO(title, instructions));
        }
        return new AssessmentCriteriaGenerationResponseDTO(criteria);
    }

    private GeneratedAssessmentInstructionDTO mapAndValidateInstruction(@Nullable GeneratedInstructionOutput instruction) {
        if (instruction == null || !Double.isFinite(instruction.credits()) || instruction.usageCount() < 0) {
            throw generationError("Generated assessment instruction has invalid numeric values", "invalidResponse");
        }
        String gradingScale = sanitizeInput(instruction.gradingScale());
        String description = sanitizeInput(instruction.instructionDescription());
        String feedback = sanitizeInput(instruction.feedback());
        if (gradingScale.isBlank() || gradingScale.length() > MAX_DATABASE_VARCHAR_LENGTH || description.isBlank() || feedback.isBlank()) {
            throw generationError("Generated assessment instruction has invalid text", "invalidResponse");
        }
        return new GeneratedAssessmentInstructionDTO(instruction.credits(), gradingScale, description, feedback, instruction.usageCount());
    }

    private InternalServerErrorAlertException generationError(String message, String errorKey) {
        return new InternalServerErrorAlertException(message, "AssessmentCriteriaGeneration", "AssessmentCriteriaGeneration." + errorKey);
    }

    record GeneratedCriteriaOutput(@JsonPropertyDescription("Ordered assessment criteria") List<GeneratedCriterionOutput> criteria) {
    }

    record GeneratedCriterionOutput(@JsonPropertyDescription("Nonempty criterion title, at most 255 characters") String title,
            @JsonPropertyDescription("One or more ordered grading instructions") List<GeneratedInstructionOutput> structuredGradingInstructions) {
    }

    record GeneratedInstructionOutput(@JsonPropertyDescription("Finite credits; use negative values for deductions where appropriate") double credits,
            @JsonPropertyDescription("Nonempty grading scale, at most 255 characters") String gradingScale,
            @JsonPropertyDescription("Nonempty instruction description") String instructionDescription, @JsonPropertyDescription("Nonempty useful feedback") String feedback,
            @JsonPropertyDescription("Nonnegative integer usage limit") int usageCount) {
    }
}
