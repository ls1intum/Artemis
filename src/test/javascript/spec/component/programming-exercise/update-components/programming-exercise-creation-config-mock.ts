import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';

export const programmingExerciseCreationConfigMock: ProgrammingExerciseCreationConfig = {
    appNamePatternForSwift: '',
    auxiliaryRepositoriesSupported: false,
    auxiliaryRepositoryDuplicateDirectories: false,
    auxiliaryRepositoryDuplicateNames: false,
    checkoutSolutionRepositoryAllowed: false,
    exerciseCategories: [],
    existingCategories: [],
    hasUnsavedChanges: false,
    inProductionEnvironment: false,
    invalidDirectoryNamePattern: undefined,
    invalidRepositoryNamePattern: undefined,
    isEdit: false,
    isExamMode: false,
    isImportFromExistingExercise: false,
    isImportFromFile: false,
    maxPenaltyPattern: '',
    modePickerOptions: [],
    onProgrammingLanguageChange(language: ProgrammingLanguage): ProgrammingLanguage {
        return undefined;
    },
    onProjectTypeChange(projectType: ProjectType): ProjectType {
        return undefined;
    },
    onRecreateBuildPlanOrUpdateTemplateChange(): void {},
    onStaticCodeAnalysisChanged(): void {},
    onWithDependenciesChanged(withDependencies: boolean): boolean {
        return false;
    },
    packageNamePattern: '',
    packageNameRequired: false,
    problemStatementLoaded: false,
    projectTypes: [],
    publishBuildPlanUrlAllowed: false,
    recreateBuildPlanOrUpdateTemplateChange(): void {},
    recreateBuildPlans: false,
    refreshAuxiliaryRepositoryChecks(): void {},
    rerenderSubject: undefined,
    selectedProgrammingLanguage: undefined,
    selectedProjectType: undefined,
    sequentialTestRunsAllowed: false,
    shortNamePattern: undefined,
    showSummary: false,
    staticCodeAnalysisAllowed: false,
    supportedLanguages: [],
    templateParticipationResultLoaded: false,
    testwiseCoverageAnalysisSupported: false,
    titleNamePattern: '',
    updateCategories(categories: ExerciseCategory[]): void {},
    updateCheckoutDirectory(editedAuxiliaryRepository: AuxiliaryRepository): (newValue: any) => string | undefined {
        return function (p1: any) {
            return undefined;
        };
    },
    updateRepositoryName(auxiliaryRepository: AuxiliaryRepository): (newValue: any) => string | undefined {
        return function (p1: any) {
            return undefined;
        };
    },
    updateTemplate: false,
    validIdeSelection(): boolean | undefined {
        return undefined;
    },
    withDependencies: false,
};
