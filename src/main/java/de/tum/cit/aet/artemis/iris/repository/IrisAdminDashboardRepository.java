package de.tum.cit.aet.artemis.iris.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.dashboard.IrisDashboardSessionType;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardUserMessageResultDTO;

@Lazy
@Repository
@Conditional(IrisEnabled.class)
public class IrisAdminDashboardRepository {

    private static final String SESSION_TYPE_EXPRESSION = "CASE WHEN s.discriminator = 'TUTOR_SUGGESTION' THEN 'TUTOR_SUGGESTION' ELSE s.chat_mode END";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public IrisAdminDashboardRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Finds Iris sessions created in the requested window or active through messages in the requested window.
     *
     * @param from        the inclusive UTC start
     * @param to          the exclusive UTC end
     * @param sessionType optional session type filter
     * @return matching session rows
     */
    public List<SessionRow> findSessions(ZonedDateTime from, ZonedDateTime to, @Nullable IrisDashboardSessionType sessionType) {
        String sql = """
                SELECT s.id, %s AS session_type, s.course_id, c.title, s.creation_date
                FROM iris_session s
                    LEFT JOIN course c ON c.id = s.course_id
                WHERE (s.creation_date >= :from
                        AND s.creation_date < :to
                        OR EXISTS (
                            SELECT 1
                            FROM iris_message sm
                            WHERE sm.session_id = s.id
                                AND sm.sent_at >= :from
                                AND sm.sent_at < :to
                        ))
                    AND %s
                ORDER BY s.creation_date, s.id
                """.formatted(SESSION_TYPE_EXPRESSION, sessionFilterSql(sessionType));

        return jdbcTemplate.query(sql, parameters(from, to, sessionType), (resultSet, rowNumber) -> new SessionRow(resultSet.getLong(1), toStringValue(resultSet.getObject(2)),
                nullableLong(resultSet, 3), resultSet.getString(4), toZonedDateTime(resultSet.getObject(5))));
    }

    /**
     * Finds Iris messages sent in the requested window.
     *
     * @param from        the inclusive UTC start
     * @param to          the exclusive UTC end
     * @param sessionType optional session type filter
     * @return matching message rows
     */
    public List<MessageRow> findMessages(ZonedDateTime from, ZonedDateTime to, @Nullable IrisDashboardSessionType sessionType) {
        String sql = """
                SELECT m.id, m.session_id, %s AS session_type, s.course_id, c.title, m.sender, m.sent_at, m.helpful, s.user_id
                FROM iris_message m
                    JOIN iris_session s ON s.id = m.session_id
                    LEFT JOIN course c ON c.id = s.course_id
                WHERE m.sent_at >= :from
                    AND m.sent_at < :to
                    AND %s
                ORDER BY m.sent_at, m.id
                """.formatted(SESSION_TYPE_EXPRESSION, sessionFilterSql(sessionType));

        return jdbcTemplate.query(sql, parameters(from, to, sessionType),
                (resultSet, rowNumber) -> new MessageRow(resultSet.getLong(1), resultSet.getLong(2), toStringValue(resultSet.getObject(3)), nullableLong(resultSet, 4),
                        resultSet.getString(5), toStringValue(resultSet.getObject(6)), toZonedDateTime(resultSet.getObject(7)), toNullableBoolean(resultSet.getObject(8)),
                        nullableLong(resultSet, 9)));
    }

    /**
     * Finds USER messages and their deterministic next message for no-response and response-time metrics.
     *
     * @param from        the inclusive UTC start
     * @param to          the exclusive UTC end
     * @param sessionType optional session type filter
     * @return matching USER message result rows
     */
    public List<IrisDashboardUserMessageResultDTO> findUserMessageResults(ZonedDateTime from, ZonedDateTime to, @Nullable IrisDashboardSessionType sessionType) {
        String sql = """
                WITH relevant_sessions AS (
                    SELECT DISTINCT u.session_id
                    FROM iris_message u
                        JOIN iris_session s ON s.id = u.session_id
                    WHERE u.sender = 'USER'
                        AND u.sent_at >= :from
                        AND u.sent_at < :to
                        AND %s
                ),
                ordered_messages AS (
                    SELECT m.id, m.session_id, m.sender, m.sent_at,
                        LEAD(m.sender) OVER (PARTITION BY m.session_id ORDER BY m.sent_at, m.id) AS next_sender,
                        LEAD(m.sent_at) OVER (PARTITION BY m.session_id ORDER BY m.sent_at, m.id) AS next_sent_at
                    FROM iris_message m
                        JOIN relevant_sessions rs ON rs.session_id = m.session_id
                )
                SELECT u.id, u.session_id, %s AS session_type, s.course_id, u.sent_at, u.next_sender, u.next_sent_at
                FROM ordered_messages u
                    JOIN iris_session s ON s.id = u.session_id
                WHERE u.sender = 'USER'
                    AND u.sent_at >= :from
                    AND u.sent_at < :to
                    AND %s
                ORDER BY u.sent_at, u.id
                """.formatted(sessionFilterSql(sessionType), SESSION_TYPE_EXPRESSION, sessionFilterSql(sessionType));

        return jdbcTemplate.query(sql, parameters(from, to, sessionType),
                (resultSet, rowNumber) -> new IrisDashboardUserMessageResultDTO(resultSet.getLong(1), resultSet.getLong(2), nullableLong(resultSet, 4),
                        toStringValue(resultSet.getObject(3)), toZonedDateTime(resultSet.getObject(5)), resultSet.getString(6), toNullableZonedDateTime(resultSet.getObject(7))));
    }

    /**
     * Finds Iris token traces and request costs in the requested window.
     *
     * @param from        the inclusive UTC start
     * @param to          the exclusive UTC end
     * @param sessionType optional session type filter
     * @return matching token usage rows
     */
    public List<TokenUsageRow> findTokenUsage(ZonedDateTime from, ZonedDateTime to, @Nullable IrisDashboardSessionType sessionType) {
        String filter = sessionType == null ? "(t.iris_message_id IS NULL OR " + sessionFilterSql(null) + ")"
                : "t.iris_message_id IS NOT NULL AND " + sessionFilterSql(sessionType);
        String sql = """
                SELECT t.id, t.time, COALESCE(s.course_id, t.course_id) AS course_id, c.title,
                    %s AS session_type, r.model, COALESCE(r.num_input_tokens, 0), COALESCE(r.num_output_tokens, 0),
                    ((COALESCE(r.num_input_tokens, 0) * COALESCE(r.cost_per_million_input_tokens, 0)) / 1000000.0)
                        + ((COALESCE(r.num_output_tokens, 0) * COALESCE(r.cost_per_million_output_tokens, 0)) / 1000000.0) AS cost_eur,
                    CASE WHEN t.iris_message_id IS NULL THEN 0 ELSE 1 END AS chat_attributed
                FROM llm_token_usage_trace t
                    JOIN llm_token_usage_request r ON r.trace_id = t.id
                    LEFT JOIN iris_message im ON im.id = t.iris_message_id
                    LEFT JOIN iris_session s ON s.id = im.session_id
                    LEFT JOIN course c ON c.id = COALESCE(s.course_id, t.course_id)
                WHERE t.service = 'IRIS'
                    AND t.time >= :from
                    AND t.time < :to
                    AND %s
                ORDER BY t.time, t.id, r.id
                """.formatted(SESSION_TYPE_EXPRESSION, filter);

        return jdbcTemplate.query(sql, parameters(from, to, sessionType),
                (resultSet, rowNumber) -> new TokenUsageRow(resultSet.getLong(1), toZonedDateTime(resultSet.getObject(2)), nullableLong(resultSet, 3), resultSet.getString(4),
                        resultSet.getString(5), resultSet.getString(6), resultSet.getInt(7), resultSet.getInt(8), toDouble(resultSet.getObject(9)),
                        toBoolean(resultSet.getObject(10))));
    }

    private static MapSqlParameterSource parameters(ZonedDateTime from, ZonedDateTime to, @Nullable IrisDashboardSessionType sessionType) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("from", Timestamp.from(from.toInstant()));
        parameters.addValue("to", Timestamp.from(to.toInstant()));
        if (sessionType != null && !sessionType.isTutorSuggestion()) {
            parameters.addValue("chatMode", sessionType.databaseValue());
        }
        return parameters;
    }

    private static String sessionFilterSql(@Nullable IrisDashboardSessionType sessionType) {
        if (sessionType == null) {
            return "s.discriminator IN ('CHAT', 'TUTOR_SUGGESTION')";
        }
        if (sessionType.isTutorSuggestion()) {
            return "s.discriminator = 'TUTOR_SUGGESTION'";
        }
        return "s.discriminator = 'CHAT' AND s.chat_mode = :chatMode";
    }

    @Nullable
    private static Long nullableLong(ResultSet resultSet, int columnIndex) throws SQLException {
        long value = resultSet.getLong(columnIndex);
        return resultSet.wasNull() ? null : value;
    }

    private static double toDouble(@Nullable Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    @Nullable
    private static Boolean toNullableBoolean(@Nullable Object value) {
        return value == null ? null : toBoolean(value);
    }

    private static String toStringValue(Object value) {
        return value == null ? "UNKNOWN" : value.toString();
    }

    private static ZonedDateTime toZonedDateTime(Object value) {
        return switch (value) {
            case ZonedDateTime zonedDateTime -> zonedDateTime.withZoneSameInstant(ZoneOffset.UTC);
            case OffsetDateTime offsetDateTime -> offsetDateTime.toZonedDateTime().withZoneSameInstant(ZoneOffset.UTC);
            case Instant instant -> instant.atZone(ZoneOffset.UTC);
            case Timestamp timestamp -> timestamp.toInstant().atZone(ZoneOffset.UTC);
            case LocalDateTime localDateTime -> localDateTime.atZone(ZoneOffset.UTC);
            default -> throw new IllegalArgumentException("Unsupported timestamp value " + value + " of type " + value.getClass().getName());
        };
    }

    @Nullable
    private static ZonedDateTime toNullableZonedDateTime(@Nullable Object value) {
        return value == null ? null : toZonedDateTime(value);
    }

    public record SessionRow(long sessionId, String sessionType, @Nullable Long courseId, @Nullable String courseTitle, ZonedDateTime creationDate) {
    }

    public record MessageRow(long messageId, long sessionId, String sessionType, @Nullable Long courseId, @Nullable String courseTitle, String sender, ZonedDateTime sentAt,
            @Nullable Boolean helpful, @Nullable Long userId) {
    }

    public record TokenUsageRow(long traceId, ZonedDateTime time, @Nullable Long courseId, @Nullable String courseTitle, @Nullable String sessionType, @Nullable String model,
            int inputTokens, int outputTokens, double costEur, boolean chatAttributed) {
    }
}
