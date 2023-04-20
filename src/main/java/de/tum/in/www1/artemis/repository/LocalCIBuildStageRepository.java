package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.localci.LocalCIBuildStage;

public interface LocalCIBuildStageRepository extends JpaRepository<LocalCIBuildStage, Long> {

    @Query("""
            SELECT buildStage
            FROM LocalCIBuildStage buildStage
            WHERE buildStage.name = :name
            """)
    Optional<LocalCIBuildStage> findByName(@Param("name") String name);
}
