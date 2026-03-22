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

    // --------------------------------------------------
    // Shared infrastructure
    // --------------------------------------------------

    /**
     * Holds the student/team joins created for a query root. Exactly one of the two fields is
     * non-null depending on whether the exercise uses team mode.
     */
    private record ParticipantJoins(Join<StudentParticipation, User> studentJoin, Join<StudentParticipation, Team> teamJoin) {
    }

    private ParticipantJoins createParticipantJoins(Root<StudentParticipation> root, boolean teamMode) {
        if (teamMode) {
            return new ParticipantJoins(null, root.join(StudentParticipation_.TEAM, JoinType.LEFT));
        }
        return new ParticipantJoins(root.join(StudentParticipation_.STUDENT, JoinType.LEFT), null);
    }

    /**
     * Builds the predicates that are common to both the scores and management queries:
     * exercise match, participant-not-null guard, and free-text search term.
     */
    private List<Predicate> buildBasePredicates(CriteriaBuilder cb, Root<StudentParticipation> root, ParticipantJoins joins, long exerciseId, boolean teamMode, String searchTerm) {
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
                predicates.add(cb.or(cb.like(cb.lower(joins.teamJoin().get(Team_.NAME)), pattern), cb.like(cb.lower(joins.teamJoin().get(Team_.SHORT_NAME)), pattern)));
            }
            else {
                predicates.add(cb.or(cb.like(cb.lower(joins.studentJoin().get(User_.LOGIN)), pattern), cb.like(cb.lower(joins.studentJoin().get(User_.FIRST_NAME)), pattern),
                        cb.like(cb.lower(joins.studentJoin().get(User_.LAST_NAME)), pattern)));
            }
        }

        return predicates;
    }

    /**
     * Functional interface for building predicates against a fresh query root.
     * Used by {@link #executeCountQuery} to avoid sharing predicates across queries.
     */
    @FunctionalInterface
    private interface PredicateBuilder {

        List<Predicate> build(CriteriaBuilder cb, CriteriaQuery<?> query, Root<StudentParticipation> root);
    }

    /**
     * Executes a COUNT query by rebuilding predicates against a fresh root via {@code builder}.
     * Predicates from the ID query must NOT be reused here — Hibernate 6 binds predicates to
     * the specific Root they were built with, so sharing them across queries causes
     * "Could not locate TableGroup" errors.
     */
    private long executeCountQuery(PredicateBuilder builder) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<StudentParticipation> root = countQuery.from(StudentParticipation.class);
        countQuery.select(cb.count(root));
        List<Predicate> predicates = builder.build(cb, countQuery, root);
        countQuery.where(cb.and(predicates.toArray(new Predicate[0])));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    // --------------------------------------------------
    // Scores view
    // --------------------------------------------------

    @Override
    public Page<Long> findParticipationIdsForScores(long exerciseId, boolean teamMode, String searchTerm, String filterProp, Integer scoreRangeLower, Integer scoreRangeUpper,
            Pageable pageable, SortingOrder sortOrder, String sortedColumn) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<StudentParticipation> root = idQuery.from(StudentParticipation.class);
        idQuery.select(root.get(DomainObject_.ID));

        ParticipantJoins joins = createParticipantJoins(root, teamMode);
        List<Predicate> predicates = buildBasePredicates(cb, root, joins, exerciseId, teamMode, searchTerm);
        addScoresFilterPredicate(cb, idQuery, root, predicates, filterProp);
        addScoreRangePredicate(cb, idQuery, root, predicates, scoreRangeLower, scoreRangeUpper);
        idQuery.where(cb.and(predicates.toArray(new Predicate[0])));

        Expression<?> sortExpr = buildScoresSortExpression(cb, idQuery, root, joins, teamMode, sortedColumn);
        idQuery.orderBy((sortOrder == SortingOrder.DESCENDING) ? cb.desc(sortExpr) : cb.asc(sortExpr), cb.asc(root.get(DomainObject_.ID)));

        TypedQuery<Long> typedQuery = entityManager.createQuery(idQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<Long> ids = typedQuery.getResultList();

        long total = executeCountQuery((cb2, cq, countRoot) -> {
            ParticipantJoins countJoins = createParticipantJoins(countRoot, teamMode);
            List<Predicate> countPreds = buildBasePredicates(cb2, countRoot, countJoins, exerciseId, teamMode, searchTerm);
            addScoresFilterPredicate(cb2, cq, countRoot, countPreds, filterProp);
            addScoreRangePredicate(cb2, cq, countRoot, countPreds, scoreRangeLower, scoreRangeUpper);
            return countPreds;
        });
        return new PageImpl<>(ids, pageable, total);
    }

    private void addScoresFilterPredicate(CriteriaBuilder cb, CriteriaQuery<?> query, Root<StudentParticipation> root, List<Predicate> predicates, String filterProp) {
        if (filterProp == null || "All".equals(filterProp)) {
            return;
        }
        switch (filterProp) {
            case "Successful" -> predicates.add(latestResultExists(cb, query, root, r -> cb.equal(r.get(Result_.SUCCESSFUL), true)));
            case "Unsuccessful" -> {
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

    private Expression<?> buildScoresSortExpression(CriteriaBuilder cb, CriteriaQuery<Long> query, Root<StudentParticipation> root, ParticipantJoins joins, boolean teamMode,
            String sortedColumn) {
        return switch (sortedColumn != null ? sortedColumn : "") {
            case "participantName" -> teamMode ? joins.teamJoin().get(Team_.NAME) : cb.coalesce(joins.studentJoin().get(User_.LAST_NAME), cb.literal(""));
            case "participantIdentifier" -> teamMode ? joins.teamJoin().get(Team_.SHORT_NAME) : joins.studentJoin().get(User_.LOGIN);
            case "score" -> latestResultScalarSubquery(cb, query, root, Result_.SCORE, Double.class);
            case "completionDate" -> latestResultScalarSubquery(cb, query, root, Result_.COMPLETION_DATE, java.time.ZonedDateTime.class);
            default -> root.get(DomainObject_.ID);
        };
    }

    // --------------------------------------------------
    // Management view
    // --------------------------------------------------

    @Override
    public Page<Long> findParticipationIdsForManagement(long exerciseId, boolean teamMode, String searchTerm, String filterProp, ZonedDateTime stuckBuildCutoff, Pageable pageable,
            SortingOrder sortOrder, String sortedColumn) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<StudentParticipation> root = idQuery.from(StudentParticipation.class);
        idQuery.select(root.get(DomainObject_.ID));

        ParticipantJoins joins = createParticipantJoins(root, teamMode);
        List<Predicate> predicates = buildBasePredicates(cb, root, joins, exerciseId, teamMode, searchTerm);
        addManagementFilterPredicate(cb, idQuery, root, predicates, filterProp, stuckBuildCutoff);
        idQuery.where(cb.and(predicates.toArray(new Predicate[0])));

        Expression<?> sortExpr = buildManagementSortExpression(cb, root, joins, teamMode, sortedColumn);
        idQuery.orderBy((sortOrder == SortingOrder.DESCENDING) ? cb.desc(sortExpr) : cb.asc(sortExpr), cb.asc(root.get(DomainObject_.ID)));

        TypedQuery<Long> typedQuery = entityManager.createQuery(idQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<Long> ids = typedQuery.getResultList();

        long total = executeCountQuery((cb2, cq, countRoot) -> {
            ParticipantJoins countJoins = createParticipantJoins(countRoot, teamMode);
            List<Predicate> countPreds = buildBasePredicates(cb2, countRoot, countJoins, exerciseId, teamMode, searchTerm);
            addManagementFilterPredicate(cb2, cq, countRoot, countPreds, filterProp, stuckBuildCutoff);
            return countPreds;
        });
        return new PageImpl<>(ids, pageable, total);
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

    private Expression<?> buildManagementSortExpression(CriteriaBuilder cb, Root<StudentParticipation> root, ParticipantJoins joins, boolean teamMode, String sortedColumn) {
        return switch (sortedColumn != null ? sortedColumn : "") {
            case "participantName" -> teamMode ? joins.teamJoin().get(Team_.NAME) : cb.coalesce(joins.studentJoin().get(User_.LAST_NAME), cb.literal(""));
            case "participantIdentifier" -> teamMode ? joins.teamJoin().get(Team_.SHORT_NAME) : joins.studentJoin().get(User_.LOGIN);
            case "initializationState" -> root.get("initializationState");
            case "initializationDate" -> root.get("initializationDate");
            case "testRun" -> root.get("testRun");
            case "presentationScore" -> root.get("presentationScore");
            case "individualDueDate" -> root.get("individualDueDate");
            default -> root.get(DomainObject_.ID);
        };
    }

    // --------------------------------------------------
    // EXISTS subquery helpers
    // --------------------------------------------------

    @FunctionalInterface
    private interface ResultCondition {

        Predicate apply(From<?, Result> resultPath);
    }

    /**
     * Creates an EXISTS subquery that checks if the latest result of the latest submission
     * satisfies an additional condition.
     */
    private Predicate latestResultExists(CriteriaBuilder cb, CriteriaQuery<?> parentQuery, Root<StudentParticipation> participationRoot, ResultCondition condition) {

        Subquery<Long> existsSub = parentQuery.subquery(Long.class);
        Root<Submission> subSRoot = existsSub.from(Submission.class);
        Join<Submission, Result> subRJoin = subSRoot.join(Submission_.RESULTS, JoinType.INNER);
        existsSub.select(cb.literal(1L));

        Predicate participationMatch = cb.equal(subSRoot.get(Submission_.PARTICIPATION), participationRoot);

        Subquery<Long> maxSubId = existsSub.subquery(Long.class);
        Root<Submission> msRoot = maxSubId.from(Submission.class);
        maxSubId.select(cb.max(msRoot.get(DomainObject_.ID)));
        maxSubId.where(cb.equal(msRoot.get(Submission_.PARTICIPATION), participationRoot));
        Predicate latestSubmission = cb.equal(subSRoot.get(DomainObject_.ID), maxSubId);

        Subquery<Long> maxResId = existsSub.subquery(Long.class);
        Root<Result> mrRoot = maxResId.from(Result.class);
        maxResId.select(cb.max(mrRoot.get(DomainObject_.ID)));
        maxResId.where(cb.equal(mrRoot.get(Result_.SUBMISSION), subSRoot), cb.notEqual(mrRoot.get(Result_.ASSESSMENT_TYPE), AssessmentType.AUTOMATIC_ATHENA));
        Predicate latestResult = cb.equal(subRJoin.get(DomainObject_.ID), maxResId);

        existsSub.where(participationMatch, latestSubmission, latestResult, condition.apply(subRJoin));
        return cb.exists(existsSub);
    }

    /**
     * EXISTS subquery for the BuildFailed filter. Checks if the latest submission is a
     * ProgrammingSubmission with buildFailed = true.
     */
    private Predicate buildFailedExists(CriteriaBuilder cb, CriteriaQuery<?> parentQuery, Root<StudentParticipation> participationRoot) {

        Subquery<Long> existsSub = parentQuery.subquery(Long.class);
        Root<ProgrammingSubmission> subRoot = existsSub.from(ProgrammingSubmission.class);
        existsSub.select(cb.literal(1L));

        Predicate participationMatch = cb.equal(subRoot.get(Submission_.PARTICIPATION), participationRoot);

        Subquery<Long> maxSubId = existsSub.subquery(Long.class);
        Root<Submission> msRoot = maxSubId.from(Submission.class);
        maxSubId.select(cb.max(msRoot.get(DomainObject_.ID)));
        maxSubId.where(cb.equal(msRoot.get(Submission_.PARTICIPATION), participationRoot));
        Predicate latestSubmission = cb.equal(subRoot.get(DomainObject_.ID), maxSubId);

        Predicate buildFailed = cb.equal(subRoot.get(ProgrammingSubmission_.BUILD_FAILED), true);

        existsSub.where(participationMatch, latestSubmission, buildFailed);

        return cb.exists(existsSub);
    }

    /**
     * EXISTS subquery for the Failed (stuck build) filter.
     * Matches participations whose latest submission has no result yet and was submitted before {@code cutoff}.
     */
    private Predicate stuckBuildExists(CriteriaBuilder cb, CriteriaQuery<?> parentQuery, Root<StudentParticipation> participationRoot, ZonedDateTime cutoff) {

        Subquery<Long> existsSub = parentQuery.subquery(Long.class);
        Root<Submission> subRoot = existsSub.from(Submission.class);
        existsSub.select(cb.literal(1L));

        Predicate participationMatch = cb.equal(subRoot.get(Submission_.PARTICIPATION), participationRoot);

        Subquery<Long> maxSubId = existsSub.subquery(Long.class);
        Root<Submission> msRoot = maxSubId.from(Submission.class);
        maxSubId.select(cb.max(msRoot.get(DomainObject_.ID)));
        maxSubId.where(cb.equal(msRoot.get(Submission_.PARTICIPATION), participationRoot));
        Predicate latestSubmission = cb.equal(subRoot.get(DomainObject_.ID), maxSubId);

        Subquery<Long> resultSub = existsSub.subquery(Long.class);
        Root<Result> resultRoot = resultSub.from(Result.class);
        resultSub.select(cb.literal(1L));
        resultSub.where(cb.equal(resultRoot.get(Result_.SUBMISSION), subRoot));
        Predicate noResult = cb.not(cb.exists(resultSub));

        Predicate beforeCutoff = cb.lessThan(subRoot.get("submissionDate"), cutoff);

        existsSub.where(participationMatch, latestSubmission, noResult, beforeCutoff);

        return cb.exists(existsSub);
    }

    /**
     * Scalar subquery returning a single result field for the latest result of the latest submission.
     * Used for ORDER BY on result fields without joining.
     */
    private <T> Expression<T> latestResultScalarSubquery(CriteriaBuilder cb, CriteriaQuery<Long> parentQuery, Root<StudentParticipation> participationRoot, String resultField,
            Class<T> type) {

        Subquery<T> sub = parentQuery.subquery(type);
        Root<Submission> sRoot = sub.from(Submission.class);
        Join<Submission, Result> rJoin = sRoot.join(Submission_.RESULTS, JoinType.INNER);
        sub.select(rJoin.get(resultField));

        Predicate participationMatch = cb.equal(sRoot.get(Submission_.PARTICIPATION), participationRoot);

        Subquery<Long> maxSubId = sub.subquery(Long.class);
        Root<Submission> msRoot = maxSubId.from(Submission.class);
        maxSubId.select(cb.max(msRoot.get(DomainObject_.ID)));
        maxSubId.where(cb.equal(msRoot.get(Submission_.PARTICIPATION), participationRoot));
        Predicate latestSubmission = cb.equal(sRoot.get(DomainObject_.ID), maxSubId);

        Subquery<Long> maxResId = sub.subquery(Long.class);
        Root<Result> mrRoot = maxResId.from(Result.class);
        maxResId.select(cb.max(mrRoot.get(DomainObject_.ID)));
        maxResId.where(cb.equal(mrRoot.get(Result_.SUBMISSION), sRoot), cb.notEqual(mrRoot.get(Result_.ASSESSMENT_TYPE), AssessmentType.AUTOMATIC_ATHENA));
        Predicate latestResult = cb.equal(rJoin.get(DomainObject_.ID), maxResId);

        sub.where(participationMatch, latestSubmission, latestResult);
        return sub;
    }
}
