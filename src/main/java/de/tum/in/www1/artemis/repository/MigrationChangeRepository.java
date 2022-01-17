package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.MigrationChangelog;

public interface MigrationChangeRepository extends JpaRepository<MigrationChangelog, String> {
}
