package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.CourseTeamView;

@Repository
public interface CourseTeamViewRepository extends JpaRepository<CourseTeamView, Long> {

}
