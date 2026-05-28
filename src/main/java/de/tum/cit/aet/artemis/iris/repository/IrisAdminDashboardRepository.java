package de.tum.cit.aet.artemis.iris.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;

@Repository
@Lazy
@Conditional(IrisEnabled.class)
public interface IrisAdminDashboardRepository extends ArtemisJpaRepository<IrisMessage, Long> {

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*) FROM iris_session s
            WHERE s.creation_date >= :from AND s.creation_date < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    long countTotalSessions(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(DISTINCT m.session_id)
            FROM iris_message m
            JOIN iris_session s ON s.id = m.session_id
            WHERE m.sender = 'USER'
              AND s.creation_date >= :from AND s.creation_date < :to
              AND m.sent_at >= :from AND m.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    long countActiveSessions(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*)
            FROM iris_message m
            JOIN iris_session s ON s.id = m.session_id
            WHERE m.sent_at >= :from AND m.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    long countTotalMessages(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(DISTINCT s.user_id)
            FROM iris_message m
            JOIN iris_session s ON s.id = m.session_id
            WHERE m.sender = 'USER'
              AND m.sent_at >= :from AND m.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    long countUniqueUsers(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*)
            FROM iris_message m
            JOIN iris_session s ON s.id = m.session_id
            WHERE m.sender = 'LLM'
              AND m.helpful = true
              AND m.sent_at >= :from AND m.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    long countThumbsUp(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*)
            FROM iris_message m
            JOIN iris_session s ON s.id = m.session_id
            WHERE m.sender = 'LLM'
              AND m.helpful = false
              AND m.sent_at >= :from AND m.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    long countThumbsDown(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*)
            FROM iris_message m
            JOIN iris_session s ON s.id = m.session_id
            WHERE m.sender = 'LLM'
              AND m.sent_at >= :from AND m.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    long countTotalLlmMessages(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(DISTINCT m.session_id)
            FROM iris_message m
            JOIN iris_session s ON s.id = m.session_id
            WHERE m.sender = 'LLM'
              AND m.helpful = true
              AND s.creation_date >= :from AND s.creation_date < :to
              AND m.sent_at >= :from AND m.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    long countSessionsWithThumbsUp(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(DISTINCT m.session_id)
            FROM iris_message m
            JOIN iris_session s ON s.id = m.session_id
            WHERE m.sender = 'LLM'
              AND m.helpful = false
              AND s.creation_date >= :from AND s.creation_date < :to
              AND m.sent_at >= :from AND m.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    long countSessionsWithThumbsDown(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT u.id AS userMsgId, u.session_id AS sessionId, u.sent_at AS sentAt,
                (SELECT m2.sender FROM iris_message m2
                 WHERE m2.session_id = u.session_id
                   AND (m2.sent_at > u.sent_at OR (m2.sent_at = u.sent_at AND m2.id > u.id))
                 ORDER BY m2.sent_at, m2.id LIMIT 1
                ) AS nextSender,
                (SELECT m2.sent_at FROM iris_message m2
                 WHERE m2.session_id = u.session_id
                   AND (m2.sent_at > u.sent_at OR (m2.sent_at = u.sent_at AND m2.id > u.id))
                 ORDER BY m2.sent_at, m2.id LIMIT 1
                ) AS nextSentAt,
                CASE WHEN s.discriminator = 'TUTOR_SUGGESTION' THEN 'TUTOR_SUGGESTION' ELSE s.chat_mode END AS modeLabel
            FROM iris_message u
            JOIN iris_session s ON s.id = u.session_id
            WHERE u.sender = 'USER'
              AND u.sent_at >= :from AND u.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    List<Object[]> findUserMessagesWithNextMessageFullRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT CASE WHEN t.iris_message_id IS NOT NULL THEN 1 ELSE 0 END AS chatAttributed,
                   SUM(r.num_input_tokens * r.cost_per_million_input_tokens / 1000000.0
                     + r.num_output_tokens * r.cost_per_million_output_tokens / 1000000.0) AS totalCostEur
            FROM llm_token_usage_trace t
            JOIN llm_token_usage_request r ON r.trace_id = t.id
            WHERE t.service = 'IRIS'
              AND t.time >= :from AND t.time < :to
            GROUP BY CASE WHEN t.iris_message_id IS NOT NULL THEN 1 ELSE 0 END
            """)
    List<Object[]> computeTokenCost(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT s.id AS sessionId, s.creation_date AS creationDate,
                   CASE WHEN s.discriminator = 'TUTOR_SUGGESTION' THEN 'TUTOR_SUGGESTION' ELSE s.chat_mode END AS modeLabel,
                   CASE WHEN EXISTS (
                       SELECT 1 FROM iris_message m WHERE m.session_id = s.id AND m.sender = 'USER'
                         AND m.sent_at >= :from AND m.sent_at < :to
                   ) THEN 1 ELSE 0 END AS hasUserMessage
            FROM iris_session s
            WHERE s.creation_date >= :from AND s.creation_date < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    List<Object[]> findSessionsWithMode(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT m.id AS msgId, m.sent_at AS sentAt, m.sender, m.session_id AS sessionId
            FROM iris_message m
            JOIN iris_session s ON s.id = m.session_id
            WHERE m.sent_at >= :from AND m.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    List<Object[]> findMessagesInRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*)
            FROM iris_message m
            JOIN iris_session s ON s.id = m.session_id
            WHERE m.sender = 'USER'
              AND m.sent_at >= :from AND m.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    long countUserMessages(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT m.id AS msgId, m.sent_at AS sentAt, m.helpful
            FROM iris_message m
            JOIN iris_session s ON s.id = m.session_id
            WHERE m.sender = 'LLM'
              AND m.sent_at >= :from AND m.sent_at < :to
              AND s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')
            """)
    List<Object[]> findLlmMessagesWithRatings(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT t.time AS traceTime,
                   CASE WHEN t.iris_message_id IS NOT NULL THEN 1 ELSE 0 END AS chatAttributed,
                   SUM(r.num_input_tokens * r.cost_per_million_input_tokens / 1000000.0
                     + r.num_output_tokens * r.cost_per_million_output_tokens / 1000000.0) AS costEur
            FROM llm_token_usage_trace t
            JOIN llm_token_usage_request r ON r.trace_id = t.id
            WHERE t.service = 'IRIS'
              AND t.time >= :from AND t.time < :to
            GROUP BY t.id, t.time, CASE WHEN t.iris_message_id IS NOT NULL THEN 1 ELSE 0 END
            """)
    List<Object[]> findTokenCostWithTimestamps(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            SELECT s.course_id AS courseId, COUNT(*) AS sessionCount
            FROM iris_session s
            WHERE s.creation_date >= :from AND s.creation_date < :to
              AND s.discriminator = 'CHAT'
              AND s.course_id IS NOT NULL
            GROUP BY s.course_id
            ORDER BY sessionCount DESC
            LIMIT :limit
            """)
    List<Object[]> findTopCoursesBySessionCount(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

    @Query(nativeQuery = true, value = """
            SELECT r.model,
                   SUM(r.num_input_tokens + r.num_output_tokens) AS totalTokens,
                   SUM(r.num_input_tokens * r.cost_per_million_input_tokens / 1000000.0
                     + r.num_output_tokens * r.cost_per_million_output_tokens / 1000000.0) AS totalCostEur
            FROM llm_token_usage_trace t
            JOIN llm_token_usage_request r ON r.trace_id = t.id
            WHERE t.service = 'IRIS'
              AND t.time >= :from AND t.time < :to
            GROUP BY r.model
            """)
    List<Object[]> computeTokenCostByModel(@Param("from") Instant from, @Param("to") Instant to);
}
