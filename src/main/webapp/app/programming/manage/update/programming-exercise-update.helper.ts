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

    // Version Control section
    ALLOW_BRANCHING = 'allowBranching',

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

/**
 * The lean field set shown on the AI-assisted create flow. It mirrors the simple layout but HIDES the problem statement (the agent authors it from the instructor's "Your Requirements"
 * brief) and SHOWS the short name + project type (both are structural — the scaffold the agent builds on). The instructor keeps the policy fields the agent cannot infer (points, dates,
 * difficulty, categories). Everything advanced stays hidden. The footer's "Generate entire exercise" action replaces Save in this mode (see programming-exercise-update.component).
 */
export const IS_DISPLAYED_IN_AI_MODE: Record<ProgrammingExerciseInputField, boolean> = {
    // General section — title (also the channel name) is the one field the agent never produces; short name is needed to scaffold the repositories.
    title: true,
    channelName: false,
    shortName: true,
    editRepositoriesCheckoutPath: false,
    addAuxiliaryRepository: false,
    categories: true,
    // Mode section — difficulty is instructor policy and stays manual for now (the agent does not emit it yet).
    difficulty: true,
    participationMode: false,
    allowOfflineIde: false,
    allowOnlineCodeEditor: false,
    allowOnlineIde: false,
    // Language section — the agent builds against a concrete harness, so language + project type + package are confirmed up front; SCA / sequential runs / build script stay off.
    programmingLanguage: true,
    projectType: true,
    withExemplaryDependency: false,
    packageName: true,
    enableStaticCodeAnalysis: false,
    sequentialTestRuns: false,
    customizeBuildScript: false,
    // Version Control section
    allowBranching: false,
    // Problem section — the statement editor stays visible so the instructor can review/adapt the AI-drafted "plan" before the full build; it starts empty (the brief drives a
    // from-scratch run, or "Draft a plan to review" populates it).
    problemStatement: true,
    linkedCompetencies: false,
    // Grading section — points, included-in-score and the timeline/dates are instructor policy and stay; everything else is advanced.
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
    assessmentInstructions: false,
    presentationScore: false,
    plagiarismControl: false,
};

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
    packageName: true,
    enableStaticCodeAnalysis: false,
    sequentialTestRuns: false,
    customizeBuildScript: false,
    // Version Control section
    allowBranching: false,
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
