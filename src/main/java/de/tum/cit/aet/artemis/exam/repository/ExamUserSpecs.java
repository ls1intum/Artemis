package de.tum.cit.aet.artemis.exam.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.core.domain.DomainObject_;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.User_;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.exam.domain.ExamSession;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.ExamUser_;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam_;
import de.tum.cit.aet.artemis.exam.dto.ExamStudentDTO;

/**
 * Specifications for filtering, searching, and ordering {@link ExamUser} entities for the paginated exam-students view.
 */
public final class ExamUserSpecs {

    private ExamUserSpecs() {
    }

    /** Returns a specification that matches all records without adding any predicate. */
    @NonNull
    private static Specification<ExamUser> noOp() {
        return (root, query, builder) -> builder.conjunction();
    }

    /** LIKE-escape for user input. Escapes backslash, percent, and underscore so they are treated literally. */
    private static String escapeForLike(String term) {
        return term.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Narrows the result set to a specific student progress state or attendance check state.
     * Recognised values: {@code "ExamMissing"}, {@code "NotStarted"}, {@code "Started"}, {@code "Submitted"},
     * {@code "AttendanceChecked"}, {@code "AttendanceNotChecked"}, {@code "DidNotAttend"}. Blank or {@code null} values return a no-op.
     *
     * @param filter the filter value from the frontend; unrecognised values are treated as no-op
     * @return specification applying the requested filter predicate
     */
    @NonNull
    public static Specification<ExamUser> filteredBy(@Nullable String filter) {
        if (filter == null || filter.isBlank()) {
            return noOp();
        }
        return switch (filter) {
            case "ExamMissing" -> isExamMissing();
            case "NotStarted" -> isNotStarted();
            case "Started" -> isStarted();
            case "Submitted" -> isSubmitted();
            case "AttendanceChecked" -> isAttendanceChecked();
            case "AttendanceNotChecked" -> isAttendanceNotChecked();
            case "DidNotAttend" -> isDidNotAttend();
            default -> noOp();
        };
    }

    /** Matches exam users for whom no non-test-run StudentExam exists. */
    @NonNull
    private static Specification<ExamUser> isExamMissing() {
        return (root, query, builder) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<StudentExam> se = sub.from(StudentExam.class);
            sub.select(builder.literal(1));
            sub.where(builder.and(builder.equal(se.get(StudentExam_.USER), root.get(ExamUser_.USER)), builder.equal(se.get(StudentExam_.EXAM), root.get(ExamUser_.EXAM)),
                    builder.or(builder.isNull(se.get(StudentExam_.TEST_RUN)), builder.isFalse(se.get(StudentExam_.TEST_RUN)))));
            return builder.not(builder.exists(sub));
        };
    }

    /** Matches exam users whose non-test-run StudentExam exists but has not been started. */
    @NonNull
    private static Specification<ExamUser> isNotStarted() {
        return (root, query, builder) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<StudentExam> se = sub.from(StudentExam.class);
            sub.select(builder.literal(1));
            sub.where(builder.and(builder.equal(se.get(StudentExam_.USER), root.get(ExamUser_.USER)), builder.equal(se.get(StudentExam_.EXAM), root.get(ExamUser_.EXAM)),
                    builder.or(builder.isNull(se.get(StudentExam_.TEST_RUN)), builder.isFalse(se.get(StudentExam_.TEST_RUN))),
                    builder.or(builder.isNull(se.get(StudentExam_.STARTED)), builder.isFalse(se.get(StudentExam_.STARTED)))));
            return builder.exists(sub);
        };
    }

    /** Matches exam users whose non-test-run StudentExam has been started but not yet submitted. */
    @NonNull
    private static Specification<ExamUser> isStarted() {
        return (root, query, builder) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<StudentExam> se = sub.from(StudentExam.class);
            sub.select(builder.literal(1));
            sub.where(builder.and(builder.equal(se.get(StudentExam_.USER), root.get(ExamUser_.USER)), builder.equal(se.get(StudentExam_.EXAM), root.get(ExamUser_.EXAM)),
                    builder.or(builder.isNull(se.get(StudentExam_.TEST_RUN)), builder.isFalse(se.get(StudentExam_.TEST_RUN))), builder.isTrue(se.get(StudentExam_.STARTED)),
                    builder.or(builder.isNull(se.get(StudentExam_.SUBMITTED)), builder.isFalse(se.get(StudentExam_.SUBMITTED)))));
            return builder.exists(sub);
        };
    }

    /** Matches exam users whose non-test-run StudentExam has been submitted. */
    @NonNull
    private static Specification<ExamUser> isSubmitted() {
        return (root, query, builder) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<StudentExam> se = sub.from(StudentExam.class);
            sub.select(builder.literal(1));
            sub.where(builder.and(builder.equal(se.get(StudentExam_.USER), root.get(ExamUser_.USER)), builder.equal(se.get(StudentExam_.EXAM), root.get(ExamUser_.EXAM)),
                    builder.or(builder.isNull(se.get(StudentExam_.TEST_RUN)), builder.isFalse(se.get(StudentExam_.TEST_RUN))), builder.isTrue(se.get(StudentExam_.SUBMITTED))));
            return builder.exists(sub);
        };
    }

    /** Matches exam users who have a signing image and all attendance checks completed. */
    @NonNull
    private static Specification<ExamUser> isAttendanceChecked() {
        return (root, query, builder) -> builder.and(builder.isNotNull(root.get(ExamUser_.SIGNING_IMAGE_PATH)), builder.notEqual(root.get(ExamUser_.SIGNING_IMAGE_PATH), ""),
                builder.isTrue(root.get(ExamUser_.DID_CHECK_IMAGE)), builder.isTrue(root.get(ExamUser_.DID_CHECK_NAME)), builder.isTrue(root.get(ExamUser_.DID_CHECK_LOGIN)),
                builder.isTrue(root.get(ExamUser_.DID_CHECK_REGISTRATION_NUMBER)));
    }

    /** Matches exam users whose non-test-run StudentExam was started but attendance checks are incomplete. */
    @NonNull
    private static Specification<ExamUser> isAttendanceNotChecked() {
        return (root, query, builder) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<StudentExam> se = sub.from(StudentExam.class);
            sub.select(builder.literal(1));
            sub.where(builder.and(builder.equal(se.get(StudentExam_.USER), root.get(ExamUser_.USER)), builder.equal(se.get(StudentExam_.EXAM), root.get(ExamUser_.EXAM)),
                    builder.or(builder.isNull(se.get(StudentExam_.TEST_RUN)), builder.isFalse(se.get(StudentExam_.TEST_RUN))), builder.isTrue(se.get(StudentExam_.STARTED))));
            Predicate hasStarted = builder.exists(sub);
            Predicate checkIncomplete = builder.or(builder.isNull(root.get(ExamUser_.SIGNING_IMAGE_PATH)), builder.equal(root.get(ExamUser_.SIGNING_IMAGE_PATH), ""),
                    builder.isFalse(root.get(ExamUser_.DID_CHECK_IMAGE)), builder.isFalse(root.get(ExamUser_.DID_CHECK_NAME)), builder.isFalse(root.get(ExamUser_.DID_CHECK_LOGIN)),
                    builder.isFalse(root.get(ExamUser_.DID_CHECK_REGISTRATION_NUMBER)));
            return builder.and(hasStarted, checkIncomplete);
        };
    }

    /** Matches exam users who never started the exam: either no student exam was generated (ExamMissing) or the student exam was not started (NotStarted). */
    @NonNull
    private static Specification<ExamUser> isDidNotAttend() {
        return Specification.where(isExamMissing()).or(isNotStarted());
    }

    /**
     * Filter by exam id.
     *
     * @param examId the id of the exam to filter for
     * @return specification that restricts results to the given exam
     */
    @NonNull
    public static Specification<ExamUser> forExam(long examId) {
        return (root, query, builder) -> builder.equal(root.get(ExamUser_.EXAM).get(DomainObject_.ID), examId);
    }

    /**
     * Case-insensitive search across {@code user.login} and the concatenated full name ({@code firstName + ' ' + lastName}).
     * Comma-separated terms are split and ORed: a match on any token is sufficient.
     * Returns a no-op predicate when the search term is blank or contains only blank tokens.
     *
     * @param searchTerm the text to search for; may be {@code null} or blank
     * @return specification that matches exam users whose login or full name contains the search term
     */
    @NonNull
    public static Specification<ExamUser> searchByUserFields(@Nullable String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return noOp();
        }
        List<String> tokens = Arrays.stream(searchTerm.split(",")).map(String::trim).filter(t -> !t.isBlank()).toList();
        if (tokens.isEmpty()) {
            return noOp();
        }
        return (root, query, builder) -> {
            Join<ExamUser, User> userJoin = root.join(ExamUser_.USER, JoinType.LEFT);
            Expression<String> fullName = builder
                    .lower(builder.concat(builder.concat(builder.coalesce(userJoin.get(User_.FIRST_NAME), ""), " "), builder.coalesce(userJoin.get(User_.LAST_NAME), "")));
            List<Predicate> tokenPredicates = tokens.stream().map(token -> {
                String pattern = "%" + escapeForLike(token) + "%";
                return builder.or(builder.like(builder.lower(userJoin.get(User_.LOGIN)), pattern, '\\'), builder.like(fullName, pattern, '\\'));
            }).toList();
            return builder.or(tokenPredicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Applies whitelisted ORDER BY for the paginated exam-students query, always followed by {@code eu.id ASC}
     * as a stable tiebreaker. Sorting by {@code workingTime} uses a correlated subquery against the matching
     * non-test-run {@link StudentExam}.
     *
     * @param sortedColumn the column key from the frontend; unrecognised keys fall through to the default (by name)
     * @param sortOrder    ascending or descending; null is treated as ascending
     * @return specification that sets the query's ORDER BY, or a no-op on count queries
     */
    @NonNull
    public static Specification<ExamUser> ordered(@Nullable String sortedColumn, @Nullable SortingOrder sortOrder) {
        return (root, query, builder) -> {
            if (query == null || Long.class.equals(query.getResultType())) {
                return null;
            }
            boolean asc = sortOrder != SortingOrder.DESCENDING;
            List<Order> orders = new ArrayList<>();
            Path<User> user = root.get(ExamUser_.USER);
            String column = sortedColumn == null ? "" : sortedColumn;

            switch (column) {
                case "login" -> {
                    Expression<String> expr = builder.lower(user.get(User_.LOGIN));
                    orders.add(asc ? builder.asc(expr) : builder.desc(expr));
                }
                case "visibleRegistrationNumber" -> {
                    Expression<String> expr = user.get(User_.REGISTRATION_NUMBER);
                    orders.add(asc ? builder.asc(expr) : builder.desc(expr));
                }
                case "plannedRoom" -> {
                    Expression<String> expr = root.get(ExamUser_.PLANNED_ROOM);
                    orders.add(asc ? builder.asc(expr) : builder.desc(expr));
                }
                case "plannedSeat" -> {
                    Expression<String> expr = root.get(ExamUser_.PLANNED_SEAT);
                    orders.add(asc ? builder.asc(expr) : builder.desc(expr));
                }
                case "actualRoom" -> {
                    // Sort by the displayed room: actual room when set, otherwise fall back to the planned room
                    Expression<String> expr = builder.coalesce(root.get(ExamUser_.ACTUAL_ROOM), root.get(ExamUser_.PLANNED_ROOM));
                    orders.add(asc ? builder.asc(expr) : builder.desc(expr));
                }
                case "actualSeat" -> {
                    // Sort by the displayed seat: actual seat when set, otherwise fall back to the planned seat
                    Expression<String> expr = builder.coalesce(root.get(ExamUser_.ACTUAL_SEAT), root.get(ExamUser_.PLANNED_SEAT));
                    orders.add(asc ? builder.asc(expr) : builder.desc(expr));
                }
                case "workingTime" -> {
                    Subquery<Integer> sub = query.subquery(Integer.class);
                    Root<StudentExam> se = sub.from(StudentExam.class);
                    sub.select(se.get(StudentExam_.WORKING_TIME));
                    sub.where(builder.and(builder.equal(se.get(StudentExam_.USER), user), builder.equal(se.get(StudentExam_.EXAM), root.get(ExamUser_.EXAM)),
                            builder.or(builder.isNull(se.get(StudentExam_.TEST_RUN)), builder.isFalse(se.get(StudentExam_.TEST_RUN)))));
                    orders.add(asc ? builder.asc(sub) : builder.desc(sub));
                }
                case "numberOfExamSessions" -> {
                    Subquery<Long> sub = query.subquery(Long.class);
                    Root<ExamSession> sess = sub.from(ExamSession.class);
                    Path<StudentExam> se = sess.get("studentExam");
                    sub.select(builder.count(sess));
                    sub.where(builder.and(builder.equal(se.get(StudentExam_.USER), user), builder.equal(se.get(StudentExam_.EXAM), root.get(ExamUser_.EXAM)),
                            builder.or(builder.isNull(se.get(StudentExam_.TEST_RUN)), builder.isFalse(se.get(StudentExam_.TEST_RUN)))));
                    orders.add(asc ? builder.asc(sub) : builder.desc(sub));
                }
                case "name" -> {
                    Expression<String> first = builder.lower(builder.coalesce(user.get(User_.FIRST_NAME), ""));
                    Expression<String> last = builder.lower(builder.coalesce(user.get(User_.LAST_NAME), ""));
                    orders.add(asc ? builder.asc(first) : builder.desc(first));
                    orders.add(asc ? builder.asc(last) : builder.desc(last));
                }
                case "progress" -> {
                    Subquery<String> sub = query.subquery(String.class);
                    Root<StudentExam> se = sub.from(StudentExam.class);
                    sub.select(builder.<String>selectCase().when(builder.isTrue(se.get(StudentExam_.SUBMITTED)), ExamStudentDTO.PROGRESS_SUBMITTED)
                            .when(builder.isTrue(se.get(StudentExam_.STARTED)), ExamStudentDTO.PROGRESS_STARTED).otherwise(ExamStudentDTO.PROGRESS_NOT_STARTED));
                    sub.where(builder.and(builder.equal(se.get(StudentExam_.USER), user), builder.equal(se.get(StudentExam_.EXAM), root.get(ExamUser_.EXAM)),
                            builder.or(builder.isNull(se.get(StudentExam_.TEST_RUN)), builder.isFalse(se.get(StudentExam_.TEST_RUN)))));
                    Expression<String> progressExpr = builder.coalesce(sub, ExamStudentDTO.PROGRESS_EXAM_MISSING);
                    orders.add(asc ? builder.asc(progressExpr) : builder.desc(progressExpr));
                }
                default -> {
                    // no custom ordering
                }
            }
            // Stable tiebreaker
            orders.add(builder.asc(root.get(DomainObject_.ID)));
            query.orderBy(orders);
            return null;
        };
    }
}
