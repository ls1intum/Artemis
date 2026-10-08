package de.tum.cit.aet.artemis.lecture.domain.event;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Domain event published when a lecture unit's content-bearing fields are created or materially changed.
 * <p>
 * Mirrors {@link de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent} for exercises:
 * it decouples the lecture-unit REST resources from the consumers that react to content changes — currently
 * only the Atlas auto-orchestration recorder ({@code AutonomousCompetencyLectureUnitEventListener}). The
 * publishing resource is responsible for firing this event only when a content-bearing field actually
 * changed (on create: the new content is non-blank; on update: it differs from the pre-update value), so the
 * per-course daily orchestration cap is not spent on purely administrative edits.
 *
 * @param lectureUnit the lecture unit whose content changed (never an {@code ExerciseUnit} — those are
 *                        orchestrated through their exercise instead)
 */
public record LectureUnitContentChangedEvent(LectureUnit lectureUnit) {
}
