package de.tum.cit.aet.artemis.exercise.repository;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Result_;
import de.tum.cit.aet.artemis.core.domain.DomainObject_;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.User_;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Submission_;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.Team_;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation_;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation_;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation_;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission_;

/**
 * JPA Specifications for {@link StudentParticipation} queries.
 * Follows the same pattern as {@link de.tum.cit.aet.artemis.core.repository.UserSpecs}.
 */
public class StudentParticipationSpecs {

    // --------------------------------------------------
    // Helpers
    // --------------------------------------------------

    /**
     * Returns a no-op specification that adds no predicate (matches everything).
     */
    @NonNull
    private static Specification<StudentParticipation> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    // --------------------------------------------------
    // Basic filter specifications
    // --------------------------------------------------

    /**
     * Matches participations belonging to the given exercise.
     *
     * @param exerciseId the exercise ID
     * @return specification matching participations for the given exercise
     */
    @NonNull
    public static Specification<StudentParticipation> forExercise(long exerciseId) {
        return (root, query, cb) -> cb.equal(root.get(Participation_.EXERCISE).get(DomainObject_.ID), exerciseId);
    }

    /**
     * Matches participations that have a student (individual mode).
     *
     * @return specification matching participations with a student
     */
    @NonNull
    public static Specification<StudentParticipation> hasStudent() {
        return (root, query, cb) -> cb.isNotNull(root.get(StudentParticipation_.STUDENT));
    }

    /**
     * Matches participations that have a team (team mode).
     *
     * @return specification matching participations with a team
     */
    @NonNull
    public static Specification<StudentParticipation> hasTeam() {
        return (root, query, cb) -> cb.isNotNull(root.get(StudentParticipation_.TEAM));
    }

    /**
     * Matches participations by student or team based on team mode.
     *
     * @param teamMode whether the exercise uses teams
     * @return specification matching participations for the given mode
     */
    @NonNull
    public static Specification<StudentParticipation> forMode(boolean teamMode) {
        return teamMode ? hasTeam() : hasStudent();
    }

    /**
     * Searches participations by student login/name or team name/shortName.
     *
     * @param searchTerm the search term (may be null or blank to skip)
     * @param teamMode   whether the exercise uses teams
     * @return specification, or null if search term is blank
     */
    @NonNull
    public static Specification<StudentParticipation> searchByName(@Nullable String searchTerm, boolean teamMode) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return noOp();
        }
        List<String> tokens = Arrays.stream(searchTerm.split(",")).map(String::trim).filter(t -> !t.isBlank()).toList();
        if (tokens.isEmpty()) {
            return noOp();
        }
        if (teamMode) {
            return (root, query, cb) -> {
                List<Predicate> tokenPredicates = tokens.stream().map(token -> {
                    String pattern = likePattern(token);
                    Predicate teamNameMatch = cb.or(cb.like(cb.lower(root.get(StudentParticipation_.TEAM).get(Team_.NAME)), pattern, '\\'),
                            cb.like(cb.lower(root.get(StudentParticipation_.TEAM).get(Team_.SHORT_NAME)), pattern, '\\'));

                    Subquery<Long> studentSub = query.subquery(Long.class);
                    Root<Team> teamRoot = studentSub.from(Team.class);
                    Join<Team, User> studentJoin = teamRoot.join(Team_.STUDENTS);
                    studentSub.select(cb.literal(1L));
                    studentSub.where(cb.equal(teamRoot.get(DomainObject_.ID), root.get(StudentParticipation_.TEAM).get(DomainObject_.ID)),
                            cb.or(cb.like(cb.lower(studentJoin.get(User_.LOGIN)), pattern, '\\'), cb.like(cb.lower(studentJoin.get(User_.FIRST_NAME)), pattern, '\\'),
                                    cb.like(cb.lower(studentJoin.get(User_.LAST_NAME)), pattern, '\\')));

                    return cb.or(teamNameMatch, cb.exists(studentSub));
                }).toList();
                return cb.or(tokenPredicates.toArray(new Predicate[0]));
            };
        }
        return (root, query, cb) -> {
            List<Predicate> tokenPredicates = tokens.stream().map(token -> {
                String pattern = likePattern(token);
                return cb.or(cb.like(cb.lower(root.get(StudentParticipation_.STUDENT).get(User_.LOGIN)), pattern, '\\'),
                        cb.like(cb.lower(root.get(StudentParticipation_.STUDENT).get(User_.FIRST_NAME)), pattern, '\\'),
                        cb.like(cb.lower(root.get(StudentParticipation_.STUDENT).get(User_.LAST_NAME)), pattern, '\\'));
            }).toList();
            return cb.or(tokenPredicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Escapes SQL LIKE special characters in {@code token} and wraps it with {@code %} wildcards.
     * The escape character is {@code \}.
     */
    private static String likePattern(String token) {
        return "%" + token.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    // --------------------------------------------------
    // Reusable subquery helpers
    // --------------------------------------------------

    /**
     * Builds a subquery that returns the ID of the latest submission for the given participation root.
     */
    private static Subquery<Long> latestSubmissionIdSubquery(Root<StudentParticipation> root, jakarta.persistence.criteria.CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<Submission> subRoot = sub.from(Submission.class);
        sub.select(cb.max(subRoot.get(DomainObject_.ID)));
        sub.where(cb.equal(subRoot.get(Submission_.PARTICIPATION), root));
        return sub;
    }

    /**
     * Creates an EXISTS predicate matching the latest submission's latest non-Athena result with an additional condition.
     *
     * @param root            the participation root
     * @param query           the criteria query
     * @param cb              the criteria builder
     * @param resultCondition a function that produces an additional predicate on the result (may be null for no extra condition)
     * @return the EXISTS predicate
     */
    private static Predicate existsLatestResultMatching(Root<StudentParticipation> root, jakarta.persistence.criteria.CriteriaQuery<?> query, CriteriaBuilder cb,
            @Nullable ResultCondition resultCondition) {
        Subquery<Long> existsSub = query.subquery(Long.class);
        Root<Submission> submissionRoot = existsSub.from(Submission.class);
        Join<Submission, Result> resultJoin = submissionRoot.join(Submission_.RESULTS);

        // Latest submission
        Subquery<Long> latestSubId = latestSubmissionIdSubquery(root, query, cb);
        Predicate isLatestSubmission = cb.equal(submissionRoot.get(DomainObject_.ID), latestSubId);

        // Belongs to this participation
        Predicate belongsToParticipation = cb.equal(submissionRoot.get(Submission_.PARTICIPATION), root);

        // Latest non-Athena result
        Subquery<Long> latestResultSub = query.subquery(Long.class);
        Root<Result> latestResultRoot = latestResultSub.from(Result.class);
        latestResultSub.select(cb.max(latestResultRoot.get(DomainObject_.ID)));
        latestResultSub.where(cb.equal(latestResultRoot.get(Result_.SUBMISSION), submissionRoot),
                cb.notEqual(latestResultRoot.get(Result_.ASSESSMENT_TYPE), AssessmentType.AUTOMATIC_ATHENA));

        Predicate isLatestResult = cb.equal(resultJoin.get(DomainObject_.ID), latestResultSub);

        List<Predicate> predicates = new ArrayList<>(List.of(belongsToParticipation, isLatestSubmission, isLatestResult));

        if (resultCondition != null) {
            predicates.add(resultCondition.apply(resultJoin, cb));
        }

        existsSub.select(cb.literal(1L));
        existsSub.where(predicates.toArray(Predicate[]::new));

        return cb.exists(existsSub);
    }

    @FunctionalInterface
    private interface ResultCondition {

        Predicate apply(jakarta.persistence.criteria.Join<Submission, Result> resultJoin, CriteriaBuilder cb);
    }

    // --------------------------------------------------
    // Scores view filter specifications
    // --------------------------------------------------

    /**
     * Matches participations whose latest result is successful.
     *
     * @return specification matching participations with a successful latest result
     */
    @NonNull
    public static Specification<StudentParticipation> isSuccessful() {
        return (root, query, cb) -> existsLatestResultMatching(root, query, cb, (r, c) -> c.equal(r.get(Result_.SUCCESSFUL), true));
    }

    /**
     * Matches participations whose latest result is NOT successful.
     *
     * @return specification matching participations without a successful latest result
     */
    @NonNull
    public static Specification<StudentParticipation> isUnsuccessful() {
        return (root, query, cb) -> existsLatestResultMatching(root, query, cb, (r, c) -> c.equal(r.get(Result_.SUCCESSFUL), true)).not();
    }

    /**
     * Matches participations whose latest submission is a ProgrammingSubmission with buildFailed = true.
     *
     * @return specification matching participations with a failed build
     */
    @NonNull
    public static Specification<StudentParticipation> isBuildFailed() {
        return (root, query, cb) -> {
            Subquery<Long> existsSub = query.subquery(Long.class);
            Root<ProgrammingSubmission> subRoot = existsSub.from(ProgrammingSubmission.class);
            Subquery<Long> latestSubId = latestSubmissionIdSubquery(root, query, cb);
            existsSub.select(cb.literal(1L));
            existsSub.where(cb.equal(subRoot.get(Submission_.PARTICIPATION), root), cb.equal(subRoot.get(DomainObject_.ID), latestSubId),
                    cb.equal(subRoot.get(ProgrammingSubmission_.BUILD_FAILED), true));
            return cb.exists(existsSub);
        };
    }

    /**
     * Matches participations whose latest result has a manual or semi-automatic assessment type.
     *
     * @return specification matching participations with a manual or semi-automatic latest result
     */
    @NonNull
    public static Specification<StudentParticipation> hasManualAssessment() {
        return (root, query, cb) -> existsLatestResultMatching(root, query, cb, (r, c) -> r.get(Result_.ASSESSMENT_TYPE).in(AssessmentType.MANUAL, AssessmentType.SEMI_AUTOMATIC));
    }

    /**
     * Matches participations whose latest result has an automatic assessment type.
     *
     * @return specification matching participations with an automatic latest result
     */
    @NonNull
    public static Specification<StudentParticipation> hasAutomaticAssessment() {
        return (root, query, cb) -> existsLatestResultMatching(root, query, cb, (r, c) -> c.equal(r.get(Result_.ASSESSMENT_TYPE), AssessmentType.AUTOMATIC));
    }

    /**
     * Matches participations whose latest result has no completion date (i.e. is locked).
     *
     * @return specification matching participations with a locked latest result
     */
    @NonNull
    public static Specification<StudentParticipation> isLocked() {
        return (root, query, cb) -> existsLatestResultMatching(root, query, cb, (r, c) -> c.isNull(r.get(Result_.COMPLETION_DATE)));
    }

    /**
     * Matches participations whose latest result score is in the given range.
     * Lower bound is inclusive, upper bound is exclusive unless it equals 100 (then inclusive).
     *
     * @param scoreRangeLower inclusive lower bound (nullable to skip)
     * @param scoreRangeUpper upper bound (nullable to skip)
     * @return specification, or null if both bounds are null
     */
    @NonNull
    public static Specification<StudentParticipation> scoreInRange(@Nullable Integer scoreRangeLower, @Nullable Integer scoreRangeUpper) {
        if (scoreRangeLower == null || scoreRangeUpper == null) {
            return noOp();
        }
        return (root, query, cb) -> existsLatestResultMatching(root, query, cb, (r, c) -> {
            Predicate lowerBound = c.ge(r.get(Result_.SCORE), scoreRangeLower);
            Predicate upperBound = scoreRangeUpper == 100 ? c.le(r.get(Result_.SCORE), 100) : c.lt(r.get(Result_.SCORE), scoreRangeUpper);
            return c.and(lowerBound, upperBound);
        });
    }

    /**
     * Returns the filter specification for the scores view based on filter name.
     *
     * @param filterProp filter name (Successful, Unsuccessful, BuildFailed, Manual, Automatic, Locked)
     * @return specification, or null for unrecognized filterProp
     */
    @NonNull
    public static Specification<StudentParticipation> scoresFilter(@Nullable String filterProp) {
        if (filterProp == null) {
            return noOp();
        }
        return switch (filterProp) {
            case "Successful" -> isSuccessful();
            case "Unsuccessful" -> isUnsuccessful();
            case "BuildFailed" -> isBuildFailed();
            case "Manual" -> hasManualAssessment();
            case "Automatic" -> hasAutomaticAssessment();
            case "Locked" -> isLocked();
            default -> noOp();
        };
    }

    // --------------------------------------------------
    // Management view filter specifications
    // --------------------------------------------------

    /**
     * Matches participations whose latest submission has no result and was submitted before the cutoff (stuck build).
     *
     * @param stuckBuildCutoff the cutoff timestamp
     * @return specification matching participations with a stuck/failed build
     */
    @NonNull
    public static Specification<StudentParticipation> hasFailedBuild(@Nullable ZonedDateTime stuckBuildCutoff) {
        return (root, query, cb) -> {
            if (stuckBuildCutoff == null) {
                return cb.disjunction();
            }
            Subquery<Long> existsSub = query.subquery(Long.class);
            Root<Submission> subRoot = existsSub.from(Submission.class);
            Subquery<Long> latestSubId = latestSubmissionIdSubquery(root, query, cb);

            // No result exists for this submission
            Subquery<Long> noResultSub = query.subquery(Long.class);
            Root<Result> noResultRoot = noResultSub.from(Result.class);
            noResultSub.select(cb.literal(1L));
            noResultSub.where(cb.equal(noResultRoot.get(Result_.SUBMISSION), subRoot));

            existsSub.select(cb.literal(1L));
            existsSub.where(cb.equal(subRoot.get(Submission_.PARTICIPATION), root), cb.equal(subRoot.get(DomainObject_.ID), latestSubId), cb.exists(noResultSub).not(),
                    cb.lessThan(subRoot.get(Submission_.SUBMISSION_DATE), stuckBuildCutoff));
            return cb.exists(existsSub);
        };
    }

    /**
     * Matches participations that have no submissions.
     *
     * @return specification matching participations with no submissions
     */
    @NonNull
    public static Specification<StudentParticipation> hasNoSubmissions() {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Submission> subRoot = sub.from(Submission.class);
            sub.select(cb.literal(1L));
            sub.where(cb.equal(subRoot.get(Submission_.PARTICIPATION), root));
            return cb.exists(sub).not();
        };
    }

    /**
     * Matches participations that are not in practice mode (testRun is null or false).
     *
     * @return specification matching participations not in practice mode
     */
    @NonNull
    public static Specification<StudentParticipation> isNotPracticeMode() {
        return (root, query, cb) -> cb.or(cb.isNull(root.get(Participation_.TEST_RUN)), cb.equal(root.get(Participation_.TEST_RUN), false));
    }

    /**
     * Returns the filter specification for the management view based on filter name.
     *
     * @param filterProp       filter name (Failed, NoSubmissions, NoPracticeMode)
     * @param stuckBuildCutoff cutoff timestamp for the Failed filter
     * @return specification, or null for unrecognized filterProp
     */
    @NonNull
    public static Specification<StudentParticipation> managementFilter(@Nullable String filterProp, @Nullable ZonedDateTime stuckBuildCutoff) {
        if (filterProp == null) {
            return noOp();
        }
        return switch (filterProp) {
            case "Failed" -> hasFailedBuild(stuckBuildCutoff);
            case "NoSubmissions" -> hasNoSubmissions();
            case "NoPracticeMode" -> isNotPracticeMode();
            default -> noOp();
        };
    }

    // --------------------------------------------------
    // Ordering specifications
    // --------------------------------------------------

    /**
     * Applies sorting for the scores view.
     * Uses CriteriaQuery.orderBy() as a side-effect, since sorting by subquery results
     * cannot be expressed through Spring Data's Sort abstraction.
     *
     * @param sortedColumn the column to sort by (participantName, participantIdentifier, score, completionDate)
     * @param sortOrder    ascending or descending
     * @param teamMode     whether the exercise uses teams
     * @return specification that applies ordering
     */
    @NonNull
    public static Specification<StudentParticipation> orderedForScores(@Nullable String sortedColumn, SortingOrder sortOrder, boolean teamMode) {
        return (root, query, cb) -> {
            if (query == null || sortedColumn == null || sortedColumn.isBlank()) {
                return null;
            }
            List<Order> orders = new ArrayList<>();
            boolean asc = sortOrder == SortingOrder.ASCENDING;

            switch (sortedColumn) {
                case "id", "participationId" -> {
                    Expression<?> expr = root.get(DomainObject_.ID);
                    orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                }
                case "participantName" -> {
                    if (teamMode) {
                        Expression<?> expr = root.get(StudentParticipation_.TEAM).get(Team_.NAME);
                        orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                    }
                    else {
                        Expression<String> fullName = cb.concat(cb.concat(cb.coalesce(root.get(StudentParticipation_.STUDENT).get(User_.FIRST_NAME), ""), " "),
                                cb.coalesce(root.get(StudentParticipation_.STUDENT).get(User_.LAST_NAME), ""));
                        orders.add(asc ? cb.asc(fullName) : cb.desc(fullName));
                    }
                }
                case "participantIdentifier" -> {
                    Expression<?> expr = teamMode ? root.get(StudentParticipation_.TEAM).get(Team_.SHORT_NAME) : root.get(StudentParticipation_.STUDENT).get(User_.LOGIN);
                    orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                }
                case "submissionCount" -> {
                    Subquery<Long> countSub = query.subquery(Long.class);
                    Root<Submission> submissionRoot = countSub.from(Submission.class);
                    countSub.select(cb.count(submissionRoot)).where(cb.equal(submissionRoot.get(Submission_.PARTICIPATION), root));
                    orders.add(asc ? cb.asc(countSub) : cb.desc(countSub));
                }
                case "score" -> {
                    Subquery<Double> scoreSub = buildLatestResultFieldSubquery(root, query, cb, Result_.SCORE, Double.class);
                    orders.add(asc ? cb.asc(scoreSub) : cb.desc(scoreSub));
                }
                case "completionDate" -> {
                    Subquery<ZonedDateTime> dateSub = buildLatestResultFieldSubquery(root, query, cb, Result_.COMPLETION_DATE, ZonedDateTime.class);
                    orders.add(asc ? cb.asc(dateSub) : cb.desc(dateSub));
                }
                case "assessmentType" -> {
                    Subquery<AssessmentType> typeSub = buildLatestResultFieldSubquery(root, query, cb, Result_.ASSESSMENT_TYPE, AssessmentType.class);
                    orders.add(asc ? cb.asc(typeSub) : cb.desc(typeSub));
                }
                case "testRun" -> {
                    Expression<?> expr = root.get(Participation_.TEST_RUN);
                    orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                }
                default -> {
                    // no custom ordering
                }
            }
            // Tiebreaker: always sort by ID ascending
            orders.add(cb.asc(root.get(DomainObject_.ID)));
            query.orderBy(orders);
            return null;
        };
    }

    /**
     * Applies sorting for the management view.
     *
     * @param sortedColumn the column to sort by
     * @param sortOrder    ascending or descending
     * @param teamMode     whether the exercise uses teams
     * @return specification that applies ordering
     */
    @NonNull
    public static Specification<StudentParticipation> orderedForManagement(@Nullable String sortedColumn, SortingOrder sortOrder, boolean teamMode) {
        return (root, query, cb) -> {
            if (query == null || sortedColumn == null || sortedColumn.isBlank()) {
                return null;
            }
            List<Order> orders = new ArrayList<>();
            boolean asc = sortOrder == SortingOrder.ASCENDING;

            switch (sortedColumn) {
                case "id", "participationId" -> {
                    Expression<?> expr = root.get(DomainObject_.ID);
                    orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                }
                case "participantName" -> {
                    if (teamMode) {
                        Expression<?> expr = root.get(StudentParticipation_.TEAM).get(Team_.NAME);
                        orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                    }
                    else {
                        Expression<String> fullName = cb.concat(cb.concat(cb.coalesce(root.get(StudentParticipation_.STUDENT).get(User_.FIRST_NAME), ""), " "),
                                cb.coalesce(root.get(StudentParticipation_.STUDENT).get(User_.LAST_NAME), ""));
                        orders.add(asc ? cb.asc(fullName) : cb.desc(fullName));
                    }
                }
                case "participantIdentifier" -> {
                    Expression<?> expr = teamMode ? root.get(StudentParticipation_.TEAM).get(Team_.SHORT_NAME) : root.get(StudentParticipation_.STUDENT).get(User_.LOGIN);
                    orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                }
                case "submissionCount" -> {
                    Subquery<Long> countSub = query.subquery(Long.class);
                    Root<Submission> submissionRoot = countSub.from(Submission.class);
                    countSub.select(cb.count(submissionRoot)).where(cb.equal(submissionRoot.get(Submission_.PARTICIPATION), root));
                    orders.add(asc ? cb.asc(countSub) : cb.desc(countSub));
                }
                case "initializationState" -> {
                    // Sort by logical progress order: INACTIVE is treated as a deactivated INITIALIZED state,
                    // so it appears after INITIALIZED rather than at its stateNumber position (3).
                    Expression<Object> sortWeight = cb.selectCase(root.<InitializationState>get(Participation_.INITIALIZATION_STATE)).when(InitializationState.UNINITIALIZED, 0)
                            .when(InitializationState.REPO_COPIED, 1).when(InitializationState.REPO_CONFIGURED, 2).when(InitializationState.BUILD_PLAN_COPIED, 3)
                            .when(InitializationState.BUILD_PLAN_CONFIGURED, 4).when(InitializationState.INITIALIZED, 5).when(InitializationState.INACTIVE, 6).otherwise(7);
                    orders.add(asc ? cb.asc(sortWeight) : cb.desc(sortWeight));
                }
                case "buildPlanId" -> {
                    Expression<?> expr = cb.treat(root, ProgrammingExerciseStudentParticipation.class).get(ProgrammingExerciseStudentParticipation_.BUILD_PLAN_ID);
                    orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                }
                case "initializationDate" -> {
                    Expression<?> expr = root.get(Participation_.INITIALIZATION_DATE);
                    orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                }
                case "individualDueDate" -> {
                    Expression<?> expr = root.get(Participation_.INDIVIDUAL_DUE_DATE);
                    orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                }
                case "presentationScore" -> {
                    Expression<?> expr = root.get(StudentParticipation_.PRESENTATION_SCORE);
                    orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                }
                case "testRun" -> {
                    Expression<?> expr = root.get(Participation_.TEST_RUN);
                    orders.add(asc ? cb.asc(expr) : cb.desc(expr));
                }
                default -> {
                    // no custom ordering
                }
            }
            // Tiebreaker
            orders.add(cb.asc(root.get(DomainObject_.ID)));
            query.orderBy(orders);
            return null;
        };
    }

    /**
     * Builds a subquery selecting a field from the latest non-Athena result of the latest submission.
     *
     * @param <T>       the result field type
     * @param root      the participation root
     * @param query     the criteria query
     * @param cb        the criteria builder
     * @param fieldName the Result_ field name
     * @param type      the result type class
     * @return a subquery for the requested field
     */
    private static <T> Subquery<T> buildLatestResultFieldSubquery(Root<StudentParticipation> root, jakarta.persistence.criteria.CriteriaQuery<?> query, CriteriaBuilder cb,
            String fieldName, Class<T> type) {
        Subquery<T> sub = query.subquery(type);
        Root<Submission> submissionRoot = sub.from(Submission.class);
        Join<Submission, Result> resultJoin = submissionRoot.join(Submission_.RESULTS);

        Subquery<Long> latestSubId = latestSubmissionIdSubquery(root, query, cb);

        Subquery<Long> latestResultSub = query.subquery(Long.class);
        Root<Result> latestResultRoot = latestResultSub.from(Result.class);
        latestResultSub.select(cb.max(latestResultRoot.get(DomainObject_.ID)));
        latestResultSub.where(cb.equal(latestResultRoot.get(Result_.SUBMISSION), submissionRoot),
                cb.notEqual(latestResultRoot.get(Result_.ASSESSMENT_TYPE), AssessmentType.AUTOMATIC_ATHENA));

        sub.select(resultJoin.get(fieldName));
        sub.where(cb.equal(submissionRoot.get(Submission_.PARTICIPATION), root), cb.equal(submissionRoot.get(DomainObject_.ID), latestSubId),
                cb.equal(resultJoin.get(DomainObject_.ID), latestResultSub));
        return sub;
    }
}
