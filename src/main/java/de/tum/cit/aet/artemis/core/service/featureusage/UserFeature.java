package de.tum.cit.aet.artemis.core.service.featureusage;

/**
 * The catalogue of Artemis features as users know them, which the admin feature usage page reports on.
 * <p>
 * A feature here is something a student, tutor, instructor or administrator recognises and would name ("Online code
 * editor", "Take an exam", "AI problem statement generation"), not a controller or a code module. Every REST endpoint is
 * assigned to exactly one feature with {@link FeatureUsage}, and one feature is usually served by several endpoints,
 * often across several modules: the online code editor spans {@code programming}, {@code localci} and
 * {@code localvc}. The page groups features by their {@link ProductArea} and shows the modules and resources behind
 * each one as the level below.
 * <p>
 * Features that no endpoint serves are recorded explicitly through {@link FeatureUsageCollector}: git operations and
 * server-side work such as notification delivery. {@code FeatureUsageAnnotationTest} fails for a constant that is
 * neither annotated on an endpoint nor referenced by such a call site, so the catalogue cannot collect dead entries.
 * <p>
 * The constant name is what the inventory stores as the feature label. Moving an endpoint to another constant is safe at
 * any time: the label is re-derived on every startup, and historic counts regroup under the new feature immediately. The
 * same holds for renaming a constant, with one exception: a row that is no longer offered is never re-registered, so it
 * keeps the old name and its history is no longer attributed to the feature. Display names and descriptions live in the client translations
 * ({@code artemisApp.featureUsage.catalog.feature.*}); the test fails when one is missing.
 */
public enum UserFeature {

    // Courses
    COURSE_DASHBOARD(ProductArea.COURSES), COURSE_OVERVIEW(ProductArea.COURSES), COURSE_ENROLLMENT(ProductArea.COURSES), STUDENT_COURSE_STATISTICS(ProductArea.COURSES),
    CALENDAR(ProductArea.COURSES), GLOBAL_SEARCH(ProductArea.COURSES),

    // Course management
    COURSE_CREATION(ProductArea.COURSE_MANAGEMENT), COURSE_MANAGEMENT_OVERVIEW(ProductArea.COURSE_MANAGEMENT), COURSE_SETTINGS(ProductArea.COURSE_MANAGEMENT),
    COURSE_MEMBERS(ProductArea.COURSE_MANAGEMENT), COURSE_MATERIAL_IMPORT(ProductArea.COURSE_MANAGEMENT), COURSE_STATISTICS(ProductArea.COURSE_MANAGEMENT),
    COURSE_SCORES(ProductArea.COURSE_MANAGEMENT), COURSE_ARCHIVE(ProductArea.COURSE_MANAGEMENT), COURSE_RESET_DELETE(ProductArea.COURSE_MANAGEMENT),
    MARKDOWN_UPLOADS(ProductArea.COURSE_MANAGEMENT),

    // Exercises
    EXERCISE_DETAILS(ProductArea.EXERCISES), EXERCISE_PARTICIPATION(ProductArea.EXERCISES), EXERCISE_FEEDBACK(ProductArea.EXERCISES), TEAM_EXERCISES(ProductArea.EXERCISES),
    EXERCISE_VARIANT_GROUPS(ProductArea.EXERCISES), EXERCISE_MANAGEMENT(ProductArea.EXERCISES), EXERCISE_VERSION_HISTORY(ProductArea.EXERCISES),
    EXERCISE_REVIEW(ProductArea.EXERCISES), EXERCISE_PARTICIPATIONS_STAFF(ProductArea.EXERCISES), EXERCISE_SCORES(ProductArea.EXERCISES),

    // Programming exercises
    PROGRAMMING_ONLINE_EDITOR(ProductArea.PROGRAMMING), PROGRAMMING_LOCAL_IDE(ProductArea.PROGRAMMING), PROGRAMMING_ONLINE_IDE(ProductArea.PROGRAMMING),
    PROGRAMMING_REPOSITORY_HISTORY(ProductArea.PROGRAMMING), PROGRAMMING_RESULTS(ProductArea.PROGRAMMING), PROGRAMMING_AUTHORING(ProductArea.PROGRAMMING),
    PROGRAMMING_REPOSITORY_EDITING(ProductArea.PROGRAMMING), PROGRAMMING_BUILD_CONFIGURATION(ProductArea.PROGRAMMING), PROGRAMMING_GRADING_CONFIGURATION(ProductArea.PROGRAMMING),
    PROGRAMMING_REEVALUATION(ProductArea.PROGRAMMING), PROGRAMMING_SUBMISSION_POLICY(ProductArea.PROGRAMMING), PROGRAMMING_CONSISTENCY_CHECK(ProductArea.PROGRAMMING),
    PROGRAMMING_IMPORT_EXPORT(ProductArea.PROGRAMMING),

    // Quizzes
    QUIZ_LIVE(ProductArea.QUIZ), QUIZ_PRACTICE(ProductArea.QUIZ), QUIZ_TRAINING(ProductArea.QUIZ), QUIZ_AUTHORING(ProductArea.QUIZ), QUIZ_LIFECYCLE(ProductArea.QUIZ),
    QUIZ_EVALUATION(ProductArea.QUIZ),

    // Text, modeling and file upload exercises
    TEXT_EXERCISES(ProductArea.TEXT_MODELING_UPLOAD), TEXT_AUTHORING(ProductArea.TEXT_MODELING_UPLOAD), MODELING_EXERCISES(ProductArea.TEXT_MODELING_UPLOAD),
    MODELING_AUTHORING(ProductArea.TEXT_MODELING_UPLOAD), FILE_UPLOAD_EXERCISES(ProductArea.TEXT_MODELING_UPLOAD), FILE_UPLOAD_AUTHORING(ProductArea.TEXT_MODELING_UPLOAD),

    // Assessment and grading
    ASSESSMENT_DASHBOARD(ProductArea.ASSESSMENT), MANUAL_ASSESSMENT(ProductArea.ASSESSMENT), COMPLAINTS(ProductArea.ASSESSMENT), FEEDBACK_RATINGS(ProductArea.ASSESSMENT),
    TUTOR_TRAINING(ProductArea.ASSESSMENT), TUTOR_EFFORT(ProductArea.ASSESSMENT), GRADING_KEYS(ProductArea.ASSESSMENT), BONUS(ProductArea.ASSESSMENT),
    RESULTS_AND_FEEDBACK_ANALYSIS(ProductArea.ASSESSMENT),

    // Exams
    EXAM_TAKE(ProductArea.EXAMS), EXAM_TEST_EXAMS(ProductArea.EXAMS), EXAM_RESULTS(ProductArea.EXAMS), EXAM_AUTHORING(ProductArea.EXAMS), EXAM_REGISTRATION(ProductArea.EXAMS),
    EXAM_ROOMS(ProductArea.EXAMS), EXAM_ATTENDANCE(ProductArea.EXAMS), EXAM_CONDUCTION_MANAGEMENT(ProductArea.EXAMS), EXAM_SUSPICIOUS_BEHAVIOR(ProductArea.EXAMS),
    EXAM_SCORES(ProductArea.EXAMS), EXAM_ARCHIVE(ProductArea.EXAMS),

    // Lectures
    LECTURE_PAGES(ProductArea.LECTURES), LECTURE_UNITS(ProductArea.LECTURES), LECTURE_VIDEO(ProductArea.LECTURES), LECTURE_AUTHORING(ProductArea.LECTURES),
    LECTURE_SLIDE_PROCESSING(ProductArea.LECTURES), LECTURE_CONTENT_PROCESSING(ProductArea.LECTURES),

    // Communication
    MESSAGING(ProductArea.COMMUNICATION), CHANNELS(ProductArea.COMMUNICATION), DIRECT_MESSAGES(ProductArea.COMMUNICATION), CONVERSATION_ORGANIZATION(ProductArea.COMMUNICATION),
    MESSAGE_INTERACTIONS(ProductArea.COMMUNICATION), LINK_PREVIEWS(ProductArea.COMMUNICATION), FAQ(ProductArea.COMMUNICATION),

    // Notifications
    COURSE_NOTIFICATIONS(ProductArea.NOTIFICATIONS), NOTIFICATION_DELIVERY(ProductArea.NOTIFICATIONS), NOTIFICATION_SETTINGS(ProductArea.NOTIFICATIONS),
    SYSTEM_NOTIFICATIONS(ProductArea.NOTIFICATIONS),

    // Tutorial groups
    TUTORIAL_GROUPS(ProductArea.TUTORIAL_GROUPS), TUTORIAL_GROUP_MANAGEMENT(ProductArea.TUTORIAL_GROUPS), TUTORIAL_GROUP_SESSIONS(ProductArea.TUTORIAL_GROUPS),

    // Competencies and learning paths
    COMPETENCY_PROGRESS(ProductArea.COMPETENCIES), COMPETENCY_MANAGEMENT(ProductArea.COMPETENCIES), STANDARDIZED_COMPETENCIES(ProductArea.COMPETENCIES),
    LEARNING_PATHS(ProductArea.COMPETENCIES), LEARNING_PATH_MANAGEMENT(ProductArea.COMPETENCIES), LEARNER_PROFILE(ProductArea.COMPETENCIES), SCIENCE(ProductArea.COMPETENCIES),

    // AI for learners
    IRIS_CHAT(ProductArea.AI_LEARNERS), IRIS_MEMORY(ProductArea.AI_LEARNERS), IRIS_STRUGGLE_INTERVENTION(ProductArea.AI_LEARNERS), IRIS_SEARCH_ANSWER(ProductArea.AI_LEARNERS),
    IRIS_SETTINGS(ProductArea.AI_LEARNERS), IRIS_CONTENT_INGESTION(ProductArea.AI_LEARNERS), AI_FEEDBACK_REQUEST(ProductArea.AI_LEARNERS),

    // AI for instructors and tutors
    HYPERION_PROBLEM_STATEMENT(ProductArea.AI_AUTHORING), HYPERION_CHECKLIST(ProductArea.AI_AUTHORING), HYPERION_CONSISTENCY_CHECK(ProductArea.AI_AUTHORING),
    HYPERION_CODE_GENERATION(ProductArea.AI_AUTHORING), HYPERION_EXERCISE_GENERATION(ProductArea.AI_AUTHORING), HYPERION_GENERATION_MONITORING(ProductArea.AI_AUTHORING),
    HYPERION_GENERATION_RECOVERY(ProductArea.AI_AUTHORING), HYPERION_VARIANT_GENERATION(ProductArea.AI_AUTHORING), HYPERION_QUIZ_GENERATION(ProductArea.AI_AUTHORING),
    HYPERION_ASSESSMENT_CRITERIA(ProductArea.AI_AUTHORING), HYPERION_FAQ_REWRITE(ProductArea.AI_AUTHORING), ATHENA_FEEDBACK_SUGGESTIONS(ProductArea.AI_AUTHORING),
    IRIS_TUTOR_SUGGESTIONS(ProductArea.AI_AUTHORING), ATLAS_AGENT(ProductArea.AI_AUTHORING), COMPETENCY_ORCHESTRATION(ProductArea.AI_AUTHORING),
    AI_COMPETENCY_GENERATION(ProductArea.AI_AUTHORING),

    // Academic integrity
    PLAGIARISM_CHECKS(ProductArea.INTEGRITY), CONTINUOUS_PLAGIARISM_CONTROL(ProductArea.INTEGRITY), PLAGIARISM_CASES(ProductArea.INTEGRITY), DEIMOS(ProductArea.INTEGRITY),

    // Account and security
    SIGN_IN(ProductArea.ACCOUNT), REGISTRATION_PASSWORD(ProductArea.ACCOUNT), ACCOUNT_SETTINGS(ProductArea.ACCOUNT), PASSKEYS(ProductArea.ACCOUNT),
    GIT_CREDENTIALS(ProductArea.ACCOUNT), PERSONAL_DATA_EXPORT(ProductArea.ACCOUNT),

    // Integrations
    LTI(ProductArea.INTEGRATIONS), SHARING_PLATFORM(ProductArea.INTEGRATIONS), MOBILE_APPS(ProductArea.INTEGRATIONS), VS_CODE_EXTENSION(ProductArea.INTEGRATIONS),

    // Build system
    BUILD_OVERVIEW(ProductArea.BUILD_SYSTEM), BUILD_AGENTS(ProductArea.BUILD_SYSTEM), AI_WORKERS(ProductArea.BUILD_SYSTEM),

    // Administration and operations
    USER_MANAGEMENT(ProductArea.ADMINISTRATION), ORGANIZATIONS(ProductArea.ADMINISTRATION), FEATURE_TOGGLES(ProductArea.ADMINISTRATION), MONITORING(ProductArea.ADMINISTRATION),
    USAGE_STATISTICS(ProductArea.ADMINISTRATION), FEATURE_USAGE(ProductArea.ADMINISTRATION), DATA_CLEANUP(ProductArea.ADMINISTRATION),
    ADMIN_DATA_EXPORTS(ProductArea.ADMINISTRATION), LEGAL_PAGES(ProductArea.ADMINISTRATION), DEPENDENCIES(ProductArea.ADMINISTRATION),
    IRIS_ADMIN_DASHBOARD(ProductArea.ADMINISTRATION), UPCOMING_EXAMS_AND_EXERCISES(ProductArea.ADMINISTRATION);

    private final ProductArea area;

    UserFeature(ProductArea area) {
        this.area = area;
    }

    /**
     * The product area the feature is listed under.
     *
     * @return the area of this feature
     */
    public ProductArea getArea() {
        return area;
    }
}
