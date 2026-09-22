package de.tum.cit.aet.artemis.hyperion.test_repository;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.hyperion.repository.AuthoringRunRepository;

@Lazy
@Repository
@Primary
public interface AuthoringRunTestRepository extends AuthoringRunRepository {
}
