package de.tum.cit.aet.artemis.lecture.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The versions of a lecture unit's material that Iris has ingested.
 * <p>
 * An Iris citation is pinned to these values when it is written, so that clicking it later can tell whether the cited page or timestamp still refers to the same content.
 * They are only reported once processing reached {@code DONE} — while a unit is being reprocessed there is nothing trustworthy to pin, because the vector database still
 * serves the previous version. A {@code null} value means that kind of material was never ingested.
 * <p>
 * Slides are versioned by {@code Attachment#version}; videos by the transcription version, because a cited timestamp comes from a transcription segment. The counterpart
 * at click time is {@link LectureUnitMaterialVersionsDTO}, which reports what exists right now.
 *
 * @param lectureUnitId     the id of the lecture unit
 * @param attachmentVersion the attachment version Iris has ingested
 * @param videoVersion      the transcription version Iris has ingested
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitIngestedVersionsDTO(long lectureUnitId, @Nullable Integer attachmentVersion, @Nullable Integer videoVersion) {
}
