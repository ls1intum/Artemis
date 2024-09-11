package de.tum.cit.aet.artemis.repository;

import de.tum.cit.aet.artemis.domain.MigrationChangelog;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

public interface MigrationChangeRepository extends ArtemisJpaRepository<MigrationChangelog, String> {
}
