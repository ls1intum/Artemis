package de.tum.cit.aet.artemis.repository;

import java.util.Set;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.User;

public interface UserTestRepository extends ArtemisJpaRepository<User, Long> {

    Set<User> findAllByGroupsNotEmpty();
}
