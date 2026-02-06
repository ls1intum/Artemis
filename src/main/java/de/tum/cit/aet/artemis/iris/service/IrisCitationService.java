package de.tum.cit.aet.artemis.iris.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.dto.IrisCitationMetaDTO;
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

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[cite:L:(?<entityId>\\d+):[^:]*:[^:]*:[^:]*:[^:]*:[^\\]]*\\]");

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    public IrisCitationService(Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi) {
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
    }

    /**
     * Extracts citation placeholders from the given text and returns the metadata (lecture name und lecture unit name) for each lecture unit found.
     *
     * @param text the text to parse for citation placeholders
     * @return ordered metadata for resolved lecture unit citations, or null if no citations were found
     */
    public List<IrisCitationMetaDTO> resolveCitationInfo(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        var entityIds = extractEntityIds(text);
        if (entityIds.isEmpty()) {
            return null;
        }
        var citations = entityIds.stream().map(this::resolveLectureUnit).filter(Optional::isPresent).map(Optional::get).toList();
        return citations.isEmpty() ? null : citations;
    }

    /**
     * Collects all contents from the supplied messages and resolves lecture unit citations contained within.
     *
     * @param messages the messages whose contents should be scanned for citations
     * @return ordered metadata for resolved lecture unit citations, or null if no citations were found
     */
    public List<IrisCitationMetaDTO> resolveCitationInfoFromMessages(List<IrisMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        var contentStrings = messages.stream().filter(Objects::nonNull).filter(message -> message.getContent() != null).flatMap(message -> message.getContent().stream())
                .map(IrisMessageContent::getContentAsString).filter(content -> content != null && !content.isBlank()).toList();
        if (contentStrings.isEmpty()) {
            return null;
        }
        return resolveCitationInfo(String.join("\n", contentStrings));
    }

    private Set<Long> extractEntityIds(String text) {
        var entityIds = new LinkedHashSet<Long>();
        var matcher = CITATION_PATTERN.matcher(text);
        while (matcher.find()) {
            var entityIdString = matcher.group("entityId");
            try {
                entityIds.add(Long.parseLong(entityIdString));
            }
            catch (NumberFormatException ex) {
                // Skip invalid entityId
            }
        }
        return entityIds;
    }

    private Optional<IrisCitationMetaDTO> resolveLectureUnit(long entityId) {
        if (lectureUnitRepositoryApi.isEmpty()) {
            return Optional.empty();
        }
        try {
            LectureUnit unit = lectureUnitRepositoryApi.get().findByIdElseThrow(entityId);
            var lectureTitle = unit.getLecture() != null ? unit.getLecture().getTitle() : null;
            var lectureUnitTitle = unit.getName();
            if (lectureTitle == null || lectureTitle.isBlank() || lectureUnitTitle == null || lectureUnitTitle.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new IrisCitationMetaDTO(entityId, lectureTitle, lectureUnitTitle));
        }
        catch (EntityNotFoundException ex) {
            return Optional.empty();
        }
    }
}
