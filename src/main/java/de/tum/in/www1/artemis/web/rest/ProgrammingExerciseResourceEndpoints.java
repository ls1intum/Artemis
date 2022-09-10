package de.tum.in.www1.artemis.web.rest;

public final class ProgrammingExerciseResourceEndpoints {

    public static final String ROOT = "/api";

    public static final String PROGRAMMING_EXERCISES = "/programming-exercises";

    public static final String SETUP = PROGRAMMING_EXERCISES + "/setup";

    public static final String GET_FOR_COURSE = "/courses/{courseId}/programming-exercises";

    public static final String IMPORT = PROGRAMMING_EXERCISES + "/import/{sourceExerciseId}";

    public static final String PROGRAMMING_EXERCISE = PROGRAMMING_EXERCISES + "/{exerciseId}";

    public static final String PROBLEM = PROGRAMMING_EXERCISE + "/problem-statement";

    public static final String TIMELINE = PROGRAMMING_EXERCISES + "/timeline";

    public static final String PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS = PROGRAMMING_EXERCISE + "/with-participations";

    public static final String PROGRAMMING_EXERCISE_WITH_TEMPLATE_AND_SOLUTION_PARTICIPATION = PROGRAMMING_EXERCISE + "/with-template-and-solution-participation";

    public static final String COMBINE_COMMITS = PROGRAMMING_EXERCISE + "/combine-template-commits";

    public static final String EXPORT_SUBMISSIONS_BY_PARTICIPANTS = PROGRAMMING_EXERCISE + "/export-repos-by-participant-identifiers/{participantIdentifiers}";

    public static final String EXPORT_SUBMISSIONS_BY_PARTICIPATIONS = PROGRAMMING_EXERCISE + "/export-repos-by-participation-ids/{participationIds}";

    public static final String EXPORT_INSTRUCTOR_EXERCISE = PROGRAMMING_EXERCISE + "/export-instructor-exercise";

    public static final String EXPORT_INSTRUCTOR_REPOSITORY = PROGRAMMING_EXERCISE + "/export-instructor-repository/{repositoryType}";

    public static final String EXPORT_INSTRUCTOR_AUXILIARY_REPOSITORY = PROGRAMMING_EXERCISE + "/export-instructor-auxiliary-repository/{repositoryId}";

    public static final String GENERATE_TESTS = PROGRAMMING_EXERCISE + "/generate-tests";

    public static final String CHECK_PLAGIARISM = PROGRAMMING_EXERCISE + "/check-plagiarism";

    public static final String PLAGIARISM_RESULT = PROGRAMMING_EXERCISE + "/plagiarism-result";

    public static final String CHECK_PLAGIARISM_JPLAG_REPORT = PROGRAMMING_EXERCISE + "/check-plagiarism-jplag-report";

    public static final String TEST_CASE_STATE = PROGRAMMING_EXERCISE + "/test-case-state";

    public static final String UNLOCK_ALL_REPOSITORIES = PROGRAMMING_EXERCISE + "/unlock-all-repositories";

    public static final String LOCK_ALL_REPOSITORIES = PROGRAMMING_EXERCISE + "/lock-all-repositories";

    public static final String AUXILIARY_REPOSITORY = PROGRAMMING_EXERCISE + "/auxiliary-repository";

    public static final String RECREATE_BUILD_PLANS = PROGRAMMING_EXERCISE + "/recreate-build-plans";

    public static final String REEVALUATE_EXERCISE = PROGRAMMING_EXERCISE + "/re-evaluate";

    public static final String TASKS = PROGRAMMING_EXERCISE + "/tasks";

    public static final String EXPORT_SOLUTION_REPOSITORY = PROGRAMMING_EXERCISE + "/export-solution-repository";

    public static final String SOLUTION_REPOSITORY_FILES_WITH_CONTENT = PROGRAMMING_EXERCISE + "/solution-files-content";

    public static final String TEMPLATE_REPOSITORY_FILES_WITH_CONTENT = PROGRAMMING_EXERCISE + "/template-files-content";

    public static final String SOLUTION_REPOSITORY_FILE_NAMES = PROGRAMMING_EXERCISE + "/file-names";

    public static final String BUILD_LOG_STATISTICS = PROGRAMMING_EXERCISE + "/build-log-statistics";

    private ProgrammingExerciseResourceEndpoints() {
    }
}
