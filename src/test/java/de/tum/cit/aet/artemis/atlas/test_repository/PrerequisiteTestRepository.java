package de.tum.cit.aet.artemis.atlas.test_repository;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;

/**
 * Spring Data JPA repository for the {@link Prerequisite} entity.
 */
@Repository
@Primary
public interface PrerequisiteTestRepository extends PrerequisiteRepository {

    List<Prerequisite> findAllByCourseIdOrderById(long courseId);
}
