package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
// @formatter:off
public record PyrisLectureUnitDTO(
        long id,
        long lectureId,
        Instant releaseDate,
        String name,
        int attachmentVersion
) {}
// @formatter:on
