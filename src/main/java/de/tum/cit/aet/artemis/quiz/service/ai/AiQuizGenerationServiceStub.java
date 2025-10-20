package de.tum.cit.aet.artemis.quiz.service.ai;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuestionSubtype;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuizGenerationResponseDTO;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.GeneratedMcQuestionDTO;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.McOptionDTO;

@Service
public class AiQuizGenerationServiceStub implements AiQuizGenerationService {

    private final AiAuditService audit;

    public AiQuizGenerationServiceStub(AiAuditService audit) {
        this.audit = audit;
    }

    @Override
    public AiQuizGenerationResponseDTO generate(Long courseId, AiQuizGenerationRequestDTO req, String username) {
        var n = Math.max(1, Math.min(Objects.requireNonNullElse(req.numberOfQuestions(), 1), 20));
        List<GeneratedMcQuestionDTO> items = IntStream.range(0, n).mapToObj(i -> {
            var subtype = Objects.requireNonNullElse(req.requestedSubtype(), AiQuestionSubtype.MULTI_CORRECT);
            var options = switch (subtype) {
                case TRUE_FALSE -> List.of(new McOptionDTO("Wahr", true, "Richtig."), new McOptionDTO("Falsch", false, "Falsch."));
                case SINGLE_CORRECT -> List.of(new McOptionDTO("A", true, "Korrekt."), new McOptionDTO("B", false, "—"), new McOptionDTO("C", false, "—"));
                default -> List.of(new McOptionDTO("A", true, "✓"), new McOptionDTO("B", true, "✓"), new McOptionDTO("C", false, "—"));
            };
            var validation = ValidationUtils.basic(options, subtype);
            return new GeneratedMcQuestionDTO("AI-Draft " + (i + 1), "Stub stem for: " + Optional.ofNullable(req.topic()).orElse("General"), "Kurzbegründung.",
                    "Denke an die Definitionen.", Optional.ofNullable(req.difficultyLevel()).map(d -> switch (d) {
                        case EASY -> 2;
                        case MEDIUM -> 3;
                        case HARD -> 4;
                        default -> 3;
                    }).orElse(3), Set.of("ai", "draft"), subtype, Optional.ofNullable(req.competencyIds()).orElse(Set.of()), options, validation);
        }).toList();

        audit.logGeneration(username, courseId, n, req.topic(), req.requestedSubtype());
        return new AiQuizGenerationResponseDTO(items, List.of());
    }
}
