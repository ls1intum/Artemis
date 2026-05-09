package de.tum.cit.aet.artemis.exam.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.dto.ExamStudentSearchDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamUserAttendanceCheckDTO;

@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamUserRepository extends ArtemisJpaRepository<ExamUser, Long>, JpaSpecificationExecutor<ExamUser> {

    @Query("""
            SELECT eu
            FROM ExamUser eu
            WHERE eu.exam.id = :examId
               AND eu.user.id = :userId
            """)
    Optional<ExamUser> findByExamIdAndUserId(@Param("examId") long examId, @Param("userId") long userId);

    @EntityGraph(type = LOAD, attributePaths = { "exam" })
    Optional<ExamUser> findWithExamById(long examUserId);

    List<ExamUser> findAllByExamId(long examId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.exam.dto.ExamUserAttendanceCheckDTO(
                examUser.id,
                examUser.studentImagePath,
                examUser.user.login,
                examUser.user.registrationNumber,
                examUser.signingImagePath,
                studentExams.started,
                studentExams.submitted
            )
            FROM ExamUser examUser
                LEFT JOIN examUser.exam exam
                LEFT JOIN exam.studentExams studentExams ON studentExams.user.id = examUser.user.id
            WHERE exam.id = :examId
                AND studentExams.started = TRUE
                AND (examUser.signingImagePath IS NULL
                    OR examUser.signingImagePath = ''
                    OR examUser.didCheckImage = FALSE
                    OR examUser.didCheckLogin = FALSE
                    OR examUser.didCheckRegistrationNumber = FALSE
                    OR examUser.didCheckName = FALSE
                )
            """)
    Set<ExamUserAttendanceCheckDTO> findAllExamUsersWhoDidNotSign(@Param("examId") long examId);

    @Query("""
            SELECT COUNT(examUser) > 0
            FROM ExamUser examUser
            WHERE examUser.exam.id = :examId
                AND examUser.user.login = :login
                AND examUser.signingImagePath IS NOT NULL
                AND examUser.signingImagePath != ''
                AND examUser.didCheckImage = TRUE
                AND examUser.didCheckLogin = TRUE
                AND examUser.didCheckRegistrationNumber = TRUE
                AND examUser.didCheckName = TRUE
            """)
    boolean isAttendanceChecked(@Param("examId") long examId, @Param("login") String login);

    @Query("""
            SELECT COUNT (DISTINCT eu)
            FROM ExamUser eu
            WHERE eu.exam.id = :examId
            """)
    long countByExamId(@Param("examId") long examId);

    @EntityGraph(type = LOAD, attributePaths = { "exam", "exam.examUsers" })
    Optional<ExamUser> findWithExamWithExamUsersById(long examUserId);

    /**
     * Returns the IDs of users who are already registered as exam users for the given exam, filtered to the provided user IDs.
     * Used to mark users as already registered in the user-registration modal.
     *
     * @param examId  the exam to check registrations against
     * @param userIds the user IDs to check; must not be empty
     * @return the subset of {@code userIds} that are registered for the exam
     */
    @Query("""
            SELECT eu.user.id
            FROM ExamUser eu
            WHERE eu.exam.id = :examId
                AND eu.user.id IN :userIds
            """)
    Set<Long> findRegisteredUserIdsByExamIdAndUserIds(@Param("examId") long examId, @Param("userIds") List<Long> userIds);

    /**
     * Returns a page of {@link ExamUser} IDs for the given exam, filtered and searched according to {@link ExamStudentSearchDTO}.
     * Sorting is applied via {@code query.orderBy} inside the specification rather than through {@link Pageable#getSort()}
     * because the working-time sort requires a correlated subquery that {@link Sort} cannot express.
     *
     * @param examId the exam whose registered students are queried
     * @param search search term, page index, page size, sort column, sort direction, and an optional filter value
     * @return a page of {@link ExamUser} IDs in the requested order
     */
    default Page<Long> findExamUserIdsForExam(long examId, ExamStudentSearchDTO search) {
        SortingOrder sortOrder = search.sortingOrder() != null ? search.sortingOrder() : SortingOrder.ASCENDING;
        Specification<ExamUser> spec = Specification.where(ExamUserSpecs.forExam(examId)).and(ExamUserSpecs.searchByUserFields(search.searchTerm()))
                .and(ExamUserSpecs.filteredBy(search.filterProp())).and(ExamUserSpecs.ordered(search.sortedColumn(), sortOrder));

        Pageable unsortedPageable = PageRequest.of(search.page(), search.pageSize(), Sort.unsorted());
        return findAll(spec, unsortedPageable).map(ExamUser::getId);
    }

    /**
     * Loads {@link ExamUser} entities for the given IDs with their associated {@link de.tum.cit.aet.artemis.core.domain.User} eagerly fetched.
     *
     * @param ids the {@link ExamUser} IDs to load
     * @return the matching exam users in unspecified order
     */
    @Query("""
            SELECT eu
            FROM ExamUser eu
                LEFT JOIN FETCH eu.user
            WHERE eu.id IN :ids
            """)
    List<ExamUser> findByIdsWithUser(@Param("ids") List<Long> ids);
}
