package de.tum.cit.aet.artemis.quiz.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuestionSubtype;

@Service
public class AiAuditServiceLogger implements AiAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiAuditServiceLogger.class);

    @Override
    public void logGeneration(String user, Long courseId, int n, String topic, AiQuestionSubtype subtype) {
        log.info("[AI-QUIZ][GEN] user={} courseId={} n={} topic='{}' subtype={}", user, courseId, n, topic, subtype);
    }

    @Override
    public void logImport(String user, Long exerciseId, int n) {
        log.info("[AI-QUIZ][IMP] user={} exerciseId={} n={}", user, exerciseId, n);
    }
}
