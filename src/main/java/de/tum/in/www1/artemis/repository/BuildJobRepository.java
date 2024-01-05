package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.BuildJob;

public interface BuildJobRepository extends JpaRepository<BuildJob, Long> {

    List<BuildJob> findAllByCourseId(Long courseId);

    List<BuildJob> findAllByExerciseId(Long exerciseId);

    List<BuildJob> findAllByExceptionOccurred(boolean exceptionOccurred);

}
