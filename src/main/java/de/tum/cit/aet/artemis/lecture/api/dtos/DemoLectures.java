package de.tum.cit.aet.artemis.lecture.api.dtos;

import java.util.List;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * The lecture units that the {@code demo} profile seeded into the lectures of the demo course, by the topic of the course they belong to.
 * <p>
 * Each list holds the units of one lecture in the order they are seeded, exercise units included. Units that users added to a demo lecture are not part of it, so that later
 * seeding steps never link them to anything.
 *
 * @param architecture the seeded units of the lecture about software architecture.
 * @param algorithms   the seeded units of the lecture about algorithms and their complexity.
 * @param modeling     the seeded units of the lecture about object-oriented modeling.
 */
public record DemoLectures(List<LectureUnit> architecture, List<LectureUnit> algorithms, List<LectureUnit> modeling) {
}
