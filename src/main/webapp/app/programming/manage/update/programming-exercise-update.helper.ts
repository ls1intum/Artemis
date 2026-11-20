import { MAX_PACKAGE_NAME_LENGTH, PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN } from 'app/foundation/constants/input.constants';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { PROPOSED_PACKAGE_PREFIX_FOR_DOTTED_LANGUAGES, deriveProposedPackageName } from 'app/hyperion/problem-statement/problem-statement.utils';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

export enum ProgrammingExerciseInputField {
    // General section
    TITLE = 'title',
    CHANNEL_NAME = 'channelName',
    SHORT_NAME = 'shortName',
    EDIT_REPOSITORIES_CHECKOUT_PATH = 'editRepositoriesCheckoutPath',
    ADD_AUXILIARY_REPOSITORY = 'addAuxiliaryRepository',
    CATEGORIES = 'categories',

    // Mode section
    DIFFICULTY = 'difficulty',
    PARTICIPATION_MODE = 'participationMode',
    ALLOW_OFFLINE_IDE = 'allowOfflineIde',
    ALLOW_ONLINE_CODE_EDITOR = 'allowOnlineCodeEditor',
    ALLOW_ONLINE_IDE = 'allowOnlineIde',

    // Language section
    PROGRAMMING_LANGUAGE = 'programmingLanguage',
    PROJECT_TYPE = 'projectType',
    WITH_EXEMPLARY_DEPENDENCY = 'withExemplaryDependency',
    PACKAGE_NAME = 'packageName',
    ENABLE_STATIC_CODE_ANALYSIS = 'enableStaticCodeAnalysis',
    SEQUENTIAL_TEST_RUNS = 'sequentialTestRuns',

    // Version Control section
    ALLOW_BRANCHING = 'allowBranching',

    // Build time window section
    BUILD_TIME_WINDOW = 'buildTimeWindow',

    // Problem section
    PROBLEM_STATEMENT = 'problemStatement',
    LINKED_COMPETENCIES = 'linkedCompetencies',

    // Grading section
    INCLUDE_EXERCISE_IN_COURSE_SCORE_CALCULATION = 'includeExerciseInCourseScoreCalculation',
    POINTS = 'points',
    BONUS_POINTS = 'bonusPoints',
    SUBMISSION_POLICY = 'submissionPolicy',
    TIMELINE = 'timeline',
    RELEASE_DATE = 'releaseDate',
    START_DATE = 'startDate',
    DUE_DATE = 'dueDate',
    RUN_TESTS_AFTER_DUE_DATE = 'runTestsAfterDueDate',
    ASSESSMENT_DUE_DATE = 'assessmentDueDate',
    EXAMPLE_SOLUTION_PUBLICATION_DATE = 'exampleSolutionPublicationDate',
    COMPLAINT_ON_AUTOMATIC_ASSESSMENT = 'complaintOnAutomaticAssessment',
    SHOW_TEST_NAMES_TO_STUDENTS = 'showTestNamesToStudents',
    INCLUDE_TESTS_INTO_EXAMPLE_SOLUTION = 'includeTestsIntoExampleSolution',
    ASSESSMENT_INSTRUCTIONS = 'assessmentInstructions',
    PRESENTATION_SCORE = 'presentationScore',
    PLAGIARISM_CONTROL = 'plagiarismControl',
}

export const IS_DISPLAYED_IN_SIMPLE_MODE: Record<ProgrammingExerciseInputField, boolean> = {
    // General section
    title: true,
    channelName: false,
    shortName: false,
    editRepositoriesCheckoutPath: false,
    addAuxiliaryRepository: false,
    categories: true,
    // Mode section
    difficulty: true,
    participationMode: false,
    allowOfflineIde: false,
    allowOnlineCodeEditor: false,
    allowOnlineIde: false, // refers to theia
    // Language section
    programmingLanguage: true,
    projectType: false,
    withExemplaryDependency: false,
    packageName: false,
    enableStaticCodeAnalysis: false,
    sequentialTestRuns: false,
    // Version Control section
    allowBranching: false,
    // Build time window section
    buildTimeWindow: false,
    // Problem section
    problemStatement: true,
    linkedCompetencies: false,
    // Grading section
    includeExerciseInCourseScoreCalculation: true,
    points: true,
    bonusPoints: true,
    submissionPolicy: false,
    timeline: true,
    releaseDate: true,
    startDate: false,
    dueDate: true,
    runTestsAfterDueDate: false,
    assessmentDueDate: true,
    exampleSolutionPublicationDate: false,
    complaintOnAutomaticAssessment: false,
    showTestNamesToStudents: false,
    includeTestsIntoExampleSolution: false,
    assessmentInstructions: true,
    presentationScore: false,
    plagiarismControl: false,
};

/**
 * Namespaces Ares 2 trusts for its own classes. Ares fails every test when compiled exercise code lies inside one of them or in a package
 * above one of them (e.g. "de" or "org"). Keep in sync with RESERVED_ARES_PACKAGE_PREFIXES in ProgrammingExerciseValidationService.
 */
const RESERVED_ARES_PACKAGE_PREFIXES = [
    'java.',
    'javax.',
    'sun.',
    'jdk.',
    'com.sun.',
    'de.tum.cit.ase.ares.api.',
    'net.bytebuddy.',
    'org.aspectj.',
    'com.ibm.wala.',
    'com.tngtech.archunit.',
];

/** Whether Ares 2 refuses the package of a Java or Kotlin exercise, see {@link RESERVED_ARES_PACKAGE_PREFIXES}. */
export function isReservedAresPackageName(language: ProgrammingLanguage | undefined, packageName: string | undefined): boolean {
    if ((language !== ProgrammingLanguage.JAVA && language !== ProgrammingLanguage.KOTLIN) || !packageName) {
        return false;
    }
    const normalized = `${packageName}.`;
    // Inside a reserved namespace, or a parent of one
    return RESERVED_ARES_PACKAGE_PREFIXES.some((reserved) => normalized.startsWith(reserved) || reserved.startsWith(normalized));
}

/**
 * Derives the package name of a new exercise, so instructors do not have to pick one. Java and Kotlin get
 * "de.tum.cit.aet.<course short name>.<exercise short name>"; other languages, and short names that are Java keywords, fall back to a name derived from the title.
 */
export function deriveDefaultPackageName(exercise: ProgrammingExercise): string | undefined {
    const language = exercise.programmingLanguage;
    if (!language || !exercise.shortName) {
        return undefined;
    }
    if ((language === ProgrammingLanguage.JAVA || language === ProgrammingLanguage.KOTLIN) && exercise.projectType !== ProjectType.MAVEN_BLACKBOX) {
        const courseShortName = getCourseFromExercise(exercise)?.shortName;
        const packageName = [PROPOSED_PACKAGE_PREFIX_FOR_DOTTED_LANGUAGES, courseShortName, exercise.shortName].filter(Boolean).join('.').toLowerCase();
        if (packageName.length <= MAX_PACKAGE_NAME_LENGTH && new RegExp(PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN).test(packageName)) {
            return packageName;
        }
    }
    return deriveProposedPackageName(exercise.title || exercise.shortName, language, exercise.projectType);
}
