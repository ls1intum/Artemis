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
    allowOnlineIde: {
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
        // if this field was removed from a mode, a reasonable default value would need to be set
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
    includeExerciseInCourseScoreCalculation: {
        editModesToBeDisplayed: ['SIMPLE', 'ADVANCED'],
    },
    points: {
        // if this field was removed from a mode, a reasonable default value would need to be set
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
