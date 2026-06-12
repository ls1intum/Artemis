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
 * The radically lean field set for the AI-assisted create flow: the instructor makes only the two decisions the agent genuinely cannot — the programming LANGUAGE (which fixes the
 * harness the agent builds against) and the "Your Requirements" brief (the problem-statement surface, where the agent authors the exercise). EVERYTHING else is auto-generated. The
 * title is auto-seeded from the brief and then reconciled server-side from the generated problem statement's H1, so it never appears on the page; short name, package name and points
 * are seeded with valid values in AI mode (see {@code setEditMode}/{@code seedAiModeDefaults}); project type, bonus points and included-in-score fall back to their model defaults;
 * difficulty, categories and the whole release/due/assessment timeline are OMITTED — they are not needed to create a (yet-unreleased) exercise and are set on the exercise details
 * after the verified exercise exists, or in Advanced mode for instructors who want them up front. The footer's "Generate entire exercise" action replaces Save (see the update
 * component). Net read of the page: pick a language, describe the exercise, generate.
 */
export const IS_DISPLAYED_IN_AI_MODE: Record<ProgrammingExerciseInputField, boolean> = {
    // General section — title is auto-seeded from the brief (and refined server-side from the generated H1), short name is auto-derived; both hidden. Categories are deferred.
    title: false,
    channelName: false,
    shortName: false,
    editRepositoriesCheckoutPath: false,
    addAuxiliaryRepository: false,
    categories: false,
    // Mode section — difficulty is omitted (the agent does not consume it; showing it asks the instructor to grade unseen content).
    difficulty: false,
    participationMode: false,
    allowOfflineIde: false,
    allowOnlineCodeEditor: false,
    allowOnlineIde: false,
    // Language section — only the language is the instructor's decision; project type and package name are seeded/defaulted per language and hidden.
    programmingLanguage: true,
    projectType: false,
    withExemplaryDependency: false,
    packageName: false,
    enableStaticCodeAnalysis: false,
    sequentialTestRuns: false,
    customizeBuildScript: false,
    // Version Control section
    allowBranching: false,
    // Problem section — the "Your Requirements" brief + statement editor is the heart of the page; the agent authors the statement (or "Draft a plan to review" previews it).
    problemStatement: true,
    linkedCompetencies: false,
    // Grading section — all omitted: points/included-in-score/bonus are defaulted and disclosed; the timeline/dates are set later, before releasing.
    includeExerciseInCourseScoreCalculation: false,
    points: false,
    bonusPoints: false,
    submissionPolicy: false,
    timeline: false,
    releaseDate: false,
    startDate: false,
    dueDate: false,
    runTestsAfterDueDate: false,
    assessmentDueDate: false,
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
