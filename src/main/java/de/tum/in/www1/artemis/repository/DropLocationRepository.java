package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DropLocation;

@Repository
public interface DropLocationRepository extends JpaRepository<DropLocation, Long> {
}
