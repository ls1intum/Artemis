package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

@Repository
public interface ProgrammingExerciseTestRepository extends JpaRepository<ProgrammingExercise, Long> {

    @Query("""
            SELECT p FROM ProgrammingExercise p
                LEFT JOIN FETCH p.studentParticipations
                LEFT JOIN FETCH p.categories
                LEFT JOIN FETCH p.templateParticipation
                LEFT JOIN FETCH p.solutionParticipation
                LEFT JOIN FETCH p.exampleSubmissions
                LEFT JOIN FETCH p.exerciseHints eh
                LEFT JOIN FETCH p.tutorParticipations
                LEFT JOIN FETCH p.posts
                LEFT JOIN FETCH p.testCases tc
                    LEFT JOIN FETCH tc.solutionEntries
                LEFT JOIN FETCH p.staticCodeAnalysisCategories
                LEFT JOIN FETCH p.auxiliaryRepositories
                LEFT JOIN FETCH p.tasks t
                    LEFT JOIN FETCH t.testCases
                    LEFT JOIN FETCH t.exerciseHints
            WHERE p.id = :exerciseId
            """)
    ProgrammingExercise findOneWithEagerEverything(@Param("exerciseId") Long exerciseId);
}
