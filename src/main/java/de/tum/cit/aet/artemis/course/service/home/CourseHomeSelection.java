package de.tum.cit.aet.artemis.course.service.home;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;

public record CourseHomeSelection(CourseHomeWelcomeCase welcomeCase, CourseHomeDTO.@Nullable Item nextStep) {
}
