package de.tum.cit.aet.artemis.core.repository;

import de.tum.cit.aet.artemis.core.domain.MigrationChangelog;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

public interface MigrationChangeRepository extends ArtemisJpaRepository<MigrationChangelog, String> {
}
