package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.iris.dto.IrisCitationMetaDTO;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

@Lazy
@Service
@Profile(PROFILE_IRIS)
public class IrisCitationService {

    private static final Logger log = LoggerFactory.getLogger(IrisCitationService.class);

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[cite:(?<entityId>[^:\\]]+):(?<type>[LF])(?:[^\\]]*)\\]");

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    public IrisCitationService(Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi) {
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
    }

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
            if (reference.type() == CitationType.LECTURE_UNIT) {
                resolveLectureUnit(reference).ifPresent(value -> resolved.put(reference.entityId(), value));
            }
        }
        return List.copyOf(resolved.values());
    }

    private Map<String, IrisCitationReference> extractReferences(String text) {
        var references = new LinkedHashMap<String, IrisCitationReference>();
        var matcher = CITATION_PATTERN.matcher(text);
        while (matcher.find()) {
            var entityIdValue = matcher.group("entityId");
            var typeValue = matcher.group("type");
            var parsed = parseReference(entityIdValue, typeValue);
            if (parsed.isPresent()) {
                var reference = parsed.get();
                references.put(reference.type().name() + ":" + reference.entityId(), reference);
            }
        }
        return references;
    }

    private Optional<IrisCitationReference> parseReference(String entityIdValue, String typeValue) {
        long entityId;
        try {
            entityId = Long.parseLong(entityIdValue);
        }
        catch (NumberFormatException ex) {
            log.debug("Skipping citation with non-numeric entity id {}", entityIdValue);
            return Optional.empty();
        }

        var type = "L".equals(typeValue) ? CitationType.LECTURE_UNIT : null;

        if (type == null) {
            log.debug("Skipping citation with unsupported type {}", typeValue);
            return Optional.empty();
        }

        return Optional.of(new IrisCitationReference(entityId, type));
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
            log.debug("Lecture unit citation {} not found", reference.entityId());
            return Optional.empty();
        }
    }

    private record IrisCitationReference(long entityId, CitationType type) {
    }

    private enum CitationType {
        LECTURE_UNIT,
    }
}
