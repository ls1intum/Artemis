package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the {@link TestCaseFeedback} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface TestCaseFeedbackRepository extends ArtemisJpaRepository<TestCaseFeedback, Long> {

    @Query("""
            SELECT feedback
            FROM TestCaseFeedback feedback
                LEFT JOIN FETCH feedback.testCase
                LEFT JOIN FETCH feedback.message
            WHERE feedback.result.id = :resultId
            """)
    List<TestCaseFeedback> findWithTestCaseAndMessageByResultId(@Param("resultId") long resultId);

    @Query("""
            SELECT feedback
            FROM TestCaseFeedback feedback
                LEFT JOIN FETCH feedback.testCase
            WHERE feedback.result.id IN :resultIds
            """)
    List<TestCaseFeedback> findWithTestCaseByResultIds(@Param("resultIds") Collection<Long> resultIds);

    @Query("""
            SELECT feedback
            FROM TestCaseFeedback feedback
                LEFT JOIN FETCH feedback.testCase
                LEFT JOIN FETCH feedback.message
            WHERE feedback.result.id IN :resultIds
            """)
    List<TestCaseFeedback> findWithTestCaseAndMessageByResultIds(@Param("resultIds") Collection<Long> resultIds);

    @Query("""
            SELECT feedback
            FROM TestCaseFeedback feedback
                LEFT JOIN FETCH feedback.message
                LEFT JOIN FETCH feedback.testCase
                JOIN FETCH feedback.result r
                JOIN FETCH r.submission s
                JOIN FETCH s.participation
            WHERE feedback.id = :feedbackId
            """)
    Optional<TestCaseFeedback> findWithMessageAndParticipationById(@Param("feedbackId") long feedbackId);

    /**
     * Resolves test-case feedback rows to the results they belong to. The synthetic ids handed to the client encode the row, not the result, so a lookup is the only way back
     * to the result — which is what identifies the affected student.
     *
     * @param feedbackIds ids of the test-case feedback rows
     * @return the distinct ids of the results those rows belong to
     */
    @Query("""
            SELECT DISTINCT feedback.result.id
            FROM TestCaseFeedback feedback
            WHERE feedback.id IN :feedbackIds
            """)
    List<Long> findResultIdsByIds(@Param("feedbackIds") Collection<Long> feedbackIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TestCaseFeedback feedback
            WHERE feedback.result.id = :resultId
            """)
    void deleteByResultId(@Param("resultId") long resultId);
}
