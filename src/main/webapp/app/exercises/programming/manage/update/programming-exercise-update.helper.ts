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
    CUSTOMIZE_BUILD_SCRIPT = 'customizeBuildScript',

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
    MANUAL_FEEDBACK_REQUESTS = 'manualFeedbackRequests',
    SHOW_TEST_NAMES_TO_STUDENTS = 'showTestNamesToStudents',
    INCLUDE_TESTS_INTO_EXAMPLE_SOLUTION = 'includeTestsIntoExampleSolution',
    ASSESSMENT_INSTRUCTIONS = 'assessmentInstructions',
    PRESENTATION_SCORE = 'presentationScore',
    PLAGIARISM_CONTROL = 'plagiarismControl',
}

export type InputFieldEditModeMapping = Record<ProgrammingExerciseInputField, boolean>;

export const IS_DISPLAYED_IN_SIMPLE_MODE: InputFieldEditModeMapping = {
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
    packageName: true,
    enableStaticCodeAnalysis: false,
    sequentialTestRuns: false,
    customizeBuildScript: false,
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
    manualFeedbackRequests: false,
    showTestNamesToStudents: false,
    includeTestsIntoExampleSolution: false,
    assessmentInstructions: true,
    presentationScore: false,
    plagiarismControl: false,
};
