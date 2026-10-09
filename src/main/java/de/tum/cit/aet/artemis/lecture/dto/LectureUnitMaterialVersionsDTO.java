package de.tum.cit.aet.artemis.lecture.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The versions of the material a lecture unit currently offers.
 * <p>
 * An Iris citation is pinned to the version of the material it was generated from. The client fetches these values at the moment a citation is clicked and compares them
 * against the pinned ones, which is what tells it whether the cited page or timestamp still points at the same content. A {@code null} value means that kind of material
 * does not exist for this unit.
 * <p>
 * {@code hasVideo} exists because a missing {@code videoVersion} has two very different causes: the video is gone, or the video is still there while a completed
 * transcription of it is not — the row is deleted and rewritten whenever the video changes, and a run in progress leaves it pending. Only the first is material that no
 * longer exists; the second is a video the client cannot verify the citation against, and telling a student that a video which visibly plays no longer exists would be
 * wrong.
 *
 * @param attachmentVersion the version of the unit's PDF, or {@code null} if it has none
 * @param videoVersion      the version of the unit's completed transcription, or {@code null} if there is none
 * @param hasVideo          whether the unit still offers a video, regardless of whether a transcription of it exists
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitMaterialVersionsDTO(@Nullable Integer attachmentVersion, @Nullable Integer videoVersion, boolean hasVideo) {
}
