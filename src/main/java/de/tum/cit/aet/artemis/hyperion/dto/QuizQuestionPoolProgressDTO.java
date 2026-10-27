package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing how far a course's quiz question pool build has come.
 *
 * @param running    whether a build of the course is running anywhere in the cluster
 * @param total      intended questions in the pool
 * @param toGenerate items still to generate, including those claimed for generation
 * @param toJudge    generated items awaiting the filter, including those claimed for filtering
 * @param judged     items generated and judged
 * @param accepted   judged items the filter accepted
 * @param rejected   judged items the filter rejected
 * @param failed     items whose generation or filtering failed permanently
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Progress of a course's quiz question pool build")
public record QuizQuestionPoolProgressDTO(@Schema(description = "Whether a build of the course is running anywhere in the cluster") boolean running,
        @Schema(description = "Intended questions in the pool") int total, @Schema(description = "Items still to generate") int toGenerate,
        @Schema(description = "Generated items awaiting the filter") int toJudge, @Schema(description = "Items generated and judged") int judged,
        @Schema(description = "Judged items the filter accepted") int accepted, @Schema(description = "Judged items the filter rejected") int rejected,
        @Schema(description = "Items whose generation or filtering failed permanently") int failed) {
}
