package de.tum.in.www1.artemis.web.rest.dto.response;

import java.time.ZonedDateTime;

public class ExamResponseDTO {

    public Long id;

    public String title;

    public ZonedDateTime visibleDate;

    public ZonedDateTime startDate;

    public ZonedDateTime endDate;

    public Long durationInMinutes;
}
