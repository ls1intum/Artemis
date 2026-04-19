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
     * Filter by exam id.
     */
    @NonNull
    public static Specification<ExamUser> forExam(long examId) {
        return (root, query, builder) -> builder.equal(root.get(ExamUser_.EXAM).get(DomainObject_.ID), examId);
    }

    /**
     * Case-insensitive search across {@code user.login} and the concatenated full name ({@code firstName + ' ' + lastName}).
     * Comma-separated terms are split and ORed: a match on any token is sufficient.
     * Returns a no-op predicate when the search term is blank or contains only blank tokens.
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
            if (query == null) {
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
