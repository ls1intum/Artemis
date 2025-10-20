package de.tum.cit.aet.artemis.quiz.service.ai;

import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuizGenerationResponseDTO;

public interface AiQuizGenerationService {

    AiQuizGenerationResponseDTO generate(Long courseId, AiQuizGenerationRequestDTO req, String username);
}
