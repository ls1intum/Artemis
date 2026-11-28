package de.tum.cit.aet.artemis.core.config;

import java.time.Duration;

import javax.sql.DataSource;

import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.jdbc.JdbcSessionRepository;
import org.springframework.ai.session.jdbc.JdbcSessionRepositoryDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The session memory of Spring AI, enabled when either Atlas or Hyperion is enabled. It stores the conversations of the Atlas chat agent. Chat models come from the LLM
 * presets, see {@link de.tum.cit.aet.artemis.core.service.llm.LlmClientService}.
 */
@Configuration
@Conditional(SpringAIConfiguration.SpringAIEnabled.class)
@Lazy
public class SpringAIConfiguration {

    /**
     * The expiry that a session gets when it is created. Spring AI does not enforce it when it reads a session, and nothing in Artemis deletes expired sessions, so the
     * conversations of the chat agent stay as long as they did in the chat memory before. The value only has to be far away.
     */
    private static final Duration SESSION_TIME_TO_LIVE = Duration.ofDays(3650);

    /**
     * Creates a JDBC-based session repository for persistent storage. The tables are created by Liquibase, not by Spring AI.
     * Uses an auto-detected JDBC dialect based on the configured DataSource.
     *
     * @param dataSource the datasource to use for JDBC operations
     * @return configured SessionRepository
     */
    @Bean
    @Lazy
    public SessionRepository sessionRepository(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // The mapper is the one of Spring AI on purpose: it writes the format of the stored events, which must not change with the settings of the Artemis mapper
        return JdbcSessionRepository.builder().jdbcTemplate(jdbcTemplate).dialect(JdbcSessionRepositoryDialect.from(dataSource)).build();
    }

    /**
     * Creates the session service that the chat agent reads and writes its conversations with.
     *
     * @param sessionRepository the repository for storing the conversations
     * @return the session service
     */
    @Bean
    @Lazy
    public SessionService sessionService(SessionRepository sessionRepository) {
        return DefaultSessionService.builder().sessionRepository(sessionRepository).defaultTimeToLive(SESSION_TIME_TO_LIVE).build();
    }

    /**
     * Condition that enables Spring AI configuration when either the LLM-backed part of Atlas or Hyperion is enabled.
     * <p>
     * Atlas alone is not enough. Its competency features need no model, and every consumer of the beans below sits
     * behind one of these two flags, so keying on the Atlas module would build a chat memory repository on every
     * installation that has competencies. That is not free: it opens a connection and reads the database metadata to
     * pick a dialect, and the eager initialization of lazy singletons means it happens during startup rather than on
     * first use.
     */
    public static class SpringAIEnabled implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();
            return artemisConfigHelper.isAtlasLLMEnabled(context.getEnvironment()) || artemisConfigHelper.isHyperionEnabled(context.getEnvironment());
        }
    }
}
