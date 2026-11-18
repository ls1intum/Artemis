package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.repository.UserCreationLockRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.CourseRequest;
import de.tum.cit.aet.artemis.course.domain.CourseRequestStatus;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseRequestRepository extends ArtemisJpaRepository<CourseRequest, Long>, UserCreationLockRepository {

    @EntityGraph(type = LOAD, attributePaths = { "requester" })
    Optional<CourseRequest> findOneWithEagerRelationshipsById(long id);

    Optional<CourseRequest> findOneByShortNameIgnoreCase(String shortName);

    long countByRequesterIdAndStatus(long requesterId, CourseRequestStatus status);

    /**
     * Inserts a request only while the requester has room for another pending request. The parent-row lock also
     * serializes creation when the requester has no pending requests yet.
     *
     * @param courseRequest          the new pending request
     * @param maximumPendingRequests the pending request limit
     * @return the persisted request
     */
    @Transactional
    default CourseRequest saveWithPendingLimit(CourseRequest courseRequest, int maximumPendingRequests) {
        long requesterId = courseRequest.getRequester().getId();
        lockUserForCreation(requesterId);
        if (countByRequesterIdAndStatus(requesterId, CourseRequestStatus.PENDING) >= maximumPendingRequests) {
            throw new BadRequestAlertException("There are too many course requests waiting for a decision, please wait for a decision before you send another one",
                    CourseRequest.ENTITY_NAME, "courseRequestTooManyPending");
        }
        return save(courseRequest);
    }

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByCreatedCourseId(long courseId);

    /**
     * Finds all course requests with the given status, ordered by creation date descending.
     * The requester is eagerly loaded.
     *
     * @param status the status to filter by
     * @return list of course requests with the given status
     */
    @EntityGraph(type = LOAD, attributePaths = { "requester" })
    List<CourseRequest> findAllByStatusOrderByCreatedDateDesc(CourseRequestStatus status);

    /**
     * Finds all decided (non-pending) course requests with pagination, ordered by processed date descending.
     *
     * @param status   the status to exclude (PENDING)
     * @param pageable pagination information
     * @return page of decided course requests with requester eagerly loaded
     */
    @Query("""
            SELECT cr FROM CourseRequest cr
            LEFT JOIN FETCH cr.requester
            WHERE cr.status <> :status
            ORDER BY cr.processedDate DESC
            """)
    Page<CourseRequest> findAllByStatusNotOrderByProcessedDateDesc(@Param("status") CourseRequestStatus status, Pageable pageable);
}
