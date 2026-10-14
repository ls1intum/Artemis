package de.tum.cit.aet.artemis.programming.test_repository;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseWriteOwnershipRepository;

@Lazy
@Primary
@Repository
public interface ProgrammingExerciseWriteOwnershipTestRepository extends ProgrammingExerciseWriteOwnershipRepository {

    @Transactional
    default int claimAndHold(long exerciseId, String token, CountDownLatch claimed, CountDownLatch release) {
        int result = claim(exerciseId, token, "held-process", "persist", Instant.now());
        claimed.countDown();
        try {
            if (!release.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release the ownership transaction");
            }
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return result;
    }

    @Transactional
    default int claimReaderAndHold(long exerciseId, String token, CountDownLatch claimed, CountDownLatch release) {
        int result = claimReader(exerciseId, token, "held-reader-process", Instant.now());
        claimed.countDown();
        try {
            if (!release.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release the reader transaction");
            }
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return result;
    }

    @Transactional
    @Modifying
    @Query(value = "UPDATE exercise SET title = :title WHERE id = :exerciseId", nativeQuery = true)
    int updateCallbackTitle(@Param("exerciseId") long exerciseId, @Param("title") String title);
}
