package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import jakarta.transaction.Transactional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.CourseRequest;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseRequestRepository extends ArtemisJpaRepository<CourseRequest, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "requester" })
    List<CourseRequest> findAllByOrderByCreatedDateDesc();

    @EntityGraph(type = LOAD, attributePaths = { "requester" })
    Optional<CourseRequest> findOneWithEagerRelationshipsById(long id);

    Optional<CourseRequest> findOneByShortNameIgnoreCase(String shortName);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByCreatedCourseId(long courseId);
}
