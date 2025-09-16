package de.tum.cit.aet.artemis.tutorialgroup.util;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;

public record TutorialGroupDetailSessionData(ZonedDateTime start, ZonedDateTime end, String location, TutorialGroupSessionStatus status, Integer attendanceCount) {
}
