package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.MigrationChangelog;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

public interface MigrationChangeRepository extends ArtemisJpaRepository<MigrationChangelog, String> {
}
