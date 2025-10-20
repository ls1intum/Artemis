package de.tum.cit.aet.artemis.quiz.service.ai;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    public AiQuizGenerationServiceAzure(ChatClient chatClient, AiAuditService audit) {
        this.chatClient = chatClient;
        this.audit = audit;
    }

    @Override
    public AiQuizGenerationResponseDTO generate(Long courseId, AiQuizGenerationRequestDTO req, String username) {
        audit.logGeneration(username, courseId, req.numberOfQuestions(), req.topic(), req.requestedSubtype());

        String prompt = buildPrompt(req);

        String content;
        try {
            // The fluent Spring-AI client — already wired for Azure OpenAI
            content = chatClient.prompt().user(prompt).call().content(); // plain text from assistant
        }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Azure OpenAI request failed: " + e.getMessage(), e);
        }

        try {
            List<GeneratedMcQuestionDTO> questions = mapper.readValue(content, mapper.getTypeFactory().constructCollectionType(List.class, GeneratedMcQuestionDTO.class));
            return new AiQuizGenerationResponseDTO(questions, List.of());
        }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid AI JSON output", e);
        }
    }

    private String buildPrompt(AiQuizGenerationRequestDTO req) {
        return """
                Generate %d %s quiz questions about "%s".
                Return a pure JSON array like:
                [{"title":"...","text":"...","options":[{"text":"...","correct":true}],...}]
                No explanations, only valid JSON.
                """.formatted(req.numberOfQuestions(), req.language(), req.topic());
    }
}
