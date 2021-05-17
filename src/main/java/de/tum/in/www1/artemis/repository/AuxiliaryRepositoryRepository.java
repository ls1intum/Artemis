package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Set;

/**
 * Spring Data repository for the AuxiliaryRepository entity.
 */
@Repository
public interface AuxiliaryRepositoryRepository extends JpaRepository<AuxiliaryRepository, Long> {

    Set<AuxiliaryRepository> findByExerciseId(Long exerciseId);
}
