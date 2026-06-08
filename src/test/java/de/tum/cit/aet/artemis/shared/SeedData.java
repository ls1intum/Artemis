package de.tum.cit.aet.artemis.shared;

import java.util.List;

/**
 * References to the shared, read-only CSV seed data (see {@code config/liquibase/e2e/*.csv}). The seed is loaded once per
 * Spring context when the Liquibase {@code seed} (server tests) or {@code e2e} (Playwright) context is active, and it is
 * <b>not reset between tests</b>. The same seed is consumed by the E2E suite via {@code support/seedData.ts}.
 * <p>
 * <b>Treat every entity referenced here as read-only.</b> Because the database is not cleaned between tests within a
 * bucket, mutating a seeded entity (e.g. changing a seed user's groups or deleting a seed course) pollutes it for every
 * later test in the same bucket. Tests that need to mutate data must create their own with a unique {@code TEST_PREFIX}.
 * <p>
 * Seeded ids live in the {@code 9000+} (courses/channels) and {@code 100+} (users) ranges; the seed changelog resets the
 * sequences afterwards so runtime-created entities never collide with seeded ids.
 */
public final class SeedData {

    private SeedData() {
    }

    // ---- Users (config/liquibase/e2e/users.csv) ----

    public static final long ADMIN_ID = 100L;

    public static final String ADMIN_LOGIN = "artemis_admin";

    public static final long STUDENT_1_ID = 101L;

    public static final String STUDENT_1_LOGIN = "artemis_test_user_1";

    public static final long STUDENT_2_ID = 102L;

    public static final String STUDENT_2_LOGIN = "artemis_test_user_2";

    public static final long STUDENT_3_ID = 103L;

    public static final String STUDENT_3_LOGIN = "artemis_test_user_3";

    public static final long STUDENT_4_ID = 104L;

    public static final String STUDENT_4_LOGIN = "artemis_test_user_4";

    public static final long TUTOR_ID = 106L;

    public static final String TUTOR_LOGIN = "artemis_test_user_6";

    public static final long INSTRUCTOR_ID = 116L;

    public static final String INSTRUCTOR_LOGIN = "artemis_test_user_16";

    /**
     * Indices of the seeded students (login {@code artemis_test_user_<index>}, id {@code 100 + index}). All are enrolled
     * in {@link #COURSE_CHANNEL_1_ID}'s students group. Index 6 is the tutor and 16 the instructor, hence the gaps.
     */
    public static final List<Integer> STUDENT_INDICES = List.of(1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17, 18, 19, 20);

    /**
     * @param index the seed user index
     * @return the login of the seed user with that index ({@code artemis_test_user_<index>})
     */
    public static String userLogin(int index) {
        return "artemis_test_user_" + index;
    }

    // ---- Baseline course (config/liquibase/e2e/courses.csv) ----

    public static final long COURSE_CHANNEL_1_ID = 9001L;

    public static final String COURSE_CHANNEL_1_SHORT_NAME = "e2echannel1";

    /**
     * The raw password of a seed user equals its login (e.g. {@code artemis_test_user_1} has password
     * {@code artemis_test_user_1}). Mirrors the E2E credentials in {@code support/users.ts}, so tests can authenticate
     * as a seed user without creating one.
     *
     * @param login the seed user's login
     * @return the seed user's raw password
     */
    public static String passwordOf(String login) {
        return login;
    }
}
