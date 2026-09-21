package de.tum.cit.aet.artemis.course.service;

import java.time.ZonedDateTime;
import java.util.UUID;

import de.tum.cit.aet.artemis.course.domain.CourseOperationType;

/**
 * Identifies the owner of a running course operation.
 *
 * @param courseId      the ID of the course being operated on
 * @param operationType the type of operation
 * @param startedAt     when the operation started
 * @param ownerToken    the unique token that owns the distributed claim
 */
public record CourseOperationClaim(long courseId, CourseOperationType operationType, ZonedDateTime startedAt, UUID ownerToken) {
}
