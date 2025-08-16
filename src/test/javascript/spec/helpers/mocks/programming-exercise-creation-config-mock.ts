import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { Observable } from 'rxjs';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';

/* eslint-disable @typescript-eslint/no-unused-vars */
export const programmingExerciseCreationConfigMock: ProgrammingExerciseCreationConfig = {
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
    isImportFromSharing: false,
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
    validOnlineIdeSelection(): boolean | undefined {
        return true;
    },
    withDependencies: false,
};
