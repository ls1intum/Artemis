package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.localci.LocalCIBuildStage;

public interface LocalCIBuildTaskRepository {

    @Query("""
            SELECT buildTask
            FROM LocalCIBuildStage buildTask
            WHERE buildTask.name = :name
            """)
    Optional<LocalCIBuildStage> findByName(@Param("name") String name);
}
