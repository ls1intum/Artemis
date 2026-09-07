package de.tum.cit.aet.artemis.exam.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for creating a test run ({@code POST .../test-runs}).
 * <p>
 * {@code exerciseIds} must be ordered: the server loads and persists the exercises in exactly this order (the
 * {@code StudentExam.exercises} association is an {@code @OrderColumn} list, and the client builds this list by
 * iterating the exam's exercise groups in order, picking one exercise per group).
 * <p>
 * {@code @JsonInclude} only affects serialization, never deserialization, so it is inert for this request DTO on the
 * wire; the module DTO architecture test requires it on every DTO. Integration tests must send raw JSON when they
 * need to exercise an explicit empty {@code exerciseIds} array.
 *
 * @param examId      the id of the exam the test run belongs to
 * @param exerciseIds the ids of the exercises to include in the test run, in the order they should be persisted
 * @param workingTime the working time of the test run in seconds
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateTestRunDTO(long examId, List<Long> exerciseIds, Integer workingTime) {
}
