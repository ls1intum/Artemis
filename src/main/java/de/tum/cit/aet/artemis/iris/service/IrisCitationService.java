package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.dto.IrisCitationMetaDTO;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Parses Iris citation payloads from chat contents and resolves metadata for the referenced lecture units.
 */
@Lazy
@Service
@Profile(PROFILE_IRIS)
public class IrisCitationService {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[cite:(?<payload>[^\\]]+)\\]");

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    public IrisCitationService(Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi) {
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
    }

    /**
     * Extracts citation placeholders from the given text and returns the metadata for each lecture unit found.
     */
    public List<IrisCitationMetaDTO> resolveCitationInfo(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var references = extractReferences(text);
        if (references.isEmpty()) {
            return List.of();
        }
        var resolved = new LinkedHashMap<Long, IrisCitationMetaDTO>();
        for (var reference : references.values()) {
            resolveLectureUnit(reference).ifPresent(value -> resolved.put(reference.entityId(), value));
        }
        return List.copyOf(resolved.values());
    }

    /**
     * Collects all contents from the supplied messages and resolves lecture unit citations contained within.
     */
    public List<IrisCitationMetaDTO> resolveCitationInfoFromMessages(List<IrisMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        var combined = messages.stream().filter(Objects::nonNull).flatMap(message -> message.getContent() != null ? message.getContent().stream() : Stream.empty())
                .map(IrisMessageContent::getContentAsString).filter(content -> content != null && !content.isBlank()).toList();
        if (combined.isEmpty()) {
            return List.of();
        }
        return resolveCitationInfo(String.join("\n", combined));
    }

    private Map<String, IrisCitationReference> extractReferences(String text) {
        var references = new LinkedHashMap<String, IrisCitationReference>();
        var matcher = CITATION_PATTERN.matcher(text);
        while (matcher.find()) {
            var payload = matcher.group("payload");
            var parsed = parseReference(payload);
            if (parsed.isPresent()) {
                var reference = parsed.get();
                references.put(String.valueOf(reference.entityId()), reference);
            }
        }
        return references;
    }

    private Optional<IrisCitationReference> parseReference(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        var parts = payload.split(":");
        if (parts.length < 2) {
            return Optional.empty();
        }
        var typeValue = parts[0];
        var entityIdValue = parts[1];
        if (!"L".equals(typeValue)) {
            return Optional.empty();
        }

        long entityId;
        try {
            entityId = Long.parseLong(entityIdValue);
        }
        catch (NumberFormatException ex) {
            return Optional.empty();
        }

        return Optional.of(new IrisCitationReference(entityId));
    }

    private Optional<IrisCitationMetaDTO> resolveLectureUnit(IrisCitationReference reference) {
        if (lectureUnitRepositoryApi.isEmpty()) {
            return Optional.empty();
        }
        try {
            LectureUnit unit = lectureUnitRepositoryApi.get().findByIdElseThrow(reference.entityId());
            var lectureTitle = unit.getLecture() != null ? unit.getLecture().getTitle() : null;
            if (lectureTitle != null && lectureTitle.isBlank()) {
                lectureTitle = null;
            }
            var lectureUnitTitle = unit.getName();
            if (lectureUnitTitle != null && lectureUnitTitle.isBlank()) {
                lectureUnitTitle = null;
            }
            return Optional.of(new IrisCitationMetaDTO(reference.entityId(), lectureTitle, lectureUnitTitle));
        }
        catch (EntityNotFoundException ex) {
            return Optional.empty();
        }
    }

    private record IrisCitationReference(long entityId) {
    }
}
