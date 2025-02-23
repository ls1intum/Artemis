import { AuxiliaryRepository } from 'app/entities/programming/programming-exercise-auxiliary-repository-model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { Observable } from 'rxjs';
import { ProgrammingLanguage, ProjectType } from 'app/entities/programming/programming-exercise.model';

export type ProgrammingExerciseCreationConfig = {
    titleNamePattern: string;
    shortNamePattern: RegExp;
    updateRepositoryName: (auxiliaryRepository: AuxiliaryRepository) => (newValue: any) => string | undefined;
    updateCheckoutDirectory: (editedAuxiliaryRepository: AuxiliaryRepository) => (newValue: any) => string | undefined;
    refreshAuxiliaryRepositoryChecks: () => void;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    updateCategories: (categories: ExerciseCategory[]) => void;
    auxiliaryRepositoriesSupported: boolean;
    auxiliaryRepositoryDuplicateDirectories: boolean;
    auxiliaryRepositoryDuplicateNames: boolean;
    checkoutSolutionRepositoryAllowed: boolean;
    customBuildPlansSupported: string;
    invalidDirectoryNamePattern: RegExp;
    invalidRepositoryNamePattern: RegExp;
    isImportFromExistingExercise: boolean;
    isImportFromFile: boolean;
    modePickerOptions?: ModePickerOption<ProjectType>[];
    withDependencies: boolean;
    onWithDependenciesChanged: (withDependencies: boolean) => boolean;
    packageNameRequired: boolean;
    packageNamePattern: string;
    supportedLanguages: string[];
    selectedProgrammingLanguage: ProgrammingLanguage;
    onProgrammingLanguageChange: (language: ProgrammingLanguage) => ProgrammingLanguage;
    projectTypes?: ProjectType[];
    selectedProjectType?: ProjectType;
    onProjectTypeChange: (projectType: ProjectType) => ProjectType | undefined;
    staticCodeAnalysisAllowed: boolean;
    onStaticCodeAnalysisChanged: () => void;
    maxPenaltyPattern: string;
    sequentialTestRunsAllowed: boolean;
    problemStatementLoaded: boolean;
    templateParticipationResultLoaded: boolean;
    hasUnsavedChanges: boolean;
    rerenderSubject: Observable<void>;
    validIdeSelection: () => boolean | undefined;
    validOnlineIdeSelection: () => boolean | undefined;
    inProductionEnvironment: boolean;
    recreateBuildPlans: boolean;
    onRecreateBuildPlanOrUpdateTemplateChange: () => void;
    updateTemplate: boolean;
    recreateBuildPlanOrUpdateTemplateChange: () => void; // default false
    isExamMode: boolean;
    isEdit: boolean;
    showSummary: boolean;
    buildPlanLoaded: boolean;
};
