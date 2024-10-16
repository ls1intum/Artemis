package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Pyris DTO mapping for a {@code LectureUnit}.
 */
// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureUnitDTO(
        long id,
        long lectureId,
        Instant releaseDate,
        String name,
        int attachmentVersion
) {}
// @formatter:on
