package de.tum.cit.aet.artemis.iris.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisCitationMetaDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitIngestedVersionsDTO;

/**
 * Parses Iris citation payloads from chat contents and resolves lecture name and lecture unit name for the referenced lecture units.
 * <p>
 * Only processes lecture citations with format: {@code [cite:L:entityID:page:start:end:keyword:summary]}, optionally followed by one version field:
 * {@code [cite:L:entityID:page:start:end:keyword:summary:va3]} for slides, {@code :vt3} for a video. A citation is about either a slide or a video, never both, so the tag
 * carries which kind of material it is about along with the version, in a single field.
 * <p>
 * The tag exists because the summary is by design "everything up to the closing bracket" and may contain colons: a trailing field of plain digits would be
 * indistinguishable from a summary that happens to end in a number, and reading such a summary as a version would let a citation of changed material pass as current. A
 * field that has to start with {@code va} or {@code vt} cannot be produced by a number at the end of a sentence.
 * <p>
 * The version field is appended by {@link #stampCitationVersions(String)} before the assistant message is persisted, and pins the citation to the version of the material
 * it was generated from. When a citation is clicked, the client fetches the versions the unit currently offers and compares them against the pinned one. Markers without
 * the field (written before this feature existed) keep behaving exactly as before.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisCitationService {

    /** Fields a stamped citation has after the end timestamp at minimum: keyword, summary and the version field. A summary may itself contain colons, so it can have more. */
    private static final int MIN_TRAILING_FIELDS_WHEN_STAMPED = 3;

    // Keep in sync with the Iris client regex in src/main/webapp/app/iris/overview/citation-text/iris-citation-text.model.ts and the regex defined in Pyris.
    // The version field needs no expression of its own: the trailing summary group already accepts colons and therefore covers it.
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[cite:L:(?<entityId>\\d+):[^:\\]]*:[^:\\]]*:[^:\\]]*:[^:\\]]*:[^\\]]*\\]");

    /**
     * Splits a lecture citation into the parts needed for stamping. The trailing {@code rest} group holds {@code keyword:summary}, plus the version field once stamped;
     * summaries may contain colons, which is why it is matched greedily up to the closing bracket.
     */
    private static final Pattern STAMPABLE_CITATION_PATTERN = Pattern
            .compile("\\[cite:L:(?<entityId>\\d+):(?<page>[^:\\]]*):(?<start>[^:\\]]*):(?<end>[^:\\]]*):(?<rest>[^\\]]*)\\]");

    /** Tag of a version field pinning a slide citation to the version of its attachment. */
    private static final String ATTACHMENT_VERSION_TAG = "va";

    /** Tag of a version field pinning a video citation to the version of its transcription. */
    private static final String VIDEO_VERSION_TAG = "vt";

    /** A version field is one of the two tags followed by a number. Nothing else counts as one, which is what keeps a numeric summary from being read as a version. */
    private static final Pattern VERSION_FIELD_PATTERN = Pattern.compile("v[at]\\d+");

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final IrisSessionRepository irisSessionRepository;

    public IrisCitationService(Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, IrisSessionRepository irisSessionRepository) {
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.irisSessionRepository = irisSessionRepository;
    }

    /**
     * Pins every lecture citation in the supplied text to the version of the material it was generated from.
     * <p>
     * Citations that are already stamped, reference an unresolvable lecture unit, or point at material Iris has not finished ingesting are left untouched: without a
     * trustworthy version it is better to keep the citation unverified than to pin it to the wrong one.
     *
     * @param text the raw assistant answer; may be {@code null} or blank
     * @return the text with pinned-version fields appended, or the unchanged text when there is nothing to stamp
     */
    public String stampCitationVersions(String text) {
        if (text == null || text.isBlank() || lectureUnitRepositoryApi.isEmpty()) {
            return text;
        }
        var ingestedVersions = loadIngestedVersions(extractEntityIds(text));
        if (ingestedVersions.isEmpty()) {
            return text;
        }
        // Built manually instead of via Matcher#replaceAll, because keywords and summaries are LLM-generated and may contain "$" or "\", which would be interpreted as
        // group references in a replacement string.
        var matcher = STAMPABLE_CITATION_PATTERN.matcher(text);
        var stamped = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            stamped.append(text, lastEnd, matcher.start()).append(stampSingleCitation(matcher, ingestedVersions));
            lastEnd = matcher.end();
        }
        if (lastEnd == 0) {
            return text;
        }
        return stamped.append(text, lastEnd, text.length()).toString();
    }

    /**
     * Extracts citation placeholders from the supplied text and resolves metadata (lecture name and lecture unit name) for each lecture unit found.
     *
     * @param text text to scan for citation placeholders; may be {@code null} or blank
     * @return a {@link List} of {@link IrisCitationMetaDTO} for each resolved lecture unit; empty if none were found
     * @see #resolveCitationInfoFromMessages(List)
     */
    public List<IrisCitationMetaDTO> resolveCitationInfo(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (lectureUnitRepositoryApi.isEmpty()) {
            return List.of();
        }
        var entityIds = extractEntityIds(text);
        if (entityIds.isEmpty()) {
            return List.of();
        }
        var unitMap = lectureUnitRepositoryApi.get().findAllByIdsWithLecture(entityIds).stream().collect(Collectors.toMap(LectureUnit::getId, unit -> unit));
        var citations = entityIds.stream().map(unitMap::get).filter(Objects::nonNull).map(unit -> {
            var lecture = unit.getLecture();
            var lectureTitle = lecture != null ? lecture.getTitle() : null;
            var lectureUnitTitle = unit.getName();
            if (lecture == null || lectureTitle == null || lectureTitle.isBlank() || lectureUnitTitle == null || lectureUnitTitle.isBlank()) {
                return null;
            }
            var lectureId = lecture.getId();
            var courseId = lecture.getCourse() != null ? lecture.getCourse().getId() : null;
            if (courseId == null) {
                return null;
            }
            return new IrisCitationMetaDTO(unit.getId(), lectureTitle, lectureUnitTitle, lectureId, courseId);
        }).filter(Objects::nonNull).toList();
        return citations;
    }

    /**
     * Collects non-null contents from the supplied {@link IrisMessage} list, joins them, and delegates to {@link #resolveCitationInfo(String)}.
     *
     * @param messages nullable list of {@link IrisMessage}; returns empty list when {@code null} or empty
     * @return a {@link List} of {@link IrisCitationMetaDTO} for each resolved lecture unit; empty if no citations were found
     * @see #resolveCitationInfo(String)
     */
    public List<IrisCitationMetaDTO> resolveCitationInfoFromMessages(List<IrisMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        var contentStrings = messages.stream().filter(Objects::nonNull).filter(message -> message.getContent() != null).flatMap(message -> message.getContent().stream())
                .map(IrisMessageContent::getContentAsString).filter(content -> content != null && !content.isBlank()).toList();
        if (contentStrings.isEmpty()) {
            return List.of();
        }
        return resolveCitationInfo(String.join("\n", contentStrings));
    }

    /**
     * Loads the session with messages and contents (if not already initialized), resolves citation info from the messages, and sets it on the provided session.
     * <p>
     * If the session's messages and each message's content collection are already initialized (e.g. loaded by a caller's EntityGraph), the existing
     * in-memory data is used directly and no additional database query is issued. Otherwise, the session is reloaded via
     * {@link IrisSessionRepository#findByIdWithMessagesAndContents}.
     *
     * @param session the session to enrich with citation info
     */
    public void enrichSessionWithCitationInfo(IrisSession session) {
        List<IrisMessage> messages = session.getMessages();
        boolean alreadyLoaded = Hibernate.isInitialized(messages)
                && (messages == null || messages.isEmpty() || messages.stream().allMatch(m -> Hibernate.isInitialized(m.getContent())));
        if (!alreadyLoaded) {
            IrisSession sessionWithContents = irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
            messages = sessionWithContents.getMessages();
        }
        session.setCitationInfo(resolveCitationInfoFromMessages(messages));
    }

    private Map<Long, LectureUnitIngestedVersionsDTO> loadIngestedVersions(Set<Long> entityIds) {
        if (entityIds.isEmpty() || lectureUnitRepositoryApi.isEmpty()) {
            return Map.of();
        }
        return lectureUnitRepositoryApi.get().findIngestedVersionsByIds(entityIds).stream()
                .collect(Collectors.toMap(LectureUnitIngestedVersionsDTO::lectureUnitId, Function.identity(), (first, second) -> first));
    }

    private String stampSingleCitation(MatchResult match, Map<Long, LectureUnitIngestedVersionsDTO> ingestedVersions) {
        String rest = match.group("rest");
        if (isAlreadyStamped(rest)) {
            return match.group();
        }
        Long entityId = parseEntityId(match.group("entityId"));
        if (entityId == null) {
            return match.group();
        }
        var ingested = ingestedVersions.get(entityId);
        if (ingested == null) {
            return match.group();
        }
        String page = match.group("page");
        String start = match.group("start");
        String end = match.group("end");
        // A citation is about either a video or a slide, never both: a transcript segment carries a companion page number, but its timestamp is what the citation points
        // at. Deciding by the start time alone is what the client already does when it turns the citation into a link, so pinning follows the very position the click
        // navigates to. An end time without a start time therefore counts as a slide citation, exactly as it is rendered.
        boolean isVideoCitation = !start.isBlank();
        String versionField = isVideoCitation ? formatVersionField(VIDEO_VERSION_TAG, ingested.videoVersion())
                : page.isBlank() ? "" : formatVersionField(ATTACHMENT_VERSION_TAG, ingested.attachmentVersion());
        if (versionField.isEmpty()) {
            return match.group();
        }
        return "[cite:L:" + match.group("entityId") + ":" + page + ":" + start + ":" + end + ":" + rest + ":" + versionField + "]";
    }

    /**
     * Whether the given {@code keyword:summary} part already carries the trailing version field.
     * <p>
     * Keeps stamping idempotent.
     *
     * @param rest everything between the end timestamp and the closing bracket
     * @return {@code true} when the last field is a version field
     */
    private static boolean isAlreadyStamped(String rest) {
        var parts = rest.split(":", -1);
        if (parts.length < MIN_TRAILING_FIELDS_WHEN_STAMPED) {
            return false;
        }
        return VERSION_FIELD_PATTERN.matcher(parts[parts.length - 1]).matches();
    }

    private static String formatVersionField(String tag, @Nullable Integer version) {
        return version == null ? "" : tag + version;
    }

    private Set<Long> extractEntityIds(String text) {
        return CITATION_PATTERN.matcher(text).results().map(match -> match.group("entityId")).map(IrisCitationService::parseEntityId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Reads a citation's entity ID.
     * <p>
     * The citation pattern accepts any run of digits, so a model that invents an ID far beyond {@code long} still produces a well-formed citation — one that simply cannot
     * name a lecture unit. Both the lookup and the stamping run over the same matches and must therefore agree on that; letting the exception escape here would abort
     * stamping for the whole answer, and with it the persistence of the assistant message, over one unusable ID among otherwise valid citations.
     *
     * @param entityId the digits captured from the citation
     * @return the ID, or {@code null} when it does not fit in a {@code long}
     */
    private static @Nullable Long parseEntityId(String entityId) {
        try {
            return Long.parseLong(entityId);
        }
        catch (NumberFormatException ex) {
            return null;
        }
    }
}
