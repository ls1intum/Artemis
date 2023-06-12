package de.tum.in.www1.artemis.repository.iris;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;

public interface IrisSettingsRepository extends JpaRepository<IrisSettings, Long> {

    @Query("""
            SELECT s
            FROM ProgrammingExercise p
            LEFT JOIN FETCH p.irisSettings s
            LEFT JOIN FETCH s.irisChatSettings ics
            LEFT JOIN FETCH s.irisHestiaSettings ihs
            WHERE p.id = :exerciseId
            """)
    Optional<IrisSettings> findByProgrammingExerciseId(long exerciseId);

    @Query("""
            SELECT s
            FROM Course c
            LEFT JOIN FETCH c.irisSettings s
            LEFT JOIN FETCH s.irisChatSettings ics
            LEFT JOIN FETCH s.irisHestiaSettings ihs
            WHERE c.id = :courseId
            """)
    Optional<IrisSettings> findByCourseId(long courseId);
}
