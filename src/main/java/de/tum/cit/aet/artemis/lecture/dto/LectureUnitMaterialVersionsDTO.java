package de.tum.cit.aet.artemis.lecture.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The versions of the material a lecture unit currently offers.
 * <p>
 * An Iris citation is pinned to the version of the material it was generated from. The client fetches these values at the moment a citation is clicked and compares them
 * against the pinned ones, which is what tells it whether the cited page or timestamp still points at the same content. A {@code null} value means that kind of material
 * does not exist for this unit.
 *
 * @param attachmentVersion the version of the unit's PDF, or {@code null} if it has none
 * @param videoVersion      the version of the unit's transcription, or {@code null} if it has none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitMaterialVersionsDTO(@Nullable Integer attachmentVersion, @Nullable Integer videoVersion) {
}
