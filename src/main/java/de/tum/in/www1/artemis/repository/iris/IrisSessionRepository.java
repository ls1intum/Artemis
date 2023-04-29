package de.tum.in.www1.artemis.repository.iris;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.iris.IrisSession;

public interface IrisSessionRepository extends JpaRepository<IrisSession, Long> {

    IrisSession findByExerciseId(Long exerciseId);
}
