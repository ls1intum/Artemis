export const DATE_FORMAT = 'YYYY-MM-DD';
export const DATE_TIME_FORMAT = 'YYYY-MM-DDTHH:mm';

// Note: The values in Constants.java (server) need to be the same

/** Maximum file size: 20 MB (Spring-Boot interprets MB as 1024^2 bytes) **/
export const MAX_FILE_SIZE = 20 * 1024 * 1024;
/** Maximum submission file size: 4 MB **/
export const MAX_SUBMISSION_FILE_SIZE = 8 * 1024 * 1024;
/** Maximum text exercise submission character length: 30.000 **/
export const MAX_SUBMISSION_TEXT_LENGTH = 30 * 1000;
/** Maximum quiz exercise short answer character length: 255 **/
export const MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH = 255; // Must be consistent with database column definition
/** Short names must start with a letter and cannot contain special characters **/
export const SHORT_NAME_PATTERN = /^[a-zA-Z][a-zA-Z0-9]{2,}$/;
/** Programming exercise titles must only contain alphanumeric characters, or whitespaces, or '_' or '-' **/
export const EXERCISE_TITLE_NAME_PATTERN = '^[a-zA-Z0-9-_ ]+';
export const EXERCISE_TITLE_NAME_REGEX = new RegExp(EXERCISE_TITLE_NAME_PATTERN);
/** Prefixes must follow the login pattern **/
export const LOGIN_PATTERN = /^[_'.@A-Za-z0-9-]*$/;
export const MAX_QUIZ_QUESTION_POINTS = 9999;
export const MAX_QUIZ_QUESTION_LENGTH_THRESHOLD = 250;
export const MAX_QUIZ_QUESTION_EXPLANATION_LENGTH_THRESHOLD = 500;
export const MAX_QUIZ_QUESTION_HINT_LENGTH_THRESHOLD = 255;
export const ASSIGNMENT_REPO_NAME = 'assignment';
export const TEST_REPO_NAME = 'tests';

export const MAX_PENALTY_PATTERN = '^([0-9]|([1-9][0-9])|100)$';
// No dots allowed for the blackbox project type, because the folder naming works slightly different here.
export const PACKAGE_NAME_PATTERN_FOR_JAVA_BLACKBOX =
    '^(?!.*(?:\\.|^)(?:abstract|continue|for|new|switch|assert|default|if|package|synchronized|boolean|do|goto|private|this|break|double|implements|protected|throw|byte|else|import|public|throws|case|enum|instanceof|return|transient|catch|extends|int|short|try|char|final|interface|static|void|class|finally|long|strictfp|volatile|const|float|native|super|while|_|true|false|null)(?:\\.|$))[A-Z_a-z][0-9A-Z_a-z]*$';
// Auxiliary Repository names must only include words or '-' characters.
export const INVALID_REPOSITORY_NAME_PATTERN = RegExp('^(?!(solution|exercise|tests|auxiliary)\\b)\\b(\\w|-)+$');
// Auxiliary Repository checkout directories must be valid directory paths. Those must only include words,
// '-' or '/' characters.
export const INVALID_DIRECTORY_NAME_PATTERN = RegExp('^[\\w-]+(/[\\w-]+)*$');
// length of < 3 is also accepted in order to provide more accurate validation error messages
export const PROGRAMMING_EXERCISE_SHORT_NAME_PATTERN = RegExp('(^(?![\\s\\S]))|^[a-zA-Z][a-zA-Z0-9]*$|' + SHORT_NAME_PATTERN); // must start with a letter and cannot contain special characters

// Java package name Regex according to Java 14 JLS (https://docs.oracle.com/javase/specs/jls/se14/html/jls-7.html#jls-7.4.1),
// with the restriction to a-z,A-Z,_ as "Java letter" and 0-9 as digits due to JavaScript/Browser Unicode character class limitations
export const PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN =
    '^(?!.*(?:\\.|^)(?:abstract|continue|for|new|switch|assert|default|if|package|synchronized|boolean|do|goto|private|this|break|double|implements|protected|throw|byte|else|import|public|throws|case|enum|instanceof|return|transient|catch|extends|int|short|try|char|final|interface|static|void|class|finally|long|strictfp|volatile|const|float|native|super|while|_|true|false|null)(?:\\.|$))[A-Z_a-z][0-9A-Z_a-z]*(?:\\.[A-Z_a-z][0-9A-Z_a-z]*)*$';
// Swift package name Regex derived from (https://docs.swift.org/swift-book/ReferenceManual/LexicalStructure.html#ID412),
// with the restriction to a-z,A-Z as "Swift letter" and 0-9 as digits where no separators are allowed
export const APP_NAME_PATTERN_FOR_SWIFT =
    '^(?!(?:associatedtype|class|deinit|enum|extension|fileprivate|func|import|init|inout|internal|let|open|operator|private|protocol|public|rethrows|static|struct|subscript|typealias|var|break|case|continue|default|defer|do|else|fallthrough|for|guard|if|in|repeat|return|switch|where|while|as|Any|catch|false|is|nil|super|self|Self|throw|throws|true|try|_|[sS]wift)$)[A-Za-z][0-9A-Za-z]*$';
