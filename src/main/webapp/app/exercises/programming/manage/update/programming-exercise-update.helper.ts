export enum ProgrammingExerciseInputField {
    // General section
    title = 'title',
    channelName = 'channelName',
    shortName = 'shortName',
    categories = 'categories',

    // Mode section
    difficulty = 'difficulty',
    participationMode = 'participationMode',
    allowOfflineIde = 'allowOfflineIde',
    allowOnlineEditor = 'allowOnlineEditor',

    // Language section
    programmingLanguage = 'programmingLanguage',
    projectType = 'projectType',
    withExemplaryDependency = 'withExemplaryDependency',
    packageName = 'packageName',
    enableStaticCodeAnalysis = 'enableStaticCodeAnalysis',
    sequentialTestRuns = 'sequentialTestRuns',
    customizeBuildScript = 'customizeBuildScript',

    // Problem section
    problemStatement = 'problemStatement',
    linkedCompetencies = 'linkedCompetencies',

    // Grading section
    includeCourseInScoreCalculation = 'includeCourseInScoreCalculation',
    points = 'points',
    bonusPoints = 'bonusPoints',
    submissionPolicy = 'submissionPolicy',
    // timeline
    timeline = 'timeline',
    releaseDate = 'releaseDate',
    startDate = 'startDate',
    dueDate = 'dueDate',
    runTestsAfterDueDate = 'runTestsAfterDueDate',
    assessmentDueDate = 'assessmentDueDate',
    exampleSolutionPublicationDate = 'exampleSolutionPublicationDate',
    // assessment
    complaintOnAutomaticAssessment = 'complaintOnAutomaticAssessment',
    manualFeedbackRequests = 'manualFeedbackRequests',
    showTestNamesToStudents = 'showTestNamesToStudents',
    includeTestsIntoExampleSolution = 'includeTestsIntoExampleSolution',
    // assessment instructions
    assessmentInstructions = 'assessmentInstructions',
    presentationScore = 'presentationScore',
    plagiarismControl = 'plagiarismControl',
}

// export type ProgrammingExerciseInputField = 'title' | 'channelName' | 'shortName';

export type EditMode = 'SIMPLE' | 'ADVANCED';

export type InputFieldOptions = { editModesToBeDisplayed: EditMode[] };

export type InputFieldEditModeMapping = Record<ProgrammingExerciseInputField, InputFieldOptions>;

export const INPUT_FIELD_EDIT_MODE_MAPPING: InputFieldEditModeMapping = {
    // General section
    title: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    channelName: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    shortName: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    categories: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    // Mode section
    difficulty: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    participationMode: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    allowOfflineIde: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    allowOnlineEditor: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    // Language section
    programmingLanguage: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    projectType: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    withExemplaryDependency: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    packageName: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    enableStaticCodeAnalysis: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    sequentialTestRuns: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    customizeBuildScript: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    // Problem section
    problemStatement: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    linkedCompetencies: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    // Grading section
    includeCourseInScoreCalculation: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    points: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    bonusPoints: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    submissionPolicy: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    timeline: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    releaseDate: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    startDate: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    dueDate: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    runTestsAfterDueDate: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    assessmentDueDate: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    exampleSolutionPublicationDate: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    complaintOnAutomaticAssessment: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    manualFeedbackRequests: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    showTestNamesToStudents: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    includeTestsIntoExampleSolution: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    assessmentInstructions: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    presentationScore: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
    plagiarismControl: {
        editModesToBeDisplayed: ['ADVANCED'],
    },
};
