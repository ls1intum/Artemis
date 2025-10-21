package de.tum.cit.aet.artemis.quiz.service.ai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuizGenerationResponseDTO;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.GeneratedMcQuestionDTO;

@Service
@Primary
public class AiQuizGenerationServiceAzure implements AiQuizGenerationService {

    private final ChatClient chatClient;

    private final AiAuditService audit;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(AiQuizGenerationServiceAzure.class);

    public AiQuizGenerationServiceAzure(ChatClient chatClient, AiAuditService audit) {
        this.chatClient = chatClient;
        this.audit = audit;
    }

    @Override
    public AiQuizGenerationResponseDTO generate(Long courseId, AiQuizGenerationRequestDTO request, String username) {
        audit.logGeneration(username, courseId, request.numberOfQuestions(), request.topic(), request.requestedSubtype());

        // --- build prompts -------------------------------------------------------
        String userPrompt = buildUserPrompt(request);
        String systemPrompt = """
                You are an AI assistant that generates quiz questions in JSON format only.
                Return a valid JSON array of objects following this structure:
                [
                  {
                    "title": "...",
                    "text": "...",
                    "explanation": "...",
                    "hint": "...",
                    "difficulty": 3,
                    "subtype": "MULTI_CORRECT" | "SINGLE_CORRECT" | "TRUE_FALSE",
                    "competencyIds": [],
                    "options": [
                      {"text": "...", "correct": true, "feedback": "..."}
                    ],
                    "validation": {
                      "hasSolution": true,
                      "hasHint": true,
                      "difficultyValid": true,
                      "issues": []
                    }
                  }
                ]
                Do not include any extra commentary or formatting outside the JSON array.
                """;

        try {
            // --- call Azure OpenAI through Spring AI -----------------------------
            ChatResponse chatResponse = chatClient.prompt().system(systemPrompt).user(userPrompt).options(AzureOpenAiChatOptions.builder().temperature(0.0).maxTokens(1500).build())
                    .call().chatResponse();

            if (chatResponse == null) {
                log.error("Azure returned empty chat response");
                return new AiQuizGenerationResponseDTO(List.of(), List.of("Empty response from Azure OpenAI"));
            }

            String content = chatResponse.getResult().getOutput().getText();

            log.debug("Raw model output:\n{}", content);

            // --- parse model output ----------------------------------------------
            List<GeneratedMcQuestionDTO> questions = mapper.readValue(content, mapper.getTypeFactory().constructCollectionType(List.class, GeneratedMcQuestionDTO.class));

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
