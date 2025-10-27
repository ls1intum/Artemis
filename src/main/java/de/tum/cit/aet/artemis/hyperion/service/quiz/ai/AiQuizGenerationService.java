package de.tum.cit.aet.artemis.hyperion.service.quiz.ai;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.hyperion.service.quiz.ai.dto.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.quiz.ai.dto.AiQuizGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.quiz.ai.dto.GeneratedMcQuestionDTO;

@Service
@Primary
public class AiQuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiQuizGenerationService.class);

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final ObjectMapper mapper = new ObjectMapper();

    public AiQuizGenerationService(ChatClient chatClient, HyperionPromptTemplateService templateService) {
        this.chatClient = chatClient;
        this.templateService = templateService;
    }

    public AiQuizGenerationResponseDTO generate(long courseId, AiQuizGenerationRequestDTO generationParams) {
        log.debug("Generating quiz for course {}", courseId);

        try {
            // load prompts from external templates
            String systemPrompt = templateService.render("/prompts/hyperion/quiz_generation_system.st", Map.of());
            String userPrompt = templateService.render("/prompts/hyperion/quiz_generation_user.st", generationParams.toTemplateVariables());

            String responseContent = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();

            // TODO: proper validation
            List<GeneratedMcQuestionDTO> questions = mapper.readValue(responseContent, mapper.getTypeFactory().constructCollectionType(List.class, GeneratedMcQuestionDTO.class));

            return new AiQuizGenerationResponseDTO(questions, List.of());
        }
        catch (Exception e) {
            log.error("Failed to generate or parse AI quiz: {}", e.getMessage(), e);
            return new AiQuizGenerationResponseDTO(List.of(), List.of("Error during quiz generation: " + e.getMessage()));
        }
    }

    private String buildUserPrompt(AiQuizGenerationRequestDTO req) {
        return """
                Generate %d %s quiz questions about "%s".
                Return a pure JSON array like:
                [{"title":"...","text":"...","options":[{"text":"...","correct":true}],...}]
                No explanations, only valid JSON.
                """.formatted(req.numberOfQuestions(), req.language(), req.topic());
    }
}
