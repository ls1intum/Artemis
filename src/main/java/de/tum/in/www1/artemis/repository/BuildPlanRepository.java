package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.BuildPlan;

public interface BuildPlanRepository extends JpaRepository<BuildPlan, Long> {

    Optional<BuildPlan> findByBuildPlan(String buildPlan);
}
