package de.tum.cit.aet.artemis.programming.test_repository;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;

@Lazy
@Repository
@Primary
public interface ProgrammingExerciseBuildConfigTestRepository extends ProgrammingExerciseBuildConfigRepository {

    @Modifying
    @Transactional // required because modifying
    @Query("""
            UPDATE ProgrammingExerciseBuildConfig config
            SET config.allowBranching = true, config.branchRegex = :allowedBranchRegex
            WHERE config.programmingExercise.id = :programmingExerciseId
            """)
    void allowBranching(long programmingExerciseId, String allowedBranchRegex);

    @Modifying
    @Transactional // required because modifying
    @Query("""
            UPDATE ProgrammingExerciseBuildConfig config
            SET config.allowBranching = false
            WHERE config.programmingExercise.id = :programmingExerciseId
            """)
    void disallowBranching(long programmingExerciseId);
}
