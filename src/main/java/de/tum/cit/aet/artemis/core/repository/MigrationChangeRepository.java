package de.tum.cit.aet.artemis.core.repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.MigrationChangelog;

public interface MigrationChangeRepository extends ArtemisJpaRepository<MigrationChangelog, String> {
}
