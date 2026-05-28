package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardBreakdownDimension;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardMetric;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardBreakdownEntryDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardConfigDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardDigestChatModeDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardDigestCourseDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardDigestDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardOverviewDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardTimeSeriesDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardTimeSeriesEntryDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardUserMessageResultDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository;

/**
 * Service for computing Iris admin dashboard metrics: overview KPIs, time-series, and breakdowns.
 */
@Service
@Lazy
@Profile(PROFILE_CORE)
@Conditional(IrisEnabled.class)
public class IrisAdminDashboardService {

    private static final String ENTITY_NAME = "irisDashboard";

    private final IrisAdminDashboardRepository dashboardRepository;

    private final IrisDashboardProperties properties;

    private final CourseRepository courseRepository;

    public IrisAdminDashboardService(IrisAdminDashboardRepository dashboardRepository, IrisDashboardProperties properties, CourseRepository courseRepository) {
        this.dashboardRepository = dashboardRepository;
        this.properties = properties;
        this.courseRepository = courseRepository;
    }

    // -- Overview ---------------------------------------------------------------

    /**
     * Computes all overview KPIs for the given time range.
     *
     * @param from start of the range (inclusive)
     * @param to   end of the range (exclusive)
     * @return the overview DTO
     */
    public IrisDashboardOverviewDTO computeOverview(Instant from, Instant to) {
        long totalSessions = dashboardRepository.countTotalSessions(from, to);
        long activeSessions = dashboardRepository.countActiveSessions(from, to);
        double engagementRate = totalSessions > 0 ? (double) activeSessions / totalSessions * 100.0 : 0.0;

        long totalMessages = dashboardRepository.countTotalMessages(from, to);
        long uniqueUsers = dashboardRepository.countUniqueUsers(from, to);

        // Single fetch of user messages for both no-response and response-time computation
        Instant staleBefore = computeStaleBefore(to, TimeUtil.now().toInstant());
        List<IrisDashboardUserMessageResultDTO> allUserMessages = mapResults(dashboardRepository.findUserMessagesWithNextMessageFullRange(from, to));

        // No-response: only consider messages before staleBefore
        long noResponseMessageCount = 0;
        long eligibleMessages = 0;
        Set<Long> noResponseSessionIds = new java.util.HashSet<>();
        List<Duration> responseTimes = new ArrayList<>();
        for (IrisDashboardUserMessageResultDTO msg : allUserMessages) {
            if (msg.sentAt().isBefore(staleBefore)) {
                eligibleMessages++;
                if (msg.nextSender() == null) {
                    noResponseMessageCount++;
                    noResponseSessionIds.add(msg.sessionId());
                }
            }
            if (msg.nextSender() != null && msg.nextSender() == IrisMessageSender.LLM && msg.nextSentAt() != null) {
                Duration responseTime = Duration.between(msg.sentAt(), msg.nextSentAt());
                if (!responseTime.isNegative()) {
                    responseTimes.add(responseTime);
                }
            }
        }
        double noResponseRate = eligibleMessages > 0 ? (double) noResponseMessageCount / eligibleMessages * 100.0 : 0.0;

        // Thumbs up/down
        long thumbsUp = dashboardRepository.countThumbsUp(from, to);
        long thumbsDown = dashboardRepository.countThumbsDown(from, to);
        long totalLlmMessages = dashboardRepository.countTotalLlmMessages(from, to);
        double thumbsUpRatio = totalLlmMessages > 0 ? (double) thumbsUp / totalLlmMessages * 100.0 : 0.0;
        double thumbsDownRatio = totalLlmMessages > 0 ? (double) thumbsDown / totalLlmMessages * 100.0 : 0.0;

        long sessionsWithThumbsUp = dashboardRepository.countSessionsWithThumbsUp(from, to);
        long sessionsWithThumbsDown = dashboardRepository.countSessionsWithThumbsDown(from, to);
        double thumbsUpAbsoluteRate = totalSessions > 0 ? (double) sessionsWithThumbsUp / totalSessions * 100.0 : 0.0;
        double thumbsDownAbsoluteRate = totalSessions > 0 ? (double) sessionsWithThumbsDown / totalSessions * 100.0 : 0.0;

        // Response time
        responseTimes.sort(Duration::compareTo);
        double avgResponseTime = responseTimes.isEmpty() ? 0.0 : responseTimes.stream().mapToDouble(Duration::toMillis).average().orElse(0.0) / 1000.0;
        double p50ResponseTime = percentile(responseTimes, 0.5);
        double p95ResponseTime = percentile(responseTimes, 0.95);

        // Token cost
        double totalTokenCostEur = 0.0;
        List<Object[]> costRows = dashboardRepository.computeTokenCost(from, to);
        for (Object[] row : costRows) {
            totalTokenCostEur += ((Number) row[1]).doubleValue();
        }

        return new IrisDashboardOverviewDTO(totalSessions, activeSessions, engagementRate, totalMessages, uniqueUsers, noResponseRate, noResponseMessageCount,
                noResponseSessionIds.size(), thumbsUpRatio, thumbsDownRatio, thumbsUpAbsoluteRate, thumbsDownAbsoluteRate, sessionsWithThumbsUp, sessionsWithThumbsDown, thumbsUp,
                thumbsDown, avgResponseTime, p50ResponseTime, p95ResponseTime, totalTokenCostEur);
    }

    // -- Digest -----------------------------------------------------------------

    /**
     * Computes the full digest data for the given time window.
     * <p>
     * Combines overview KPIs with a per-chat-mode breakdown and a top-5 course list.
     *
     * @param from        start of the window (inclusive)
     * @param to          end of the window (exclusive)
     * @param staleBefore cutoff for no-response computation (messages after this may still receive a response)
     * @return the digest DTO containing all overview fields, chat-mode breakdown, and top courses
     */
    public IrisDashboardDigestDTO computeDigestData(Instant from, Instant to, Instant staleBefore) {
        IrisDashboardOverviewDTO overview = computeOverview(from, to);

        // Chat-mode breakdown: group sessions by mode, zero out per-mode message/rating counts
        List<Object[]> sessionRows = dashboardRepository.findSessionsWithMode(from, to);
        Map<String, Long> sessionsByMode = new HashMap<>();
        for (Object[] row : sessionRows) {
            String mode = row[2] != null ? row[2].toString() : "UNKNOWN";
            sessionsByMode.merge(mode, 1L, Long::sum);
        }
        List<IrisDashboardDigestChatModeDTO> chatModeBreakdown = new ArrayList<>();
        for (Map.Entry<String, Long> entry : sessionsByMode.entrySet()) {
            chatModeBreakdown.add(new IrisDashboardDigestChatModeDTO(entry.getKey(), entry.getValue(), 0L, 0.0, 0L, 0L));
        }

        // Top-5 courses by session count
        List<Object[]> courseRows = dashboardRepository.findTopCoursesBySessionCount(from, to, 5);
        List<Long> courseIds = courseRows.stream().map(row -> ((Number) row[0]).longValue()).toList();
        Map<Long, String> courseNames = resolveCourseNames(courseIds);
        List<IrisDashboardDigestCourseDTO> topCourses = new ArrayList<>();
        for (Object[] row : courseRows) {
            long courseId = ((Number) row[0]).longValue();
            long sessionCount = ((Number) row[1]).longValue();
            String courseName = courseNames.getOrDefault(courseId, "Course #" + courseId);
            topCourses.add(new IrisDashboardDigestCourseDTO(courseName, sessionCount, 0L, 0.0, 0.0));
        }

        return new IrisDashboardDigestDTO(from, to, staleBefore, overview.totalSessions(), overview.activeSessions(), overview.engagementRate(), overview.totalMessages(),
                overview.uniqueUsers(), overview.noResponseRate(), overview.noResponseMessageCount(), overview.noResponseSessionCount(), overview.thumbsUpRatio(),
                overview.thumbsDownRatio(), overview.thumbsUpAbsoluteRate(), overview.thumbsDownAbsoluteRate(), overview.thumbsUpCount(), overview.thumbsDownCount(),
                overview.sessionsWithThumbsUp(), overview.sessionsWithThumbsDown(), overview.avgResponseTimeSeconds(), overview.p50ResponseTimeSeconds(),
                overview.p95ResponseTimeSeconds(), overview.totalTokenCostEur(), chatModeBreakdown, topCourses, "/admin/iris-dashboard");
    }

    // -- Config -----------------------------------------------------------------

    /**
     * Returns the current dashboard configuration as a DTO.
     *
     * @return the config DTO
     */
    public IrisDashboardConfigDTO getConfig() {
        var alert = properties.getAlert();
        var digest = properties.getDigest();
        return new IrisDashboardConfigDTO(properties.getMaxQueryWindowDays(), properties.getStaleThresholdMinutes(), digest.isEnabled(), digest.getCron(), alert.isEnabled(),
                alert.getNoResponseRateThreshold(), alert.getCheckIntervalMinutes(), alert.getCooldownMinutes(), alert.getLookbackMinutes(), alert.getMinimumEligibleSessions(),
                alert.getMinimumUserMessages());
    }

    // -- Stale-before computation -----------------------------------------------

    /**
     * Computes the stale-before cutoff: messages sent after this timestamp may still receive a response.
     *
     * @param to  the end of the query range
     * @param now the current instant
     * @return min(to, now - staleThreshold)
     */
    public Instant computeStaleBefore(Instant to, Instant now) {
        Instant staleLimit = now.minus(Duration.ofMinutes(properties.getStaleThresholdMinutes()));
        return to.isBefore(staleLimit) ? to : staleLimit;
    }

    // -- Percentile -------------------------------------------------------------

    /**
     * Computes the p-th percentile of a sorted list of durations using linear interpolation.
     *
     * @param sorted the sorted list of durations
     * @param p      the percentile (0.0 to 1.0)
     * @return the percentile value in seconds, or 0.0 for an empty list
     */
    public static double percentile(List<Duration> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        if (sorted.size() == 1) {
            return sorted.getFirst().toMillis() / 1000.0;
        }
        double index = p * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted.get(lower).toMillis() / 1000.0;
        }
        double fraction = index - lower;
        double lowerVal = sorted.get(lower).toMillis() / 1000.0;
        double upperVal = sorted.get(upper).toMillis() / 1000.0;
        return lowerVal + fraction * (upperVal - lowerVal);
    }

    // -- Map native query results -----------------------------------------------

    /**
     * Maps native query result rows to {@link IrisDashboardUserMessageResultDTO}.
     * Handles both {@link java.sql.Timestamp} and {@link Instant} for date columns (cross-DB compatibility).
     *
     * @param rows the raw rows from the native query
     * @return the mapped DTOs
     */
    public List<IrisDashboardUserMessageResultDTO> mapResults(List<Object[]> rows) {
        List<IrisDashboardUserMessageResultDTO> results = new ArrayList<>();
        for (Object[] row : rows) {
            long userMsgId = ((Number) row[0]).longValue();
            long sessionId = ((Number) row[1]).longValue();
            Instant sentAt = toInstant(row[2]);
            IrisMessageSender nextSender = row[3] != null ? IrisMessageSender.valueOf(row[3].toString()) : null;
            Instant nextSentAt = row[4] != null ? toInstant(row[4]) : null;
            String modeLabel = row[5] != null ? row[5].toString() : null;
            results.add(new IrisDashboardUserMessageResultDTO(userMsgId, sessionId, sentAt, nextSender, nextSentAt, modeLabel));
        }
        return results;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("Cannot convert to Instant: " + value.getClass().getName());
    }

    // -- Time Series ------------------------------------------------------------

    /**
     * Computes a time-series for the given metric, bucketed by the specified span.
     *
     * @param from   start of the range (inclusive)
     * @param to     end of the range (exclusive)
     * @param span   one of DAY, WEEK, MONTH, QUARTER, YEAR
     * @param metric the metric to compute
     * @return the time-series DTO
     */
    public IrisDashboardTimeSeriesDTO computeTimeSeries(Instant from, Instant to, String span, IrisDashboardMetric metric) {
        List<Instant> buckets = generateBuckets(from, to, span);
        List<IrisDashboardTimeSeriesEntryDTO> entries = switch (metric) {
            case SESSIONS -> computeSessionsTimeSeries(from, to, buckets);
            case MESSAGES -> computeMessagesTimeSeries(from, to, buckets);
            case NO_RESPONSE_RATE -> computeNoResponseRateTimeSeries(from, to, buckets);
            case RESPONSE_TIME -> computeResponseTimeTimeSeries(from, to, buckets);
            case RATINGS -> computeRatingsTimeSeries(from, to, buckets);
            case TOKEN_COST -> computeTokenCostTimeSeries(from, to, buckets);
            case ENGAGEMENT -> computeEngagementTimeSeries(from, to, buckets);
        };
        return new IrisDashboardTimeSeriesDTO(metric, entries);
    }

    /**
     * Generates bucket start instants for the given range and span.
     *
     * @param from start of the range (inclusive)
     * @param to   end of the range (exclusive)
     * @param span one of DAY, WEEK, MONTH, QUARTER, YEAR
     * @return ordered list of bucket start instants
     */
    public List<Instant> generateBuckets(Instant from, Instant to, String span) {
        List<Instant> buckets = new ArrayList<>();
        LocalDate current = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = to.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endExclusive = to.equals(endDate.atStartOfDay(ZoneOffset.UTC).toInstant()) ? endDate : endDate.plusDays(1);

        while (current.isBefore(endExclusive)) {
            buckets.add(current.atStartOfDay(ZoneOffset.UTC).toInstant());
            current = switch (span.toUpperCase()) {
                case "DAY" -> current.plusDays(1);
                case "WEEK" -> current.plusWeeks(1);
                case "MONTH" -> current.plusMonths(1);
                case "QUARTER" -> current.plusMonths(3);
                case "YEAR" -> current.plusYears(1);
                default -> throw new BadRequestAlertException("Unknown time span: " + span, ENTITY_NAME, "unknownSpan");
            };
        }
        return buckets;
    }

    private Instant nextBucketStart(Instant bucketStart, List<Instant> buckets, int index, Instant rangeTo) {
        return index + 1 < buckets.size() ? buckets.get(index + 1) : rangeTo;
    }

    // -- Sessions time-series ---------------------------------------------------

    private List<IrisDashboardTimeSeriesEntryDTO> computeSessionsTimeSeries(Instant from, Instant to, List<Instant> buckets) {
        List<Object[]> rows = dashboardRepository.findSessionsWithMode(from, to);
        List<IrisDashboardTimeSeriesEntryDTO> entries = new ArrayList<>();

        for (int i = 0; i < buckets.size(); i++) {
            Instant bucketStart = buckets.get(i);
            Instant bucketEnd = nextBucketStart(bucketStart, buckets, i, to);
            long total = 0;
            long active = 0;
            for (Object[] row : rows) {
                Instant creationDate = toInstant(row[1]);
                if (!creationDate.isBefore(bucketStart) && creationDate.isBefore(bucketEnd)) {
                    total++;
                    int hasUserMessage = ((Number) row[3]).intValue();
                    if (hasUserMessage == 1) {
                        active++;
                    }
                }
            }
            Map<String, Double> series = new LinkedHashMap<>();
            series.put("total", (double) total);
            series.put("active", (double) active);
            entries.add(new IrisDashboardTimeSeriesEntryDTO(bucketStart, series));
        }
        return entries;
    }

    // -- Messages time-series ---------------------------------------------------

    private List<IrisDashboardTimeSeriesEntryDTO> computeMessagesTimeSeries(Instant from, Instant to, List<Instant> buckets) {
        List<Object[]> rows = dashboardRepository.findMessagesInRange(from, to);
        List<IrisDashboardTimeSeriesEntryDTO> entries = new ArrayList<>();

        for (int i = 0; i < buckets.size(); i++) {
            Instant bucketStart = buckets.get(i);
            Instant bucketEnd = nextBucketStart(bucketStart, buckets, i, to);
            long user = 0;
            long llm = 0;
            for (Object[] row : rows) {
                Instant sentAt = toInstant(row[1]);
                if (!sentAt.isBefore(bucketStart) && sentAt.isBefore(bucketEnd)) {
                    String sender = row[2].toString();
                    if ("USER".equals(sender)) {
                        user++;
                    }
                    else if ("LLM".equals(sender)) {
                        llm++;
                    }
                }
            }
            Map<String, Double> series = new LinkedHashMap<>();
            series.put("user", (double) user);
            series.put("llm", (double) llm);
            series.put("total", (double) (user + llm));
            entries.add(new IrisDashboardTimeSeriesEntryDTO(bucketStart, series));
        }
        return entries;
    }

    // -- No-response rate time-series -------------------------------------------

    private List<IrisDashboardTimeSeriesEntryDTO> computeNoResponseRateTimeSeries(Instant from, Instant to, List<Instant> buckets) {
        Instant staleBefore = computeStaleBefore(to, TimeUtil.now().toInstant());
        List<IrisDashboardUserMessageResultDTO> messages = mapResults(dashboardRepository.findUserMessagesWithNextMessageFullRange(from, staleBefore));
        List<IrisDashboardTimeSeriesEntryDTO> entries = new ArrayList<>();

        for (int i = 0; i < buckets.size(); i++) {
            Instant bucketStart = buckets.get(i);
            Instant bucketEnd = nextBucketStart(bucketStart, buckets, i, staleBefore);
            long eligible = 0;
            long noResponse = 0;
            for (IrisDashboardUserMessageResultDTO msg : messages) {
                if (!msg.sentAt().isBefore(bucketStart) && msg.sentAt().isBefore(bucketEnd)) {
                    eligible++;
                    if (msg.nextSender() == null) {
                        noResponse++;
                    }
                }
            }
            double rate = eligible > 0 ? (double) noResponse / eligible * 100.0 : 0.0;
            Map<String, Double> series = new LinkedHashMap<>();
            series.put("rate", rate);
            series.put("count", (double) noResponse);
            entries.add(new IrisDashboardTimeSeriesEntryDTO(bucketStart, series));
        }
        return entries;
    }

    // -- Response time time-series ----------------------------------------------

    private List<IrisDashboardTimeSeriesEntryDTO> computeResponseTimeTimeSeries(Instant from, Instant to, List<Instant> buckets) {
        List<IrisDashboardUserMessageResultDTO> messages = mapResults(dashboardRepository.findUserMessagesWithNextMessageFullRange(from, to));
        List<IrisDashboardTimeSeriesEntryDTO> entries = new ArrayList<>();

        for (int i = 0; i < buckets.size(); i++) {
            Instant bucketStart = buckets.get(i);
            Instant bucketEnd = nextBucketStart(bucketStart, buckets, i, to);
            List<Duration> bucketTimes = new ArrayList<>();
            for (IrisDashboardUserMessageResultDTO msg : messages) {
                if (!msg.sentAt().isBefore(bucketStart) && msg.sentAt().isBefore(bucketEnd) && msg.nextSender() == IrisMessageSender.LLM && msg.nextSentAt() != null) {
                    Duration d = Duration.between(msg.sentAt(), msg.nextSentAt());
                    if (!d.isNegative()) {
                        bucketTimes.add(d);
                    }
                }
            }
            bucketTimes.sort(Duration::compareTo);
            double avg = bucketTimes.isEmpty() ? 0.0 : bucketTimes.stream().mapToDouble(Duration::toMillis).average().orElse(0.0) / 1000.0;
            Map<String, Double> series = new LinkedHashMap<>();
            series.put("avg", avg);
            series.put("p50", percentile(bucketTimes, 0.5));
            series.put("p95", percentile(bucketTimes, 0.95));
            entries.add(new IrisDashboardTimeSeriesEntryDTO(bucketStart, series));
        }
        return entries;
    }

    // -- Ratings time-series ----------------------------------------------------

    private List<IrisDashboardTimeSeriesEntryDTO> computeRatingsTimeSeries(Instant from, Instant to, List<Instant> buckets) {
        List<Object[]> rows = dashboardRepository.findLlmMessagesWithRatings(from, to);
        List<IrisDashboardTimeSeriesEntryDTO> entries = new ArrayList<>();

        for (int i = 0; i < buckets.size(); i++) {
            Instant bucketStart = buckets.get(i);
            Instant bucketEnd = nextBucketStart(bucketStart, buckets, i, to);
            long thumbsUpCount = 0;
            long thumbsDownCount = 0;
            long total = 0;
            for (Object[] row : rows) {
                Instant sentAt = toInstant(row[1]);
                if (!sentAt.isBefore(bucketStart) && sentAt.isBefore(bucketEnd)) {
                    total++;
                    Boolean helpful = row[2] != null ? (Boolean) row[2] : null;
                    if (Boolean.TRUE.equals(helpful)) {
                        thumbsUpCount++;
                    }
                    else if (Boolean.FALSE.equals(helpful)) {
                        thumbsDownCount++;
                    }
                }
            }
            Map<String, Double> series = new LinkedHashMap<>();
            series.put("thumbsUp", (double) thumbsUpCount);
            series.put("thumbsDown", (double) thumbsDownCount);
            series.put("total", (double) total);
            entries.add(new IrisDashboardTimeSeriesEntryDTO(bucketStart, series));
        }
        return entries;
    }

    // -- Token cost time-series -------------------------------------------------

    private List<IrisDashboardTimeSeriesEntryDTO> computeTokenCostTimeSeries(Instant from, Instant to, List<Instant> buckets) {
        List<Object[]> rows = dashboardRepository.findTokenCostWithTimestamps(from, to);
        List<IrisDashboardTimeSeriesEntryDTO> entries = new ArrayList<>();

        for (int i = 0; i < buckets.size(); i++) {
            Instant bucketStart = buckets.get(i);
            Instant bucketEnd = nextBucketStart(bucketStart, buckets, i, to);
            double chatCost = 0.0;
            double otherCost = 0.0;
            for (Object[] row : rows) {
                Instant traceTime = toInstant(row[0]);
                if (!traceTime.isBefore(bucketStart) && traceTime.isBefore(bucketEnd)) {
                    int chatAttributed = ((Number) row[1]).intValue();
                    double costEur = ((Number) row[2]).doubleValue();
                    if (chatAttributed == 1) {
                        chatCost += costEur;
                    }
                    else {
                        otherCost += costEur;
                    }
                }
            }
            Map<String, Double> series = new LinkedHashMap<>();
            series.put("chat", chatCost);
            series.put("other", otherCost);
            series.put("total", chatCost + otherCost);
            entries.add(new IrisDashboardTimeSeriesEntryDTO(bucketStart, series));
        }
        return entries;
    }

    // -- Engagement time-series -------------------------------------------------

    private List<IrisDashboardTimeSeriesEntryDTO> computeEngagementTimeSeries(Instant from, Instant to, List<Instant> buckets) {
        List<Object[]> rows = dashboardRepository.findSessionsWithMode(from, to);
        List<IrisDashboardTimeSeriesEntryDTO> entries = new ArrayList<>();

        for (int i = 0; i < buckets.size(); i++) {
            Instant bucketStart = buckets.get(i);
            Instant bucketEnd = nextBucketStart(bucketStart, buckets, i, to);
            long total = 0;
            long active = 0;
            for (Object[] row : rows) {
                Instant creationDate = toInstant(row[1]);
                if (!creationDate.isBefore(bucketStart) && creationDate.isBefore(bucketEnd)) {
                    total++;
                    int hasUserMessage = ((Number) row[3]).intValue();
                    if (hasUserMessage == 1) {
                        active++;
                    }
                }
            }
            double rate = total > 0 ? (double) active / total * 100.0 : 0.0;
            Map<String, Double> series = new LinkedHashMap<>();
            series.put("rate", rate);
            series.put("activeSessions", (double) active);
            series.put("totalSessions", (double) total);
            entries.add(new IrisDashboardTimeSeriesEntryDTO(bucketStart, series));
        }
        return entries;
    }

    // -- Breakdowns -------------------------------------------------------------

    /**
     * Computes a breakdown by the given dimension.
     *
     * @param from      start of the range (inclusive)
     * @param to        end of the range (exclusive)
     * @param dimension the breakdown dimension
     * @return the breakdown entries
     */
    public List<IrisDashboardBreakdownEntryDTO> computeBreakdown(Instant from, Instant to, IrisDashboardBreakdownDimension dimension) {
        return switch (dimension) {
            case CHAT_MODE -> computeChatModeBreakdown(from, to);
            case COURSE -> computeCourseBreakdown(from, to);
            case MODEL -> computeModelBreakdown(from, to);
        };
    }

    private List<IrisDashboardBreakdownEntryDTO> computeChatModeBreakdown(Instant from, Instant to) {
        Instant staleBefore = computeStaleBefore(to, TimeUtil.now().toInstant());
        List<Object[]> sessionRows = dashboardRepository.findSessionsWithMode(from, to);
        List<IrisDashboardUserMessageResultDTO> userMessages = mapResults(dashboardRepository.findUserMessagesWithNextMessageFullRange(from, staleBefore));

        // Group sessions by mode
        Map<String, Long> sessionsByMode = new HashMap<>();
        Map<String, Long> activeSessionsByMode = new HashMap<>();
        for (Object[] row : sessionRows) {
            String mode = row[2] != null ? row[2].toString() : "UNKNOWN";
            sessionsByMode.merge(mode, 1L, Long::sum);
            int hasUserMessage = ((Number) row[3]).intValue();
            if (hasUserMessage == 1) {
                activeSessionsByMode.merge(mode, 1L, Long::sum);
            }
        }

        // Group user messages by mode for no-response rate
        Map<String, Long> eligibleByMode = new HashMap<>();
        Map<String, Long> noResponseByMode = new HashMap<>();
        for (IrisDashboardUserMessageResultDTO msg : userMessages) {
            String mode = msg.modeLabel() != null ? msg.modeLabel() : "UNKNOWN";
            eligibleByMode.merge(mode, 1L, Long::sum);
            if (msg.nextSender() == null) {
                noResponseByMode.merge(mode, 1L, Long::sum);
            }
        }

        List<IrisDashboardBreakdownEntryDTO> entries = new ArrayList<>();
        for (String mode : sessionsByMode.keySet()) {
            long sessions = sessionsByMode.getOrDefault(mode, 0L);
            long active = activeSessionsByMode.getOrDefault(mode, 0L);
            long eligible = eligibleByMode.getOrDefault(mode, 0L);
            long noResp = noResponseByMode.getOrDefault(mode, 0L);
            double noRespRate = eligible > 0 ? (double) noResp / eligible * 100.0 : 0.0;
            Map<String, Double> values = new LinkedHashMap<>();
            values.put("sessions", (double) sessions);
            values.put("activeSessions", (double) active);
            values.put("noResponseRate", noRespRate);
            entries.add(new IrisDashboardBreakdownEntryDTO(mode, values));
        }
        return entries;
    }

    private List<IrisDashboardBreakdownEntryDTO> computeCourseBreakdown(Instant from, Instant to) {
        List<Object[]> rows = dashboardRepository.findTopCoursesBySessionCount(from, to, 10);
        List<Long> courseIds = rows.stream().map(row -> ((Number) row[0]).longValue()).toList();
        Map<Long, String> courseNameMap = resolveCourseNames(courseIds);
        List<IrisDashboardBreakdownEntryDTO> entries = new ArrayList<>();

        for (Object[] row : rows) {
            long courseId = ((Number) row[0]).longValue();
            long sessionCount = ((Number) row[1]).longValue();
            String courseName = courseNameMap.getOrDefault(courseId, "Unknown Course #" + courseId);
            Map<String, Double> values = new LinkedHashMap<>();
            values.put("sessions", (double) sessionCount);
            entries.add(new IrisDashboardBreakdownEntryDTO(courseName, values));
        }
        return entries;
    }

    private Map<Long, String> resolveCourseNames(List<Long> courseIds) {
        Map<Long, String> nameMap = new HashMap<>();
        for (Course course : courseRepository.findAllById(courseIds)) {
            nameMap.put(course.getId(), course.getTitle());
        }
        return nameMap;
    }

    private List<IrisDashboardBreakdownEntryDTO> computeModelBreakdown(Instant from, Instant to) {
        List<Object[]> rows = dashboardRepository.computeTokenCostByModel(from, to);
        List<IrisDashboardBreakdownEntryDTO> entries = new ArrayList<>();

        for (Object[] row : rows) {
            String model = row[0] != null ? row[0].toString() : "unknown";
            long totalTokens = ((Number) row[1]).longValue();
            double costEur = ((Number) row[2]).doubleValue();
            Map<String, Double> values = new LinkedHashMap<>();
            values.put("totalTokens", (double) totalTokens);
            values.put("costEur", costEur);
            entries.add(new IrisDashboardBreakdownEntryDTO(model, values));
        }
        return entries;
    }
}
