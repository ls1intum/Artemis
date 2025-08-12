package de.tum.cit.aet.artemis.atlas.test_repository;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.repository.LearningPathRepository;

@Lazy
@Repository
@Primary
public interface LearningPathTestRepository extends LearningPathRepository {
}
