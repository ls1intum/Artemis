package de.tum.cit.aet.artemis.iris.service;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.dashboard.IrisDashboardBreakdownDimension;
import de.tum.cit.aet.artemis.iris.domain.dashboard.IrisDashboardMetric;
import de.tum.cit.aet.artemis.iris.domain.dashboard.IrisDashboardSessionType;
import de.tum.cit.aet.artemis.iris.domain.dashboard.IrisDashboardSpan;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardBreakdownEntryDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardConfigDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardOverviewDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardTimeSeriesDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardTimeSeriesEntryDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardUserMessageResultDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository.MessageRow;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository.SessionRow;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository.TokenUsageRow;

@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisAdminDashboardService {

    private static final Set<String> ASSISTANT_SENDERS = Set.of(IrisMessageSender.LLM.name(), IrisMessageSender.ARTIFACT.name());

    private static final List<String> SESSION_SERIES = List.of("PROGRAMMING_EXERCISE_CHAT", "TEXT_EXERCISE_CHAT", "COURSE_CHAT", "LECTURE_CHAT", "TUTOR_SUGGESTION");

    private static final List<String> MESSAGE_SERIES = List.of("USER", "LLM", "ARTIFACT");

    private static final List<String> RATING_SERIES = List.of("THUMBS_UP", "THUMBS_DOWN", "UNRATED");

    private static final List<String> TOKEN_COST_SERIES = List.of("CHAT_ATTRIBUTED", "OTHER_IRIS");

    private static final String DASHBOARD_DATA_CACHE_NAME = "iris-dashboard-data";

    private final IrisAdminDashboardRepository repository;

    private final IrisDashboardProperties properties;

    private final CacheManager cacheManager;

    public IrisAdminDashboardService(IrisAdminDashboardRepository repository, IrisDashboardProperties properties, CacheManager cacheManager) {
        this.repository = repository;
        this.properties = properties;
        this.cacheManager = cacheManager;
    }

    /**
     * Computes KPI cards for the requested dashboard window.
     *
     * @param from     the inclusive UTC start
     * @param to       the exclusive UTC end
     * @param chatMode optional chat mode filter
     * @return aggregated overview KPIs
     */
    public IrisDashboardOverviewDTO getOverview(Instant from, Instant to, @Nullable String chatMode) {
        QueryWindow window = validateWindow(from, to);
        return buildOverview(loadData(window, resolveSessionType(chatMode)));
    }

    /**
     * Computes one time-series chart for the requested metric and bucket span.
     *
     * @param from     the inclusive UTC start
     * @param to       the exclusive UTC end
     * @param span     bucket span
     * @param metric   metric to compute
     * @param chatMode optional chat mode filter
     * @return time-series entries for the requested metric
     */
    public IrisDashboardTimeSeriesDTO getTimeSeries(Instant from, Instant to, IrisDashboardSpan span, IrisDashboardMetric metric, @Nullable String chatMode) {
        QueryWindow window = validateWindow(from, to);
        DashboardData data = loadData(window, resolveSessionType(chatMode));
        List<Instant> buckets = buckets(window.from(), window.to(), span);
        LinkedHashMap<Instant, Map<String, Double>> values = emptyBuckets(buckets);

        switch (metric) {
            case SESSIONS -> {
                data.sessions().forEach(session -> add(values, bucketStart(session.creationDate().toInstant(), span), session.sessionType(), 1.0));
                ensureSeries(values, SESSION_SERIES);
            }
            case MESSAGES -> {
                data.messages().forEach(message -> add(values, bucketStart(message.sentAt().toInstant(), span), message.sender(), 1.0));
                ensureSeries(values, MESSAGE_SERIES);
            }
            case NO_RESPONSE_RATE -> populateNoResponseSeries(values, data.noResponseCandidates(), span);
            case RESPONSE_TIME -> populateResponseTimeSeries(values, data.userMessageResults(), span);
            case RATINGS -> {
                data.messages().stream().filter(IrisAdminDashboardService::isLlmMessage)
                        .forEach(message -> add(values, bucketStart(message.sentAt().toInstant(), span), ratingSeries(message.helpful()), 1.0));
                ensureSeries(values, RATING_SERIES);
            }
            case TOKEN_COST -> {
                data.tokenUsage().forEach(row -> add(values, bucketStart(row.time().toInstant(), span), row.chatAttributed() ? "CHAT_ATTRIBUTED" : "OTHER_IRIS", row.costEur()));
                ensureSeries(values, TOKEN_COST_SERIES);
            }
            case ENGAGEMENT -> populateEngagementSeries(values, data.sessions(), data.messages(), span);
        }

        return new IrisDashboardTimeSeriesDTO(metric, values.entrySet().stream().map(entry -> new IrisDashboardTimeSeriesEntryDTO(entry.getKey(), entry.getValue())).toList());
    }

    /**
     * Computes one dashboard breakdown table for the requested dimension.
     *
     * @param from      the inclusive UTC start
     * @param to        the exclusive UTC end
     * @param dimension breakdown dimension
     * @param chatMode  optional chat mode filter
     * @return breakdown entries for the requested dimension
     */
    public List<IrisDashboardBreakdownEntryDTO> getBreakdown(Instant from, Instant to, IrisDashboardBreakdownDimension dimension, @Nullable String chatMode) {
        QueryWindow window = validateWindow(from, to);
        DashboardData data = loadData(window, resolveSessionType(chatMode));
        return switch (dimension) {
            case CHAT_MODE -> chatModeBreakdown(data);
            case COURSE -> courseBreakdown(data);
            case MODEL -> modelBreakdown(data);
        };
    }

    /**
     * Returns the read-only dashboard configuration exposed to admins.
     *
     * @return the dashboard configuration DTO
     */
    public IrisDashboardConfigDTO getConfig() {
        var digest = properties.getDigest();
        var alert = properties.getAlert();
        return new IrisDashboardConfigDTO(properties.getMaxQueryWindowDays(), properties.getStaleThresholdMinutes(),
                new IrisDashboardConfigDTO.DigestDTO(digest.isEnabled(), digest.getCron(), List.copyOf(digest.getRecipients())),
                new IrisDashboardConfigDTO.AlertDTO(alert.isEnabled(), alert.getNoResponseRateThreshold(), alert.getCheckIntervalMinutes(), alert.getCooldownMinutes(),
                        alert.getLookbackMinutes(), alert.getMinimumActiveSessions(), alert.getMinimumUserMessages(), List.copyOf(alert.getRecipients())));
    }

    private IrisDashboardOverviewDTO buildOverview(DashboardData data) {
        List<MessageRow> userMessages = data.messages().stream().filter(message -> IrisMessageSender.USER.name().equals(message.sender())).toList();
        long totalSessions = totalSessionIds(data.sessions(), data.messages()).size();
        Set<Long> activeSessionIds = userMessages.stream().map(MessageRow::sessionId).collect(Collectors.toSet());
        long uniqueUsers = userMessages.stream().map(MessageRow::userId).filter(Objects::nonNull).collect(Collectors.toSet()).size();
        NoResponseStats noResponseStats = noResponseStats(data.noResponseCandidates());
        RatingStats ratingStats = ratingStats(data.messages());
        List<Double> responseTimes = responseTimes(data.userMessageResults());

        return new IrisDashboardOverviewDTO(totalSessions, activeSessionIds.size(), rate(activeSessionIds.size(), totalSessions), data.messages().size(), uniqueUsers,
                userMessages.size(), noResponseStats.eligibleSessions(), noResponseStats.rate(), noResponseStats.failedMessages(), noResponseStats.failedSessions(),
                ratingStats.thumbsUpRatio(), ratingStats.thumbsDownRatio(), ratingStats.thumbsUpAbsoluteRate(), ratingStats.thumbsDownAbsoluteRate(),
                ratingStats.sessionsWithThumbsUp(), ratingStats.sessionsWithThumbsDown(), average(responseTimes), percentile(responseTimes, 0.5), percentile(responseTimes, 0.95),
                data.tokenUsage().stream().mapToDouble(TokenUsageRow::costEur).sum());
    }

    private List<IrisDashboardBreakdownEntryDTO> chatModeBreakdown(DashboardData data) {
        return SESSION_SERIES.stream().map(sessionType -> {
            List<SessionRow> sessions = data.sessions().stream().filter(row -> sessionType.equals(row.sessionType())).toList();
            List<MessageRow> messages = data.messages().stream().filter(row -> sessionType.equals(row.sessionType())).toList();
            NoResponseStats noResponseStats = noResponseStats(data.noResponseCandidates().stream().filter(row -> sessionType.equals(row.sessionType())).toList());
            RatingStats ratingStats = ratingStats(messages);
            List<Double> responseTimes = responseTimes(data.userMessageResults().stream().filter(row -> sessionType.equals(row.sessionType())).toList());
            double cost = data.tokenUsage().stream().filter(row -> row.chatAttributed() && sessionType.equals(row.sessionType())).mapToDouble(TokenUsageRow::costEur).sum();
            return new IrisDashboardBreakdownEntryDTO(sessionType,
                    metrics(Map.of("sessions", (double) totalSessionIds(sessions, messages).size(), "messages", (double) messages.size(), "noResponseRate", noResponseStats.rate(),
                            "thumbsUpRatio", ratingStats.thumbsUpRatio(), "thumbsDownRatio", ratingStats.thumbsDownRatio(), "averageResponseTimeSeconds", average(responseTimes),
                            "costEur", cost)));
        }).toList();
    }

    private List<IrisDashboardBreakdownEntryDTO> courseBreakdown(DashboardData data) {
        Set<Long> courseIds = Stream
                .of(data.sessions().stream().map(SessionRow::courseId), data.messages().stream().map(MessageRow::courseId), data.tokenUsage().stream().map(TokenUsageRow::courseId))
                .flatMap(Function.identity()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> courseNames = new HashMap<>();
        Stream.of(data.sessions().stream().map(row -> new CourseNameCandidate(row.courseId(), row.courseTitle())),
                data.messages().stream().map(row -> new CourseNameCandidate(row.courseId(), row.courseTitle())),
                data.tokenUsage().stream().map(row -> new CourseNameCandidate(row.courseId(), row.courseTitle()))).flatMap(Function.identity())
                .filter(candidate -> candidate.courseId() != null).filter(candidate -> StringUtils.hasText(candidate.courseTitle()))
                .forEach(candidate -> courseNames.putIfAbsent(candidate.courseId(), candidate.courseTitle()));

        return courseIds.stream().map(courseId -> {
            List<SessionRow> sessions = data.sessions().stream().filter(row -> Objects.equals(courseId, row.courseId())).toList();
            List<MessageRow> messages = data.messages().stream().filter(row -> Objects.equals(courseId, row.courseId())).toList();
            NoResponseStats noResponseStats = noResponseStats(data.noResponseCandidates().stream().filter(row -> Objects.equals(courseId, row.courseId())).toList());
            double cost = data.tokenUsage().stream().filter(row -> Objects.equals(courseId, row.courseId())).mapToDouble(TokenUsageRow::costEur).sum();
            String name = courseNames.getOrDefault(courseId, "Course " + courseId) + " (" + courseId + ")";
            return new IrisDashboardBreakdownEntryDTO(name, metrics(Map.of("sessions", (double) totalSessionIds(sessions, messages).size(), "messages", (double) messages.size(),
                    "noResponseRate", noResponseStats.rate(), "costEur", cost)));
        }).sorted(Comparator.comparingDouble((IrisDashboardBreakdownEntryDTO entry) -> entry.metrics().getOrDefault("sessions", 0.0)).reversed()
                .thenComparing(Comparator.comparingDouble((IrisDashboardBreakdownEntryDTO entry) -> entry.metrics().getOrDefault("messages", 0.0)).reversed())
                .thenComparing(Comparator.comparingDouble((IrisDashboardBreakdownEntryDTO entry) -> entry.metrics().getOrDefault("costEur", 0.0)).reversed())).limit(10).toList();
    }

    private List<IrisDashboardBreakdownEntryDTO> modelBreakdown(DashboardData data) {
        return data.tokenUsage().stream()
                .collect(Collectors.groupingBy(row -> StringUtils.hasText(row.model()) ? row.model() : "UNKNOWN", LinkedHashMap::new, Collectors.toCollection(ArrayList::new)))
                .entrySet().stream().map(entry -> {
                    double inputTokens = entry.getValue().stream().mapToDouble(TokenUsageRow::inputTokens).sum();
                    double outputTokens = entry.getValue().stream().mapToDouble(TokenUsageRow::outputTokens).sum();
                    double cost = entry.getValue().stream().mapToDouble(TokenUsageRow::costEur).sum();
                    return new IrisDashboardBreakdownEntryDTO(entry.getKey(),
                            metrics(Map.of("inputTokens", inputTokens, "outputTokens", outputTokens, "totalTokens", inputTokens + outputTokens, "costEur", cost)));
                }).sorted(Comparator.comparingDouble((IrisDashboardBreakdownEntryDTO entry) -> entry.metrics().getOrDefault("costEur", 0.0)).reversed()).toList();
    }

    private DashboardData loadData(QueryWindow window, @Nullable IrisDashboardSessionType sessionType) {
        DashboardDataCacheKey cacheKey = new DashboardDataCacheKey(window.from(), window.to(), sessionType);
        Cache cache = cacheManager.getCache(DASHBOARD_DATA_CACHE_NAME);
        if (cache == null) {
            return fetchData(window, sessionType);
        }
        DashboardData cachedData = cache.get(cacheKey, DashboardData.class);
        if (cachedData != null) {
            return cachedData;
        }
        DashboardData data = fetchData(window, sessionType);
        cache.put(cacheKey, data);
        return data;
    }

    private DashboardData fetchData(QueryWindow window, @Nullable IrisDashboardSessionType sessionType) {
        List<IrisDashboardUserMessageResultDTO> userMessageResults = repository.findUserMessageResults(window.fromZoned(), window.toZoned(), sessionType);
        List<IrisDashboardUserMessageResultDTO> noResponseCandidates = userMessageResults.stream()
                .filter(result -> !result.userSentAt().isBefore(window.fromZoned()) && result.userSentAt().isBefore(window.staleBeforeZoned())).toList();
        return new DashboardData(window, repository.findSessions(window.fromZoned(), window.toZoned(), sessionType),
                repository.findMessages(window.fromZoned(), window.toZoned(), sessionType), userMessageResults, noResponseCandidates,
                repository.findTokenUsage(window.fromZoned(), window.toZoned(), sessionType));
    }

    private QueryWindow validateWindow(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameter 'from' must be before parameter 'to'.");
        }
        Duration duration = Duration.between(from, to);
        if (duration.compareTo(Duration.ofDays(properties.getMaxQueryWindowDays())) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Iris dashboard query window must not exceed " + properties.getMaxQueryWindowDays() + " days.");
        }
        Instant latestAllowedTo = TimeUtil.now().toInstant().plus(1, ChronoUnit.DAYS);
        if (to.isAfter(latestAllowedTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameter 'to' must not be more than one day in the future.");
        }
        Instant staleBefore = min(to, TimeUtil.now().toInstant().minus(properties.getStaleThresholdMinutes(), ChronoUnit.MINUTES));
        return new QueryWindow(from, to, from.atZone(ZoneOffset.UTC), to.atZone(ZoneOffset.UTC), staleBefore.atZone(ZoneOffset.UTC));
    }

    @Nullable
    private static IrisDashboardSessionType resolveSessionType(@Nullable String chatMode) {
        if (!StringUtils.hasText(chatMode)) {
            return null;
        }
        return IrisDashboardSessionType.fromRequestParameter(chatMode);
    }

    private static void populateNoResponseSeries(LinkedHashMap<Instant, Map<String, Double>> values, List<IrisDashboardUserMessageResultDTO> results, IrisDashboardSpan span) {
        Map<Instant, Set<Long>> eligibleSessions = new HashMap<>();
        Map<Instant, Set<Long>> failedSessions = new HashMap<>();
        for (IrisDashboardUserMessageResultDTO result : results) {
            Instant bucket = bucketStart(result.userSentAt().toInstant(), span);
            eligibleSessions.computeIfAbsent(bucket, ignored -> new HashSet<>()).add(result.sessionId());
            if (isNoResponse(result)) {
                failedSessions.computeIfAbsent(bucket, ignored -> new HashSet<>()).add(result.sessionId());
            }
        }
        values.forEach((bucket, series) -> series.put("NO_RESPONSE_RATE",
                rate(failedSessions.getOrDefault(bucket, Set.of()).size(), eligibleSessions.getOrDefault(bucket, Set.of()).size())));
    }

    private static void populateResponseTimeSeries(LinkedHashMap<Instant, Map<String, Double>> values, List<IrisDashboardUserMessageResultDTO> results, IrisDashboardSpan span) {
        Map<Instant, List<Double>> responseTimes = new HashMap<>();
        for (IrisDashboardUserMessageResultDTO result : results) {
            if (isAssistantResponse(result)) {
                responseTimes.computeIfAbsent(bucketStart(result.userSentAt().toInstant(), span), ignored -> new ArrayList<>())
                        .add((double) Duration.between(result.userSentAt(), result.nextSentAt()).toSeconds());
            }
        }
        values.forEach((bucket, series) -> {
            List<Double> valuesForBucket = responseTimes.getOrDefault(bucket, List.of()).stream().sorted().toList();
            series.put("AVERAGE", average(valuesForBucket));
            series.put("P50", percentile(valuesForBucket, 0.5));
            series.put("P95", percentile(valuesForBucket, 0.95));
        });
    }

    private static void populateEngagementSeries(LinkedHashMap<Instant, Map<String, Double>> values, List<SessionRow> sessions, List<MessageRow> messages, IrisDashboardSpan span) {
        Map<Instant, Set<Long>> totalSessions = new HashMap<>();
        sessions.forEach(session -> totalSessions.computeIfAbsent(bucketStart(session.creationDate().toInstant(), span), ignored -> new HashSet<>()).add(session.sessionId()));
        messages.forEach(message -> totalSessions.computeIfAbsent(bucketStart(message.sentAt().toInstant(), span), ignored -> new HashSet<>()).add(message.sessionId()));
        Map<Instant, Set<Long>> activeSessions = new HashMap<>();
        messages.stream().filter(message -> IrisMessageSender.USER.name().equals(message.sender()))
                .forEach(message -> activeSessions.computeIfAbsent(bucketStart(message.sentAt().toInstant(), span), ignored -> new HashSet<>()).add(message.sessionId()));
        values.forEach(
                (bucket, series) -> series.put("ENGAGEMENT_RATE", rate(activeSessions.getOrDefault(bucket, Set.of()).size(), totalSessions.getOrDefault(bucket, Set.of()).size())));
    }

    private static Set<Long> totalSessionIds(List<SessionRow> sessions, List<MessageRow> messages) {
        Set<Long> sessionIds = sessions.stream().map(SessionRow::sessionId).collect(Collectors.toCollection(HashSet::new));
        messages.stream().map(MessageRow::sessionId).forEach(sessionIds::add);
        return sessionIds;
    }

    private static NoResponseStats noResponseStats(List<IrisDashboardUserMessageResultDTO> results) {
        long failedMessages = results.stream().filter(IrisAdminDashboardService::isNoResponse).count();
        long failedSessions = results.stream().filter(IrisAdminDashboardService::isNoResponse).map(IrisDashboardUserMessageResultDTO::sessionId).distinct().count();
        long eligibleSessions = results.stream().map(IrisDashboardUserMessageResultDTO::sessionId).distinct().count();
        return new NoResponseStats(failedMessages, failedSessions, eligibleSessions, rate(failedSessions, eligibleSessions));
    }

    private static RatingStats ratingStats(List<MessageRow> messages) {
        List<MessageRow> llmMessages = messages.stream().filter(IrisAdminDashboardService::isLlmMessage).toList();
        long thumbsUp = llmMessages.stream().filter(message -> Boolean.TRUE.equals(message.helpful())).count();
        long thumbsDown = llmMessages.stream().filter(message -> Boolean.FALSE.equals(message.helpful())).count();
        long ratedMessages = thumbsUp + thumbsDown;
        long sessionsWithThumbsUp = llmMessages.stream().filter(message -> Boolean.TRUE.equals(message.helpful())).map(MessageRow::sessionId).distinct().count();
        long sessionsWithThumbsDown = llmMessages.stream().filter(message -> Boolean.FALSE.equals(message.helpful())).map(MessageRow::sessionId).distinct().count();
        return new RatingStats(rate(thumbsUp, ratedMessages), rate(thumbsDown, ratedMessages), rate(thumbsUp, llmMessages.size()), rate(thumbsDown, llmMessages.size()),
                sessionsWithThumbsUp, sessionsWithThumbsDown);
    }

    private static List<Double> responseTimes(List<IrisDashboardUserMessageResultDTO> results) {
        return results.stream().filter(IrisAdminDashboardService::isAssistantResponse)
                .map(result -> (double) Duration.between(result.userSentAt(), result.nextSentAt()).toSeconds()).sorted().toList();
    }

    private static boolean isAssistantResponse(IrisDashboardUserMessageResultDTO result) {
        return result.nextSentAt() != null && ASSISTANT_SENDERS.contains(result.nextSender());
    }

    private static boolean isNoResponse(IrisDashboardUserMessageResultDTO result) {
        return result.nextSender() == null || IrisMessageSender.USER.name().equals(result.nextSender());
    }

    private static boolean isLlmMessage(MessageRow message) {
        return IrisMessageSender.LLM.name().equals(message.sender());
    }

    private static String ratingSeries(@Nullable Boolean helpful) {
        if (Boolean.TRUE.equals(helpful)) {
            return "THUMBS_UP";
        }
        if (Boolean.FALSE.equals(helpful)) {
            return "THUMBS_DOWN";
        }
        return "UNRATED";
    }

    private static LinkedHashMap<String, Double> metrics(Map<String, Double> unorderedMetrics) {
        return unorderedMetrics.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
    }

    private static LinkedHashMap<Instant, Map<String, Double>> emptyBuckets(List<Instant> buckets) {
        return buckets.stream().collect(Collectors.toMap(Function.identity(), ignored -> new LinkedHashMap<>(), (left, right) -> left, LinkedHashMap::new));
    }

    private static void add(LinkedHashMap<Instant, Map<String, Double>> values, Instant bucket, String series, double value) {
        Map<String, Double> seriesValues = values.get(bucket);
        if (seriesValues != null) {
            seriesValues.merge(series, value, Double::sum);
        }
    }

    private static void ensureSeries(LinkedHashMap<Instant, Map<String, Double>> values, List<String> seriesNames) {
        values.values().forEach(series -> seriesNames.forEach(name -> series.putIfAbsent(name, 0.0)));
    }

    private static List<Instant> buckets(Instant from, Instant to, IrisDashboardSpan span) {
        List<Instant> buckets = new ArrayList<>();
        Instant cursor = bucketStart(from, span);
        while (cursor.isBefore(to)) {
            buckets.add(cursor);
            cursor = nextBucket(cursor, span);
        }
        return buckets;
    }

    private static Instant bucketStart(Instant instant, IrisDashboardSpan span) {
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        return switch (span) {
            case DAY -> utc.truncatedTo(ChronoUnit.DAYS).toInstant();
            case WEEK -> utc.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).toInstant();
            case MONTH -> utc.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        };
    }

    private static Instant nextBucket(Instant instant, IrisDashboardSpan span) {
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        return switch (span) {
            case DAY -> utc.plusDays(1).toInstant();
            case WEEK -> utc.plusWeeks(1).toInstant();
            case MONTH -> utc.plusMonths(1).toInstant();
        };
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    static double percentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.getFirst();
        }
        double position = percentile * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(position);
        int upperIndex = (int) Math.ceil(position);
        double lower = sortedValues.get(lowerIndex);
        double upper = sortedValues.get(upperIndex);
        return lower + (upper - lower) * (position - lowerIndex);
    }

    private static double rate(double numerator, double denominator) {
        return denominator == 0 ? 0.0 : numerator / denominator * 100.0;
    }

    private static Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private record QueryWindow(Instant from, Instant to, ZonedDateTime fromZoned, ZonedDateTime toZoned, ZonedDateTime staleBeforeZoned) implements Serializable {
    }

    private record DashboardData(QueryWindow window, List<SessionRow> sessions, List<MessageRow> messages, List<IrisDashboardUserMessageResultDTO> userMessageResults,
            List<IrisDashboardUserMessageResultDTO> noResponseCandidates, List<TokenUsageRow> tokenUsage) implements Serializable {
    }

    private record DashboardDataCacheKey(Instant from, Instant to, @Nullable IrisDashboardSessionType sessionType) implements Serializable {
    }

    private record NoResponseStats(long failedMessages, long failedSessions, long eligibleSessions, double rate) {
    }

    private record RatingStats(double thumbsUpRatio, double thumbsDownRatio, double thumbsUpAbsoluteRate, double thumbsDownAbsoluteRate, long sessionsWithThumbsUp,
            long sessionsWithThumbsDown) {
    }

    private record CourseNameCandidate(Long courseId, String courseTitle) {
    }
}
