package de.tum.cit.aet.artemis.iris.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
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

/**
 * Parses Iris citation payloads from chat contents and resolves lecture name and lecture unit name for the referenced lecture units
 * Only processes lecture citations with format: [cite:L:entityID:page:start:end:keyword:summary]
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisCitationService {

    // Keep in sync with Iris client regex in src/main/webapp/app/iris/overview/citation-text/iris-citation-text.model.ts
    // and the regex defined in Pyris.
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[cite:L:(?<entityId>\\d+):[^:]*:[^:]*:[^:]*:[^:]*:[^\\]]*\\]");

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final IrisSessionRepository irisSessionRepository;

    public IrisCitationService(Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, IrisSessionRepository irisSessionRepository) {
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.irisSessionRepository = irisSessionRepository;
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
            var lectureTitle = unit.getLecture() != null ? unit.getLecture().getTitle() : null;
            var lectureUnitTitle = unit.getName();
            if (lectureTitle == null || lectureTitle.isBlank() || lectureUnitTitle == null || lectureUnitTitle.isBlank()) {
                return null;
            }
            return new IrisCitationMetaDTO(unit.getId(), lectureTitle, lectureUnitTitle);
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

    private Set<Long> extractEntityIds(String text) {
        return CITATION_PATTERN.matcher(text).results().map(match -> match.group("entityId")).map(id -> {
            try {
                return Long.parseLong(id);
            }
            catch (NumberFormatException ex) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
