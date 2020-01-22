package de.tum.in.www1.artemis.config;

import java.io.File;
import java.util.regex.Pattern;

/**
 * Application constants.
 */
public final class Constants {

    // Regex for acceptable logins
    public static final String LOGIN_REGEX = "^[_'.@A-Za-z0-9-]*$";

    public static final String SYSTEM_ACCOUNT = "system";

    public static final String DEFAULT_LANGUAGE = "en";

    public static final int QUIZ_GRACE_PERIOD_IN_SECONDS = 2;

    public static final String TEMP_FILEPATH = "uploads" + File.separator + "images" + File.separator + "temp" + File.separator;

    public static final String DRAG_AND_DROP_BACKGROUND_FILEPATH = "uploads" + File.separator + "images" + File.separator + "drag-and-drop" + File.separator + "backgrounds"
            + File.separator;

    public static final String DRAG_ITEM_FILEPATH = "uploads" + File.separator + "images" + File.separator + "drag-and-drop" + File.separator + "drag-items" + File.separator;

    public static final String COURSE_ICON_FILEPATH = "uploads" + File.separator + "images" + File.separator + "course" + File.separator + "icons" + File.separator;

    public static final String LECTURE_ATTACHMENT_FILEPATH = "uploads" + File.separator + "attachments" + File.separator + "lecture" + File.separator;

    public static final String FILE_UPLOAD_EXERCISES_FILEPATH = "uploads" + File.separator + "file-upload-exercises" + File.separator;

    public static final String FILEPATH_ID_PLACHEOLDER = "PLACEHOLDER_FOR_ID";

    public static final String PARTICIPATION_TOPIC_ROOT = "/topic/participation/";

    public static final String NEW_RESULT_RESOURCE_PATH = "/programming-exercises/new-result";

    public static final String NEW_RESULT_RESOURCE_API_PATH = "/api" + NEW_RESULT_RESOURCE_PATH;

    public static final String TEST_CASE_CHANGED_PATH = "/programming-exercises/test-cases-changed/";

    public static final String TEST_CASE_CHANGED_API_PATH = "/api" + TEST_CASE_CHANGED_PATH;

    public static final String PROGRAMMING_SUBMISSION_RESOURCE_PATH = "/programming-submissions/";

    public static final String PROGRAMMING_SUBMISSION_RESOURCE_API_PATH = "/api" + PROGRAMMING_SUBMISSION_RESOURCE_PATH;

    public static final String PROGRAMMING_SUBMISSION_TOPIC = "/newSubmission";

    public static final String SHORT_NAME_REGEX = "^[a-zA-Z][a-zA-Z0-9]*";

    public static final Pattern SHORT_NAME_PATTERN = Pattern.compile(SHORT_NAME_REGEX);

    public static final String TUM_USERNAME_REGEX = "^([a-z]{2}\\d{2}[a-z]{3})";

    public static final Pattern TUM_USERNAME_PATTERN = Pattern.compile(TUM_USERNAME_REGEX);

    public static final Pattern TITLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\s]*");

    public static final double COMPASS_SCORE_EQUALITY_THRESHOLD = 0.0001;

    // NOTE: the following values for programming exercises are hard-coded at the moment
    public static final String TEST_REPO_NAME = "tests";

    public static final String ASSIGNMENT_REPO_NAME = "assignment";

    public static final long MAX_COMPLAINT_NUMBER_PER_STUDENT = 3;

    public static final long MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR = 10;

    public static final long MAX_UPLOAD_FILESIZE_BYTES = 2 * 1024 * 1024; // 2 MiB

    public static final String TEST_CASES_CHANGED_NOTIFICATION = "The test cases of this programming exercise were updated. The student submissions should be build and tested so that results with the updated settings can be created.";

    public static final String TEST_CASES_CHANGED_RUN_COMPLETED_NOTIFICATION = "Build and Test run complete. New results were created for the programming exercise's student submissions with the updated test case settings.";

    public static final String BUILD_RUN_STARTED_FOR_PROGRAMMING_EXERCISE = "Build run triggered for programming exercise";

    public static final String BUILD_RUN_COMPLETE_FOR_PROGRAMMING_EXERCISE = "All builds triggered for programming exercise";

    public static final String PROGRAMMING_EXERCISE_FAILED_LOCK_OPERATIONS_NOTIFICATION = "The due date of this programming exercise has passed. When removing the write permissions for the student repositories, not all operations were successful. Number of failed operations: ";

    public static final String PROGRAMMING_EXERCISE_SUCCESSFUL_LOCK_OPERATION_NOTIFICATION = "The student repositories for this programming exercise were locked successfully when the due date passed.";

    public static final int FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS = 5000;

    public static final String ASSIGNMENT_CHECKOUT_PATH = "assignment";

    public static final String TESTS_CHECKOUT_PATH = "tests";

    public static final int EXTERNAL_SYSTEM_REQUEST_BATCH_SIZE = 100;

    // NOTE: this has to be the same as in complaint.constants.ts on the client
    public static final int MAX_COMPLAINT_TIME_WEEKS = 1;

    // Currently 10s.
    public static final int EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS = 10 * 1000; // 10s

    public static final String SETUP_COMMIT_MESSAGE = "Setup";

    public static final String REGISTER_FOR_COURSE = "REGISTER_FOR_COURSE";

    public static final String DELETE_EXERCISE = "DELETE_EXERCISE";

    public static final String DELETE_COURSE = "DELETE_COURSE";

    public static final String DELETE_PARTICIPATION = "DELETE_PARTICIPATION";

    public static final String INFO_BUILD_PLAN_URL_DETAIL = "buildPlanURLTemplate";

    private Constants() {
    }
}
