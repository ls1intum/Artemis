import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { Observable } from 'rxjs';
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
    buildPlanLoaded: false,
    customBuildPlansSupported: '',
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
    onProgrammingLanguageChange(language: ProgrammingLanguage): ProgrammingLanguage {
        return language;
    },
    onProjectTypeChange(projectType: ProjectType): ProjectType {
        return projectType;
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
    recreateBuildPlanOrUpdateTemplateChange(): void {},
    recreateBuildPlans: false,
    refreshAuxiliaryRepositoryChecks(): void {},
    rerenderSubject: new Observable(),
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
        return function (_p1: any) {
            return 'dirName';
        };
    },
    updateRepositoryName(_auxiliaryRepository: AuxiliaryRepository): (newValue: any) => string | undefined {
        return function (_p1: any) {
            return 'repoName';
        };
    },
    updateTemplate: false,
    validIdeSelection(): boolean | undefined {
        return true;
    },
    withDependencies: false,
};
