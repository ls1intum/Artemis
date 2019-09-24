package de.tum.in.www1.artemis.respository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

public interface ProgrammingExerciseTestRepository extends JpaRepository<ProgrammingExercise, Long> {

    @Query("select p from ProgrammingExercise p left join fetch p.studentParticipations left join fetch p.attachments left join fetch p.categories "
            + "left join fetch p.templateParticipation left join fetch p.solutionParticipation left join fetch p.exampleSubmissions left join fetch p.exerciseHints "
            + "left join fetch p.tutorParticipations left join fetch p.studentQuestions left join fetch p.testCases")
    ProgrammingExercise findOneWithEagerEverything(ProgrammingExercise exercise);
}
