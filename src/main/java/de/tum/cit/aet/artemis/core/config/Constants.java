package de.tum.cit.aet.artemis.core.config;

import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.lti.web.LtiResource;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;

/**
 * Application constants.
 */
public final class Constants {

    // NOTE: those 4 values have to be the same as in app.constants.ts
    public static final int USERNAME_MIN_LENGTH = 4;

    public static final int USERNAME_MAX_LENGTH = 50;

    public static final int PASSWORD_MIN_LENGTH = 8;

    public static final int PASSWORD_MAX_LENGTH = 100;

    public static int COMPLAINT_LOCK_DURATION_IN_MINUTES = 24 * 60; // 24h; Same as in artemisApp.locks.acquired

    public static final int SECONDS_BEFORE_RELEASE_DATE_FOR_COMBINING_TEMPLATE_COMMITS = 15;

    public static final int SECONDS_AFTER_RELEASE_DATE_FOR_UNLOCKING_STUDENT_EXAM_REPOS = 5;

    // Regex for acceptable logins
    public static final String LOGIN_REGEX = "^[_'.@A-Za-z0-9-]*$";

    public static final String SIMPLE_EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";

    public static final String SYSTEM_ACCOUNT = "system";

    public static final String DEFAULT_LANGUAGE = "en";

    public static final int QUIZ_GRACE_PERIOD_IN_SECONDS = 5;

    /**
     * This constant determines how many seconds after the exercise due dates submissions will still be considered rated.
     * Submissions after the grace period exceeded will be flagged as illegal.
     * <p>
     * This is needed because locking programming exercise repositories might take up to 60 seconds,
     * especially for exercises with many participants.
     * If the student was able to successfully push their solution, this solution should still be graded, even if
     * the push was a few seconds late.
     *
     * @see ProgrammingSubmissionService#isAllowedToSubmit(ProgrammingExerciseStudentParticipation, User, ProgrammingSubmission)
     */
    public static final int PROGRAMMING_GRACE_PERIOD_SECONDS = 60;

    public static final String FILEPATH_ID_PLACEHOLDER = "PLACEHOLDER_FOR_ID";

    public static final String EXERCISE_TOPIC_ROOT = "/topic/exercise/";

    public static final String NEW_RESULT_TOPIC = "/topic/newResults";

    public static final String NEW_RESULT_RESOURCE_API_PATH = "/api/public/programming-exercises/new-result";

    public static final String PROGRAMMING_SUBMISSION_TOPIC = "/newSubmissions";

    public static final String NEW_SUBMISSION_TOPIC = "/topic" + PROGRAMMING_SUBMISSION_TOPIC;

    public static final String ATHENA_PROGRAMMING_EXERCISE_REPOSITORY_API_PATH = "/api/public/athena/programming-exercises/";

    // short names should have at least 3 characters and must start with a letter
    public static final String SHORT_NAME_REGEX = "^[a-zA-Z][a-zA-Z0-9]{2,}";

    public static final Pattern SHORT_NAME_PATTERN = Pattern.compile(SHORT_NAME_REGEX);

    public static final String FILE_ENDING_REGEX = "^[a-zA-Z0-9]{1,5}";

    public static final Pattern FILE_ENDING_PATTERN = Pattern.compile(FILE_ENDING_REGEX);

    public static final Pattern TITLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\s]*");

    public static final String TUM_LDAP_MATRIKEL_NUMBER = "imMatrikelNr";

    public static final String TUM_LDAP_MAIN_EMAIL = "imHauptEMail";

    public static final String TUM_LDAP_EMAILS = "imEmailAdressen";

    // NOTE: the following values for programming exercises are hard-coded at the moment
    public static final String TEST_REPO_NAME = "tests";

    public static final String ASSIGNMENT_REPO_NAME = "assignment";

    public static final String SOLUTION_REPO_NAME = "solution";

    // Used to cut off CI specific path segments when receiving static code analysis reports
    public static final String ASSIGNMENT_DIRECTORY = "/" + ASSIGNMENT_REPO_NAME + "/";

    public static final String TEST_WORKING_DIRECTORY = "test";

    // Used as a value for <sourceDirectory> for the Java template pom.xml
    public static final String STUDENT_WORKING_DIRECTORY = ASSIGNMENT_DIRECTORY + "src";

    public static final String USER_FIRST_NAME_AFTER_SOFT_DELETE = "Deleted";

    public static final String USER_LAST_NAME_AFTER_SOFT_DELETE = "User";

    public static final String USER_EMAIL_DOMAIN_AFTER_SOFT_DELETE = "@user.deleted";

    // TODO: the following numbers should be configurable in the yml files

    public static final long MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR = 10;

    // Note: The values in input.constants.ts (client) need to be the same
    public static final long MAX_SUBMISSION_FILE_SIZE = 8 * 1024 * 1024; // 8 MB

    // Note: The values in input.constants.ts (client) need to be the same
    public static final int MAX_SUBMISSION_TEXT_LENGTH = 30_000; // 30.000 characters

    public static final int MAX_SUBMISSION_MODEL_LENGTH = 100_000; // 100.000 characters

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

    /**
     * Maximum length in the database for the feedback detail text.
     */
    public static final int FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH = 5000;

    /**
     * Maximum length of feedback detail texts before a long feedback is created.
     */
    public static final int FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH = 1000;

    /**
     * Maximum length for feedback detail text that is trimmed and moved to a connected long feedback instead.
     */
    public static final int FEEDBACK_PREVIEW_TEXT_MAX_LENGTH = 300;

    /**
     * Arbitrary limit that is unlikely to be reached by real feedback in practice.
     * Avoids filling the DB with huge text blobs, e.g. in case an infinite loop in a test case outputs a lot of text.
     */
    public static final int LONG_FEEDBACK_MAX_LENGTH = 10_000_000;

    // This value limits the amount of characters allowed for a complaint response text.
    // Set to 65535 as the db-column has type TEXT which can hold up to 65535 characters.
    // Also, the value on the client side must match this value.
    public static final int COMPLAINT_RESPONSE_TEXT_LIMIT = 65535;

    // This value limits the amount of characters allowed for a complaint text.
    // Set to 65535 as the db-column has type TEXT which can hold up to 65535 characters.
    // Also, the value on the client side must match this value.
    public static final int COMPLAINT_TEXT_LIMIT = 65535;

    public static final String SETUP_COMMIT_MESSAGE = "Setup";

    public static final String ENROLL_IN_COURSE = "ENROLL_IN_COURSE";

    public static final String UNENROLL_FROM_COURSE = "UNENROLL_FROM_COURSE";

    public static final String CLEANUP_COURSE = "CLEANUP_COURSE";

    public static final String CLEANUP_EXAM = "CLEANUP_EXAM";

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

    public static final String INFO_VERSION_CONTROL_ACCESS_TOKEN_DETAIL = "useVersionControlAccessToken";

    public static final String INFO_SHOW_CLONE_URL_WITHOUT_TOKEN = "showCloneUrlWithoutToken";

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

    public static final int HAZELCAST_QUIZ_EXERCISE_CACHE_SERIALIZER_ID = 1;

    public static final int HAZELCAST_PATH_SERIALIZER_ID = 2;

    public static final String HAZELCAST_PLAGIARISM_PREFIX = "plagiarism-";

    public static final String HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE = HAZELCAST_PLAGIARISM_PREFIX + "active-plagiarism-checks-per-course-cache";

    public static final String VERSION_CONTROL_URL = "versionControlUrl";

    public static final String VERSION_CONTROL_NAME = "versionControlName";

    public static final String CONTINUOUS_INTEGRATION_NAME = "continuousIntegrationName";

    public static final String INSTRUCTOR_BUILD_TIMEOUT_MIN_OPTION = "buildTimeoutMin";

    public static final String INSTRUCTOR_BUILD_TIMEOUT_MAX_OPTION = "buildTimeoutMax";

    public static final String INSTRUCTOR_BUILD_TIMEOUT_DEFAULT_OPTION = "buildTimeoutDefault";

    public static final String USE_EXTERNAL = "useExternal";

    public static final String EXTERNAL_CREDENTIAL_PROVIDER = "externalCredentialProvider";

    public static final String EXTERNAL_PASSWORD_RESET_LINK_MAP = "externalPasswordResetLinkMap";

    public static final String VOTE_EMOJI_ID = "heavy_plus_sign";

    public static final String EXAM_EXERCISE_START_STATUS = "exam-exercise-start-status";

    public static final String PUSH_NOTIFICATION_ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";

    /**
     * The name of the Spring profile used to choose the local VC system instead of GitLab.
     */
    public static final String PROFILE_LOCALVC = "localvc";

    /**
     * The name of the Spring profile used to choose the local CI system instead of Jenkins, or GitLabCI.
     */
    public static final String PROFILE_LOCALCI = "localci";

    /**
     * The name of the Spring profile used to process build jobs in a local CI setup.
     */
    public static final String PROFILE_BUILDAGENT = "buildagent";

    /**
     * The name of the Spring profile used for Artemis functionality.
     */
    public static final String PROFILE_ARTEMIS = "artemis";

    /**
     * The name of the Spring profile used for Artemis core functionality.
     */
    public static final String PROFILE_CORE = "core";

    /**
     * The name of the Spring profile used for Iris / Pyris functionality.
     */
    public static final String PROFILE_IRIS = "iris";

    /**
     * The name of the Spring profile used for Athena functionality.
     */
    public static final String PROFILE_ATHENA = "athena";

    /**
     * The name of the Spring profile used for Athena functionality.
     */
    public static final String PROFILE_APOLLON = "apollon";

    /**
     * The name of the Spring profile used for the Aeolus external system.
     */
    public static final String PROFILE_AEOLUS = "aeolus";

    /**
     * The name of the Spring profile used for activating LTI in Artemis, see {@link LtiResource}.
     */
    public static final String PROFILE_LTI = "lti";

    /**
     * The name of the Spring profile used for activating SAML2 in Artemis, see {@link de.tum.cit.aet.artemis.core.service.connectors.SAML2Service}.
     */
    public static final String PROFILE_SAML2 = "saml2";

    public static final String PROFILE_SCHEDULING = "scheduling";

    /**
     * The name of the Spring profile used for Theia as an external online IDE.
     */
    public static final String PROFILE_THEIA = "theia";

    /**
     * The InfoContributor's detail key for the Theia Portal URL
     */

    public static final String THEIA_PORTAL_URL = "theiaPortalURL";

    /**
     * Size of an unsigned tinyInt in SQL, that is used in the database
     */
    public static final int SIZE_OF_UNSIGNED_TINYINT = 255;

    /**
     * The maximum length of a group conversation human-readable name before it is truncated if no name is specified.
     */
    public static final int GROUP_CONVERSATION_HUMAN_READABLE_NAME_LIMIT = 100;

    /**
     * The value of the version field we send with each push notification to the native clients (Android & iOS).
     */
    public static final int PUSH_NOTIFICATION_VERSION = 1;

    /**
     * The directory in the docker container in which the build script is executed
     */
    public static final String LOCALCI_WORKING_DIRECTORY = "/var/tmp";

    /**
     * The directory in the docker container in which the results can be found
     */
    public static final String LOCALCI_RESULTS_DIRECTORY = "/results";

    /**
     * The directory to which repositories temporarely get cloned for the build job execution
     */
    public static final String CHECKED_OUT_REPOS_TEMP_DIR = "checked-out-repos";

    /**
     * Minimum score for a result to be considered successful and shown in green
     */
    public static final int MIN_SCORE_GREEN = 80;

    /**
     * Minimum score for a result to be considered partially successful and shown in orange
     */
    public static final int MIN_SCORE_ORANGE = 40;

    public static final String ASSIGNMENT_REPO_PLACEHOLDER = "${studentWorkingDirectory}";

    public static final String TEST_REPO_PLACEHOLDER = "${testWorkingDirectory}";

    public static final String SOLUTION_REPO_PLACEHOLDER = "${solutionWorkingDirectory}";

    public static final String ASSIGNMENT_REPO_PARENT_PLACEHOLDER = "${studentParentWorkingDirectoryName}";

    public static final String ASSIGNMENT_REPO_PLACEHOLDER_NO_SLASH = "${studentWorkingDirectoryNoSlash}";

    public static final Pattern ALLOWED_CHECKOUT_DIRECTORY = Pattern.compile("[\\w-]+(/[\\w-]+)*$");

    private Constants() {
    }
}
