package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A lecture as the course overview sidebar needs it: enough to title, date, group and link to it, and nothing more.
 * <p>
 * The sidebar previously received whole {@code Lecture} entities, which dragged their eagerly mapped attachments along.
 * Those were fetched, filtered for visibility and serialised on every course visit even though the sidebar renders none
 * of them — they only matter once a specific lecture is opened, which loads its own detail payload.
 *
 * @param id                the id of the lecture
 * @param title             the title shown on the sidebar card
 * @param startDate         when the lecture starts; also drives the date grouping and the "upcoming lecture" redirect
 * @param endDate           when the lecture ends; used for the date grouping
 * @param isTutorialLecture whether this is a tutorial lecture, which the sidebar links to a different sub-route
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureForOverviewDTO(long id, String title, ZonedDateTime startDate, ZonedDateTime endDate, boolean isTutorialLecture) {
}
