package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureDTO(
        long id,
        String title,
        String description,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        List<PyrisLectureUnitDTO> units
) {}
// @formatter:on
