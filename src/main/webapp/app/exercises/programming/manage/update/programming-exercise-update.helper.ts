export type ProgrammingExerciseInputField =
    // general section
    | 'title'
    | 'channelName'
    | 'shortName'
    | 'categories'

    // mode section
    | 'difficulty'
    | 'participationMode'
    | 'allowOfflineIde'
    | 'allowOnlineCodeEditor'
    | 'allowOnlineIde'

    // language section
    | 'programmingLanguage'
    | 'projectType'
    | 'withExemplaryDependency'
    | 'packageName'
    | 'enableStaticCodeAnalysis'
    | 'sequentialTestRuns'
    | 'customizeBuildScript'

    // problem section
    | 'problemStatement'
    | 'linkedCompetencies'

    // grading section
    | 'includeExerciseInCourseScoreCalculation'
    | 'points'
    | 'bonusPoints'
    | 'submissionPolicy'
    | 'timeline'
    | 'releaseDate'
    | 'startDate'
    | 'dueDate'
    | 'runTestsAfterDueDate'
    | 'assessmentDueDate'
    | 'exampleSolutionPublicationDate'
    | 'complaintOnAutomaticAssessment'
    | 'manualFeedbackRequests'
    | 'showTestNamesToStudents'
    | 'includeTestsIntoExampleSolution'
    | 'assessmentInstructions'
    | 'presentationScore'
    | 'plagiarismControl';

export type InputFieldEditModeMapping = Record<ProgrammingExerciseInputField, boolean>;

export const INPUT_FIELD_EDIT_MODE_MAPPING: InputFieldEditModeMapping = {
    // General section
    title: true,
    channelName: false,
    shortName: false,
    categories: true,
    // Mode section
    difficulty: true,
    participationMode: false,
    allowOfflineIde: false,
    allowOnlineCodeEditor: false,
    allowOnlineIde: false,
    // TODO THEIA
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
