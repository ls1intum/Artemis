package de.tum.cit.aet.artemis.repository;

import java.util.Set;

import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

public interface UserTestRepository extends ArtemisJpaRepository<User, Long> {

    Set<User> findAllByGroupsNotEmpty();
}
