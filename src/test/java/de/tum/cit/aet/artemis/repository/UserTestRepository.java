package de.tum.cit.aet.artemis.repository;

import java.util.Set;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTestRepository extends ArtemisJpaRepository<User, Long> {

    Set<User> findAllByGroupsNotEmpty();
}
