package de.tum.in.www1.artemis.config;

import java.util.regex.Pattern;

/**
 * Application constants.
 */
public final class Constants {

    // NOTE: those 4 values have to be the same as in app.constants.ts
    public static final int USERNAME_MIN_LENGTH = 4;

    public static final int USERNAME_MAX_LENGTH = 50;

    public static final int PASSWORD_MIN_LENGTH = 8;

    public static final int PASSWORD_MAX_LENGTH = 50;

    public static int COMPLAINT_LOCK_DURATION_IN_MINUTES = 1440; // 24h

    public static int SECONDS_BEFORE_RELEASE_DATE_FOR_COMBINING_TEMPLATE_COMMITS = 15;

    public static int SECONDS_AFTER_RELEASE_DATE_FOR_UNLOCKING_STUDENT_EXAM_REPOS = 5;

    // Regex for acceptable logins
    public static final String LOGIN_REGEX = "^[_'.@A-Za-z0-9-]*$";

    public static final String SYSTEM_ACCOUNT = "system";

    public static final String DEFAULT_LANGUAGE = "en";

    public static final int QUIZ_GRACE_PERIOD_IN_SECONDS = 5;

    public static final String FILEPATH_ID_PLACEHOLDER = "PLACEHOLDER_FOR_ID";

    public static final String EXERCISE_TOPIC_ROOT = "/topic/exercise/";

    public static final String NEW_RESULT_TOPIC = "/topic/newResults";

    public static final String NEW_RESULT_RESOURCE_PATH = "programming-exercises/new-result";

    public static final String NEW_RESULT_RESOURCE_API_PATH = "/api/" + NEW_RESULT_RESOURCE_PATH;

    public static final String TEST_CASE_CHANGED_PATH = "/programming-exercises/test-cases-changed/";

    public static final String TEST_CASE_CHANGED_API_PATH = "/api" + TEST_CASE_CHANGED_PATH;

    public static final String PROGRAMMING_SUBMISSION_RESOURCE_PATH = "/programming-submissions/";

    public static final String PROGRAMMING_SUBMISSION_RESOURCE_API_PATH = "/api" + PROGRAMMING_SUBMISSION_RESOURCE_PATH;

    public static final String ATHENE_RESULT_PATH = "/athene-result/";

    public static final String ATHENE_RESULT_API_PATH = "/api" + ATHENE_RESULT_PATH;

    public static final String SYSTEM_NOTIFICATIONS_RESOURCE_PATH = "/system-notifications/";

    public static final String SYSTEM_NOTIFICATIONS_RESOURCE_PATH_ACTIVE_API_PATH = "/api" + SYSTEM_NOTIFICATIONS_RESOURCE_PATH + "active";

    public static final String PROGRAMMING_SUBMISSION_TOPIC = "/newSubmissions";

    public static final String NEW_SUBMISSION_TOPIC = "/topic" + PROGRAMMING_SUBMISSION_TOPIC;

    public static final String APOLLON_CONVERSION_API_PATH = "/api/apollon-convert/pdf";

    // short names should have at least 3 characters and must start with a letter
    public static final String SHORT_NAME_REGEX = "^[a-zA-Z][a-zA-Z0-9]{2,}";

    public static final Pattern SHORT_NAME_PATTERN = Pattern.compile(SHORT_NAME_REGEX);

    public static final String FILE_ENDING_REGEX = "^[a-zA-Z0-9]{1,5}";

    public static final Pattern FILE_ENDING_PATTERN = Pattern.compile(FILE_ENDING_REGEX);

    public static final Pattern TITLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\s]*");

    public static final String TUM_LDAP_MATRIKEL_NUMBER = "imMatrikelNr";

    // NOTE: the following values for programming exercises are hard-coded at the moment
    public static final String TEST_REPO_NAME = "tests";

    public static final String ASSIGNMENT_REPO_NAME = "assignment";

    public static final String SOLUTION_REPO_NAME = "solution";

    // Used to cut off CI specific path segments when receiving static code analysis reports
    public static final String ASSIGNMENT_DIRECTORY = "/" + ASSIGNMENT_REPO_NAME + "/";

    // Used as a value for <sourceDirectory> for the Java template pom.xml
    public static final String STUDENT_WORKING_DIRECTORY = ASSIGNMENT_DIRECTORY + "src";

    // TODO: the following numbers should be configurable in the yml files

    public static final long MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR = 10;

    // Note: The values in input.constants.ts (client) need to be the same
    public static final long MAX_SUBMISSION_FILE_SIZE = 8 * 1024 * 1024; // 8 MB

    // Note: The values in input.constants.ts (client) need to be the same
    public static final int MAX_SUBMISSION_TEXT_LENGTH = 30 * 1000; // 30.000 characters

    public static final int MAX_SUBMISSION_MODEL_LENGTH = 100 * 1000; // 100.000 characters

    public static final int MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH = 255; // Must be consistent with database column definition

    public static final String TEST_CASES_DUPLICATE_NOTIFICATION = "There are duplicated test cases in this programming exercise. All test cases have to be unique and cannot have the same name. The following test cases are duplicated: ";

    public static final String TEST_CASES_CHANGED_RUN_COMPLETED_NOTIFICATION = "Build and Test run complete. New results were created for the programming exercise's student submissions with the updated test case settings.";

    public static final String BUILD_RUN_STARTED_FOR_PROGRAMMING_EXERCISE = "Build run triggered for programming exercise";

    public static final String BUILD_RUN_COMPLETE_FOR_PROGRAMMING_EXERCISE = "All builds triggered for programming exercise";

    public static final String PROGRAMMING_EXERCISE_FAILED_LOCK_OPERATIONS_NOTIFICATION = "When removing the write permissions for the student repositories, not all operations were successful. Number of failed operations: ";

    public static final String PROGRAMMING_EXERCISE_FAILED_STASH_OPERATIONS_NOTIFICATION = "When stashing the changes for the student repositories, not all operations were successful. Number of failed operations: ";

    public static final String PROGRAMMING_EXERCISE_SUCCESSFUL_LOCK_OPERATION_NOTIFICATION = "The student repositories for this programming exercise were locked successfully.";

    public static final String PROGRAMMING_EXERCISE_SUCCESSFUL_STASH_OPERATION_NOTIFICATION = "The unsubmitted changes in the student repositories for this programming exercise were stashed successfully.";

    public static final String PROGRAMMING_EXERCISE_FAILED_UNLOCK_OPERATIONS_NOTIFICATION = "When adding the write permissions for the student repositories, not all operations were successful. Number of failed operations: ";

    public static final String PROGRAMMING_EXERCISE_SUCCESSFUL_UNLOCK_OPERATION_NOTIFICATION = "The student repositories for this programming exercise were unlocked successfully.";

    public static final int FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS = 5000;

    // This value limits the amount of characters allowed for a complaint response text.
    // Set to 65535 as the db-column has type TEXT which can hold up to 65535 characters.
    // Also, the value on the client side must match this value.
    public static final int COMPLAINT_RESPONSE_TEXT_LIMIT = 65535;

    // This value limits the amount of characters allowed for a complaint text.
    // Set to 65535 as the db-column has type TEXT which can hold up to 65535 characters.
    // Also, the value on the client side must match this value.
    public static final int COMPLAINT_TEXT_LIMIT = 65535;

    public static final String ASSIGNMENT_CHECKOUT_PATH = "assignment";

    public static final String TESTS_CHECKOUT_PATH = "tests";

    public static final String SOLUTION_CHECKOUT_PATH = "solution";

    public static final String SETUP_COMMIT_MESSAGE = "Setup";

    public static final String REGISTER_FOR_COURSE = "REGISTER_FOR_COURSE";

    public static final String DELETE_EXERCISE = "DELETE_EXERCISE";

    public static final String EDIT_EXERCISE = "EDIT_EXERCISE";

    public static final String DELETE_COURSE = "DELETE_COURSE";

    public static final String DELETE_EXAM = "DELETE_EXAM";

    public static final String ADD_USER_TO_EXAM = "ADD_USER_TO_EXAM";

    public static final String REMOVE_USER_FROM_EXAM = "REMOVE_USER_FROM_EXAM";

    public static final String REMOVE_ALL_USERS_FROM_EXAM = "REMOVE_ALL_USERS_FROM_EXAM";

    public static final String RESET_EXAM = "RESET_EXAM";

    // same constant as in the client
    public static final int EXAM_START_WAIT_TIME_MINUTES = 5;

    public static final int EXAM_END_WAIT_TIME_FOR_COMPASS_MINUTES = 1;

    public static final String TOGGLE_STUDENT_EXAM_SUBMITTED = "TOGGLE_STUDENT_EXAM_SUBMITTED";

    public static final String TOGGLE_STUDENT_EXAM_UNSUBMITTED = "TOGGLE_STUDENT_EXAM_UNSUBMITTED";

    public static final String PREPARE_EXERCISE_START = "PREPARE_EXERCISE_START";

    public static final String GENERATE_STUDENT_EXAMS = "GENERATE_STUDENT_EXAMS";

    public static final String GENERATE_MISSING_STUDENT_EXAMS = "GENERATE_MISSING_STUDENT_EXAMS";

    public static final String DELETE_PARTICIPATION = "DELETE_PARTICIPATION";

    public static final String DELETE_TEAM = "DELETE_TEAM";

    public static final String DELETE_EXERCISE_GROUP = "DELETE_EXERCISE_GROUP";

    public static final String IMPORT_TEAMS = "IMPORT_TEAMS";

    public static final String RE_EVALUATE_RESULTS = "RE_EVALUATE_RESULTS";

    public static final String RESET_GRADING = "RESET_GRADING";

    public static final String TRIGGER_INSTRUCTOR_BUILD = "TRIGGER_INSTRUCTOR_BUILD";

    public static final String INFO_BUILD_PLAN_URL_DETAIL = "buildPlanURLTemplate";

    public static final String INFO_COMMIT_HASH_URL_DETAIL = "commitHashURLTemplate";

    public static final String INFO_SSH_CLONE_URL_DETAIL = "sshCloneURLTemplate";

    public static final String INFO_SSH_KEYS_URL_DETAIL = "sshKeysURL";

    public static final String INFO_VERSION_CONTROL_ACCESS_TOKEN_DETAIL = "versionControlAccessToken";

    public static final String EXTERNAL_USER_MANAGEMENT_URL = "externalUserManagementURL";

    public static final String EXTERNAL_USER_MANAGEMENT_NAME = "externalUserManagementName";

    public static final String REGISTRATION_ENABLED = "registrationEnabled";

    public static final String NEEDS_TO_ACCEPT_TERMS = "needsToAcceptTerms";

    public static final String ALLOWED_EMAIL_PATTERN = "allowedEmailPattern";

    public static final String ALLOWED_EMAIL_PATTERN_READABLE = "allowedEmailPatternReadable";

    public static final String ALLOWED_LDAP_USERNAME_PATTERN = "allowedLdapUsernamePattern";

    public static final String ACCOUNT_NAME = "accountName";

    public static final String ALLOWED_COURSE_REGISTRATION_USERNAME_PATTERN = "allowedCourseRegistrationUsernamePattern";

    public static final String ARTEMIS_GROUP_DEFAULT_PREFIX = "artemis-";

    public static final String HAZELCAST_QUIZ_SCHEDULER = "quizScheduleServiceExecutor";

    public static final String HAZELCAST_QUIZ_PREFIX = "quiz-";

    public static final String HAZELCAST_EXERCISE_CACHE = HAZELCAST_QUIZ_PREFIX + "exercise-cache";

    public static final long MONITORING_CACHE_RESET_DELAY = 60 * 30; // 30 minutes in seconds

    public static final String HAZELCAST_MONITORING_PREFIX = "monitoring-";

    public static final String HAZELCAST_MONITORING_CACHE = HAZELCAST_MONITORING_PREFIX + "activity-cache";

    public static final int HAZELCAST_MONITORING_CACHE_SERIALIZER_ID = 2;

    public static final int HAZELCAST_QUIZ_EXERCISE_CACHE_SERIALIZER_ID = 1;

    public static final String HAZELCAST_PLAGIARISM_PREFIX = "plagiarism-";

    public static final String HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE = HAZELCAST_PLAGIARISM_PREFIX + "active-plagiarism-checks-per-course-cache";

    public static final String VERSION_CONTROL_URL = "versionControlUrl";

    public static final String USE_EXTERNAL = "useExternal";

    public static final String EXTERNAL_CREDENTIAL_PROVIDER = "externalCredentialProvider";

    public static final String EXTERNAL_PASSWORD_RESET_LINK_MAP = "externalPasswordResetLinkMap";

    public static final String VOTE_EMOJI_ID = "heavy_plus_sign";

    public static final String EXAM_EXERCISE_START_STATUS = "exam-exercise-start-status";

    /**
     * Size of an unsigned tinyInt in SQL, that is used in the database
     */
    public static final int SIZE_OF_UNSIGNED_TINYINT = 255;

    private Constants() {
    }
}
