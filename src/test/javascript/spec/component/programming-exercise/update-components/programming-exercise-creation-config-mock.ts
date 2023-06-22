import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';

/* eslint-disable @typescript-eslint/no-unused-vars */
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
    invalidDirectoryNamePattern: new RegExp('(?!)'),
    invalidRepositoryNamePattern: new RegExp('(?!)'),
    isEdit: false,
    isExamMode: false,
    isImportFromExistingExercise: false,
    isImportFromFile: false,
    maxPenaltyPattern: '',
    modePickerOptions: [],
    onProgrammingLanguageChange(_language: ProgrammingLanguage): ProgrammingLanguage {
        return undefined;
    },
    onProjectTypeChange(_projectType: ProjectType): ProjectType {
        return undefined;
    },
    onRecreateBuildPlanOrUpdateTemplateChange(): void {},
    onStaticCodeAnalysisChanged(): void {},
    onWithDependenciesChanged(_withDependencies: boolean): boolean {
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
    selectedProgrammingLanguage: ProgrammingLanguage.JAVA,
    selectedProjectType: ProjectType.PLAIN_GRADLE,
    sequentialTestRunsAllowed: false,
    shortNamePattern: new RegExp('(?!)'),
    showSummary: false,
    staticCodeAnalysisAllowed: false,
    supportedLanguages: [],
    templateParticipationResultLoaded: false,
    testwiseCoverageAnalysisSupported: false,
    titleNamePattern: '',
    updateCategories(_categories: ExerciseCategory[]): void {},
    updateCheckoutDirectory(_editedAuxiliaryRepository: AuxiliaryRepository): (newValue: any) => string | undefined {
        return function (p1: any) {
            return undefined;
        };
    },
    updateRepositoryName(_auxiliaryRepository: AuxiliaryRepository): (newValue: any) => string | undefined {
        return function (_p1: any) {
            return undefined;
        };
    },
    updateTemplate: false,
    validIdeSelection(): boolean | undefined {
        return true;
    },
    withDependencies: false,
};
