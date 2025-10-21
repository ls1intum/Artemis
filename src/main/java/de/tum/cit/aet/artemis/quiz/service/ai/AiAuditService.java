package de.tum.cit.aet.artemis.quiz.service.ai;

import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuestionSubtype;

public interface AiAuditService {

    void logGeneration(String user, Long courseId, int n, String topic, AiQuestionSubtype subtype);

    void logEvent(String category, String user, String message);

    void logImport(String user, Long exerciseId, int n);
}
