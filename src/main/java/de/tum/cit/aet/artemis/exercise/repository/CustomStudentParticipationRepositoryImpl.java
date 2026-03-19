package de.tum.cit.aet.artemis.exercise.repository;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Result_;
import de.tum.cit.aet.artemis.core.domain.DomainObject_;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.User_;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Submission_;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.Team_;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation_;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation_;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission_;

/**
 * Criteria-API-based implementation for paginated, filtered participation queries.
 * <p>
 * Uses EXISTS subqueries (no expensive LEFT JOINs) for result-based filter predicates and
 * scalar subqueries for sorting by result fields. The only real JOINs used are 1:1
 * relationships (student, team) that are needed for search and sort-by-name.
 */
public class CustomStudentParticipationRepositoryImpl implements CustomStudentParticipationRepository {

    private final EntityManager entityManager;

    public CustomStudentParticipationRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<Long> findParticipationIdsByExerciseIdWithFilters(long exerciseId, boolean teamMode, String searchTerm, String filterProp, Integer scoreRangeLower,
            Integer scoreRangeUpper, Pageable pageable, SortingOrder sortOrder, String sortedColumn) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // --- ID query (paginated) ---
        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<StudentParticipation> root = idQuery.from(StudentParticipation.class);
        idQuery.select(root.get(DomainObject_.ID));

        // 1:1 joins for search / sort (only the relevant one based on mode)
        Join<StudentParticipation, User> studentJoin = null;
        Join<StudentParticipation, Team> teamJoin = null;
        if (teamMode) {
            teamJoin = root.join(StudentParticipation_.TEAM, JoinType.LEFT);
        }
        else {
            studentJoin = root.join(StudentParticipation_.STUDENT, JoinType.LEFT);
        }

        List<Predicate> predicates = buildPredicates(cb, idQuery, root, studentJoin, teamJoin, exerciseId, teamMode, searchTerm, filterProp, scoreRangeLower, scoreRangeUpper);
        idQuery.where(cb.and(predicates.toArray(new Predicate[0])));

        // Sorting
        Expression<?> sortExpr = buildSortExpression(cb, idQuery, root, studentJoin, teamJoin, teamMode, sortedColumn);
        Order primaryOrder = (sortOrder == SortingOrder.DESCENDING) ? cb.desc(sortExpr) : cb.asc(sortExpr);
        Order tieBreaker = cb.asc(root.get(DomainObject_.ID));
        idQuery.orderBy(primaryOrder, tieBreaker);

        TypedQuery<Long> typedQuery = entityManager.createQuery(idQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<Long> ids = typedQuery.getResultList();

        // --- Count query (same predicates, no sorting) ---
        long total = executeCountQuery(cb, exerciseId, teamMode, searchTerm, filterProp, scoreRangeLower, scoreRangeUpper);

        return new PageImpl<>(ids, pageable, total);
    }

    // --------------------------------------------------
    // Predicate building
    // --------------------------------------------------

    private List<Predicate> buildPredicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<StudentParticipation> root, Join<StudentParticipation, User> studentJoin,
            Join<StudentParticipation, Team> teamJoin, long exerciseId, boolean teamMode, String searchTerm, String filterProp, Integer scoreRangeLower, Integer scoreRangeUpper) {

        List<Predicate> predicates = new ArrayList<>();

        // Exercise filter
        predicates.add(cb.equal(root.get(Participation_.EXERCISE).get(DomainObject_.ID), exerciseId));

        // Participant must not be null
        if (teamMode) {
            predicates.add(cb.isNotNull(root.get(StudentParticipation_.TEAM)));
        }
        else {
            predicates.add(cb.isNotNull(root.get(StudentParticipation_.STUDENT)));
        }

        // Search term
        if (searchTerm != null && !searchTerm.isBlank()) {
            String pattern = "%" + searchTerm.toLowerCase() + "%";
            if (teamMode) {
                predicates.add(cb.or(cb.like(cb.lower(teamJoin.get(Team_.NAME)), pattern), cb.like(cb.lower(teamJoin.get(Team_.SHORT_NAME)), pattern)));
            }
            else {
                predicates.add(cb.or(cb.like(cb.lower(studentJoin.get(User_.LOGIN)), pattern), cb.like(cb.lower(studentJoin.get(User_.FIRST_NAME)), pattern),
                        cb.like(cb.lower(studentJoin.get(User_.LAST_NAME)), pattern)));
            }
        }

        // FilterProp predicate (via EXISTS subqueries on result/submission)
        addFilterPredicate(cb, query, root, predicates, filterProp);

        // Score range predicate
        addScoreRangePredicate(cb, query, root, predicates, scoreRangeLower, scoreRangeUpper);

        return predicates;
    }

    /**
     * Adds a filter predicate using an EXISTS subquery that navigates
     * Submission -> Result for the latest result of the latest submission.
     */
    private void addFilterPredicate(CriteriaBuilder cb, CriteriaQuery<?> query, Root<StudentParticipation> root, List<Predicate> predicates, String filterProp) {
        if (filterProp == null || "All".equals(filterProp)) {
            return;
        }

        switch (filterProp) {
            case "Successful" -> predicates.add(latestResultExists(cb, query, root, r -> cb.equal(r.get(Result_.SUCCESSFUL), true)));
            case "Unsuccessful" -> {
                // Participation has no result, or latest result is not successful
                Predicate hasSuccessfulResult = latestResultExists(cb, query, root, r -> cb.equal(r.get(Result_.SUCCESSFUL), true));
                predicates.add(cb.not(hasSuccessfulResult));
            }
            case "BuildFailed" -> predicates.add(buildFailedExists(cb, query, root));
            case "Manual" -> predicates.add(latestResultExists(cb, query, root,
                    r -> cb.or(cb.equal(r.get(Result_.ASSESSMENT_TYPE), AssessmentType.MANUAL), cb.equal(r.get(Result_.ASSESSMENT_TYPE), AssessmentType.SEMI_AUTOMATIC))));
            case "Automatic" -> predicates.add(latestResultExists(cb, query, root, r -> cb.equal(r.get(Result_.ASSESSMENT_TYPE), AssessmentType.AUTOMATIC)));
            case "Locked" -> predicates.add(latestResultExists(cb, query, root, r -> cb.isNull(r.get(Result_.COMPLETION_DATE))));
            default -> {
                // Unknown filter — ignore
            }
        }
    }

    /**
     * Adds a score-range filter using an EXISTS subquery.
     */
    private void addScoreRangePredicate(CriteriaBuilder cb, CriteriaQuery<?> query, Root<StudentParticipation> root, List<Predicate> predicates, Integer scoreRangeLower,
            Integer scoreRangeUpper) {
        if (scoreRangeLower == null || scoreRangeUpper == null) {
            return;
        }

        predicates.add(latestResultExists(cb, query, root, r -> {
            Predicate lower = cb.ge(r.get(Result_.SCORE), scoreRangeLower);
            Predicate upper = (scoreRangeUpper == 100) ? cb.le(r.get(Result_.SCORE), scoreRangeUpper) : cb.lt(r.get(Result_.SCORE), scoreRangeUpper);
            return cb.and(lower, upper);
        }));
    }

    // --------------------------------------------------
    // EXISTS subquery helpers
    // --------------------------------------------------

    /**
     * Creates an EXISTS subquery that checks if the latest result of the latest submission
     * satisfies an additional condition.
     * <p>
     * The subquery structure:
     * EXISTS (SELECT 1 FROM Submission s JOIN s.results r
     * WHERE s.participation = p
     * AND s.id = (SELECT MAX ...)
     * AND r.id = (SELECT MAX ... excluding ATHENA)
     * AND [additionalCondition])
     */
    @FunctionalInterface
    private interface ResultCondition {

        Predicate apply(From<?, Result> resultPath);
    }

    private Predicate latestResultExists(CriteriaBuilder cb, CriteriaQuery<?> parentQuery, Root<StudentParticipation> participationRoot, ResultCondition condition) {

        Subquery<Long> existsSub = parentQuery.subquery(Long.class);
        Root<Submission> subSRoot = existsSub.from(Submission.class);
        Join<Submission, Result> subRJoin = subSRoot.join(Submission_.RESULTS, JoinType.INNER);
        existsSub.select(cb.literal(1L));

        // s.participation = p
        Predicate participationMatch = cb.equal(subSRoot.get(Submission_.PARTICIPATION), participationRoot);

        // s.id = MAX submission id
        Subquery<Long> maxSubId = existsSub.subquery(Long.class);
        Root<Submission> msRoot = maxSubId.from(Submission.class);
        maxSubId.select(cb.max(msRoot.get(DomainObject_.ID)));
        maxSubId.where(cb.equal(msRoot.get(Submission_.PARTICIPATION), participationRoot));
        Predicate latestSubmission = cb.equal(subSRoot.get(DomainObject_.ID), maxSubId);

        // r.id = MAX result id (excluding AUTOMATIC_ATHENA)
        Subquery<Long> maxResId = existsSub.subquery(Long.class);
        Root<Result> mrRoot = maxResId.from(Result.class);
        maxResId.select(cb.max(mrRoot.get(DomainObject_.ID)));
        maxResId.where(cb.equal(mrRoot.get(Result_.SUBMISSION), subSRoot), cb.notEqual(mrRoot.get(Result_.ASSESSMENT_TYPE), AssessmentType.AUTOMATIC_ATHENA));
        Predicate latestResult = cb.equal(subRJoin.get(DomainObject_.ID), maxResId);

        // Apply the caller's additional condition on the result join
        Predicate additionalCondition = condition.apply(subRJoin);

        existsSub.where(participationMatch, latestSubmission, latestResult, additionalCondition);

        return cb.exists(existsSub);
    }

    /**
     * EXISTS subquery for BUILD_FAILED filter. Checks if the latest submission is a
     * ProgrammingSubmission with buildFailed = true.
     */
    private Predicate buildFailedExists(CriteriaBuilder cb, CriteriaQuery<?> parentQuery, Root<StudentParticipation> participationRoot) {

        Subquery<Long> existsSub = parentQuery.subquery(Long.class);
        Root<ProgrammingSubmission> subRoot = existsSub.from(ProgrammingSubmission.class);
        existsSub.select(cb.literal(1L));

        // s.participation = p
        Predicate participationMatch = cb.equal(subRoot.get(Submission_.PARTICIPATION), participationRoot);

        // s.id = MAX submission id
        Subquery<Long> maxSubId = existsSub.subquery(Long.class);
        Root<Submission> msRoot = maxSubId.from(Submission.class);
        maxSubId.select(cb.max(msRoot.get(DomainObject_.ID)));
        maxSubId.where(cb.equal(msRoot.get(Submission_.PARTICIPATION), participationRoot));
        Predicate latestSubmission = cb.equal(subRoot.get(DomainObject_.ID), maxSubId);

        // buildFailed = true
        Predicate buildFailed = cb.equal(subRoot.get(ProgrammingSubmission_.BUILD_FAILED), true);

        existsSub.where(participationMatch, latestSubmission, buildFailed);

        return cb.exists(existsSub);
    }

    /**
     * EXISTS subquery for the FAILED (stuck build) filter.
     * Matches participations whose latest submission has no result yet and was submitted before {@code cutoff},
     * meaning the build has been waiting longer than the exercise's configured timeout.
     */
    private Predicate stuckBuildExists(CriteriaBuilder cb, CriteriaQuery<?> parentQuery, Root<StudentParticipation> participationRoot, ZonedDateTime cutoff) {

        Subquery<Long> existsSub = parentQuery.subquery(Long.class);
        Root<Submission> subRoot = existsSub.from(Submission.class);
        existsSub.select(cb.literal(1L));

        // s.participation = p
        Predicate participationMatch = cb.equal(subRoot.get(Submission_.PARTICIPATION), participationRoot);

        // s.id = MAX submission id
        Subquery<Long> maxSubId = existsSub.subquery(Long.class);
        Root<Submission> msRoot = maxSubId.from(Submission.class);
        maxSubId.select(cb.max(msRoot.get(DomainObject_.ID)));
        maxSubId.where(cb.equal(msRoot.get(Submission_.PARTICIPATION), participationRoot));
        Predicate latestSubmission = cb.equal(subRoot.get(DomainObject_.ID), maxSubId);

        // no result exists for this submission
        Subquery<Long> resultSub = existsSub.subquery(Long.class);
        Root<Result> resultRoot = resultSub.from(Result.class);
        resultSub.select(cb.literal(1L));
        resultSub.where(cb.equal(resultRoot.get(Result_.SUBMISSION), subRoot));
        Predicate noResult = cb.not(cb.exists(resultSub));

        // submission was submitted before the cutoff (older than the build timeout)
        Predicate beforeCutoff = cb.lessThan(subRoot.get("submissionDate"), cutoff);

        existsSub.where(participationMatch, latestSubmission, noResult, beforeCutoff);

        return cb.exists(existsSub);
    }

    // --------------------------------------------------
    // Sorting
    // --------------------------------------------------

    private Expression<?> buildSortExpression(CriteriaBuilder cb, CriteriaQuery<Long> query, Root<StudentParticipation> root, Join<StudentParticipation, User> studentJoin,
            Join<StudentParticipation, Team> teamJoin, boolean teamMode, String sortedColumn) {

        final String column = sortedColumn != null ? sortedColumn : "";

        return switch (column) {
            case "participantName" -> {
                if (teamMode) {
                    yield teamJoin.get(Team_.NAME);
                }
                else {
                    // Sort by lastName, then firstName
                    yield cb.coalesce(studentJoin.get(User_.LAST_NAME), cb.literal(""));
                }
            }
            case "participantIdentifier" -> {
                if (teamMode) {
                    yield teamJoin.get(Team_.SHORT_NAME);
                }
                else {
                    yield studentJoin.get(User_.LOGIN);
                }
            }
            case "score" -> latestResultScalarSubquery(cb, query, root, Result_.SCORE, Double.class);
            case "completionDate" -> latestResultScalarSubquery(cb, query, root, Result_.COMPLETION_DATE, java.time.ZonedDateTime.class);
            default -> root.get(DomainObject_.ID);
        };
    }

    /**
     * Creates a scalar subquery that returns a single field from the latest result
     * of the latest submission. Used for ORDER BY on result fields without joining.
     */
    private <T> Expression<T> latestResultScalarSubquery(CriteriaBuilder cb, CriteriaQuery<Long> parentQuery, Root<StudentParticipation> participationRoot, String resultField,
            Class<T> type) {

        Subquery<T> sub = parentQuery.subquery(type);
        Root<Submission> sRoot = sub.from(Submission.class);
        Join<Submission, Result> rJoin = sRoot.join(Submission_.RESULTS, JoinType.INNER);
        sub.select(rJoin.get(resultField));

        // s.participation = p
        Predicate participationMatch = cb.equal(sRoot.get(Submission_.PARTICIPATION), participationRoot);

        // s.id = MAX submission id
        Subquery<Long> maxSubId = sub.subquery(Long.class);
        Root<Submission> msRoot = maxSubId.from(Submission.class);
        maxSubId.select(cb.max(msRoot.get(DomainObject_.ID)));
        maxSubId.where(cb.equal(msRoot.get(Submission_.PARTICIPATION), participationRoot));
        Predicate latestSubmission = cb.equal(sRoot.get(DomainObject_.ID), maxSubId);

        // r.id = MAX result id (excluding ATHENA)
        Subquery<Long> maxResId = sub.subquery(Long.class);
        Root<Result> mrRoot = maxResId.from(Result.class);
        maxResId.select(cb.max(mrRoot.get(DomainObject_.ID)));
        maxResId.where(cb.equal(mrRoot.get(Result_.SUBMISSION), sRoot), cb.notEqual(mrRoot.get(Result_.ASSESSMENT_TYPE), AssessmentType.AUTOMATIC_ATHENA));
        Predicate latestResult = cb.equal(rJoin.get(DomainObject_.ID), maxResId);

        sub.where(participationMatch, latestSubmission, latestResult);
        return sub;
    }

    // --------------------------------------------------
    // Management view (participation list)
    // --------------------------------------------------

    @Override
    public Page<Long> findParticipationIdsByExerciseIdForManagement(long exerciseId, boolean teamMode, String searchTerm, String filterProp, ZonedDateTime stuckBuildCutoff,
            Pageable pageable, SortingOrder sortOrder, String sortedColumn) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<StudentParticipation> root = idQuery.from(StudentParticipation.class);
        idQuery.select(root.get(DomainObject_.ID));

        Join<StudentParticipation, User> studentJoin = null;
        Join<StudentParticipation, Team> teamJoin = null;
        if (teamMode) {
            teamJoin = root.join(StudentParticipation_.TEAM, JoinType.LEFT);
        }
        else {
            studentJoin = root.join(StudentParticipation_.STUDENT, JoinType.LEFT);
        }

        List<Predicate> predicates = buildManagementPredicates(cb, idQuery, root, studentJoin, teamJoin, exerciseId, teamMode, searchTerm, filterProp, stuckBuildCutoff);
        idQuery.where(cb.and(predicates.toArray(new Predicate[0])));

        Expression<?> sortExpr = buildManagementSortExpression(cb, root, studentJoin, teamJoin, teamMode, sortedColumn);
        Order primaryOrder = (sortOrder == SortingOrder.DESCENDING) ? cb.desc(sortExpr) : cb.asc(sortExpr);
        Order tieBreaker = cb.asc(root.get(DomainObject_.ID));
        idQuery.orderBy(primaryOrder, tieBreaker);

        TypedQuery<Long> typedQuery = entityManager.createQuery(idQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<Long> ids = typedQuery.getResultList();

        long total = executeManagementCountQuery(cb, exerciseId, teamMode, searchTerm, filterProp, stuckBuildCutoff);

        return new PageImpl<>(ids, pageable, total);
    }

    private List<Predicate> buildManagementPredicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<StudentParticipation> root, Join<StudentParticipation, User> studentJoin,
            Join<StudentParticipation, Team> teamJoin, long exerciseId, boolean teamMode, String searchTerm, String filterProp, ZonedDateTime stuckBuildCutoff) {

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.get(Participation_.EXERCISE).get(DomainObject_.ID), exerciseId));

        if (teamMode) {
            predicates.add(cb.isNotNull(root.get(StudentParticipation_.TEAM)));
        }
        else {
            predicates.add(cb.isNotNull(root.get(StudentParticipation_.STUDENT)));
        }

        if (searchTerm != null && !searchTerm.isBlank()) {
            String pattern = "%" + searchTerm.toLowerCase() + "%";
            if (teamMode) {
                predicates.add(cb.or(cb.like(cb.lower(teamJoin.get(Team_.NAME)), pattern), cb.like(cb.lower(teamJoin.get(Team_.SHORT_NAME)), pattern)));
            }
            else {
                predicates.add(cb.or(cb.like(cb.lower(studentJoin.get(User_.LOGIN)), pattern), cb.like(cb.lower(studentJoin.get(User_.FIRST_NAME)), pattern),
                        cb.like(cb.lower(studentJoin.get(User_.LAST_NAME)), pattern)));
            }
        }

        addManagementFilterPredicate(cb, query, root, predicates, filterProp, stuckBuildCutoff);

        return predicates;
    }

    private void addManagementFilterPredicate(CriteriaBuilder cb, CriteriaQuery<?> query, Root<StudentParticipation> root, List<Predicate> predicates, String filterProp,
            ZonedDateTime stuckBuildCutoff) {
        if (filterProp == null || "All".equals(filterProp)) {
            return;
        }
        switch (filterProp) {
            case "Failed" -> {
                if (stuckBuildCutoff != null) {
                    predicates.add(stuckBuildExists(cb, query, root, stuckBuildCutoff));
                }
            }
            case "NoSubmissions" -> {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Submission> subRoot = sub.from(Submission.class);
                sub.select(cb.literal(1L));
                sub.where(cb.equal(subRoot.get(Submission_.PARTICIPATION), root));
                predicates.add(cb.not(cb.exists(sub)));
            }
            case "NoPracticeMode" -> predicates.add(cb.or(cb.isNull(root.get("testRun")), cb.equal(root.get("testRun"), false)));
            default -> {
                // Unknown filter — ignore
            }
        }
    }

    private Expression<?> buildManagementSortExpression(CriteriaBuilder cb, Root<StudentParticipation> root, Join<StudentParticipation, User> studentJoin,
            Join<StudentParticipation, Team> teamJoin, boolean teamMode, String sortedColumn) {

        final String column = sortedColumn != null ? sortedColumn : "";

        return switch (column) {
            case "participantName" -> {
                if (teamMode) {
                    yield teamJoin.get(Team_.NAME);
                }
                else {
                    yield cb.coalesce(studentJoin.get(User_.LAST_NAME), cb.literal(""));
                }
            }
            case "participantIdentifier" -> {
                if (teamMode) {
                    yield teamJoin.get(Team_.SHORT_NAME);
                }
                else {
                    yield studentJoin.get(User_.LOGIN);
                }
            }
            case "initializationState" -> root.get("initializationState");
            case "initializationDate" -> root.get("initializationDate");
            case "testRun" -> root.get("testRun");
            case "presentationScore" -> root.get("presentationScore");
            case "individualDueDate" -> root.get("individualDueDate");
            default -> root.get(DomainObject_.ID);
        };
    }

    private long executeManagementCountQuery(CriteriaBuilder cb, long exerciseId, boolean teamMode, String searchTerm, String filterProp, ZonedDateTime stuckBuildCutoff) {

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<StudentParticipation> root = countQuery.from(StudentParticipation.class);
        countQuery.select(cb.count(root));

        Join<StudentParticipation, User> studentJoin = null;
        Join<StudentParticipation, Team> teamJoin = null;
        if (teamMode) {
            teamJoin = root.join(StudentParticipation_.TEAM, JoinType.LEFT);
        }
        else {
            studentJoin = root.join(StudentParticipation_.STUDENT, JoinType.LEFT);
        }

        List<Predicate> predicates = buildManagementPredicates(cb, countQuery, root, studentJoin, teamJoin, exerciseId, teamMode, searchTerm, filterProp, stuckBuildCutoff);
        countQuery.where(cb.and(predicates.toArray(new Predicate[0])));

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    // --------------------------------------------------
    // Count query
    // --------------------------------------------------

    private long executeCountQuery(CriteriaBuilder cb, long exerciseId, boolean teamMode, String searchTerm, String filterProp, Integer scoreRangeLower, Integer scoreRangeUpper) {

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<StudentParticipation> root = countQuery.from(StudentParticipation.class);
        countQuery.select(cb.count(root));

        Join<StudentParticipation, User> studentJoin = null;
        Join<StudentParticipation, Team> teamJoin = null;
        if (teamMode) {
            teamJoin = root.join(StudentParticipation_.TEAM, JoinType.LEFT);
        }
        else {
            studentJoin = root.join(StudentParticipation_.STUDENT, JoinType.LEFT);
        }

        List<Predicate> predicates = buildPredicates(cb, countQuery, root, studentJoin, teamJoin, exerciseId, teamMode, searchTerm, filterProp, scoreRangeLower, scoreRangeUpper);
        countQuery.where(cb.and(predicates.toArray(new Predicate[0])));

        return entityManager.createQuery(countQuery).getSingleResult();
    }
}
