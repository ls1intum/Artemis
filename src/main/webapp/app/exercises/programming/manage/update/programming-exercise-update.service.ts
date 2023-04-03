import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { Observable } from 'rxjs';

interface ExerciseUpdateConfig {
    programmingExercise: ProgrammingExercise;
    isImport: boolean;
    titleNamePattern: string;
    shortNamePattern: RegExp;
    invalidRepositoryNamePattern: RegExp;
    invalidDirectoryNamePattern: RegExp;
    updateRepositoryName: (auxiliaryRepository: AuxiliaryRepository) => (newValue: any) => string | undefined;
    updateCheckoutDirectory: (editedAuxiliaryRepository: AuxiliaryRepository) => (newValue: any) => string | undefined;
    refreshAuxiliaryRepositoryChecks: () => void;
    auxiliaryRepositoryDuplicateNames: boolean;
    auxiliaryRepositoryDuplicateDirectories: boolean;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    updateCategories: (categories: ExerciseCategory[]) => void;

    appNamePatternForSwift: string;
    modePickerOptions: ModePickerOption<ProjectType>[];
    withDependencies: boolean;
    onWithDependenciesChanged: (withDependencies: boolean) => boolean;
    packageNameRequired: boolean;
    packageNamePattern: string;
    supportedLanguages: string[];
    selectedProgrammingLanguage: ProgrammingLanguage;
    onProgrammingLanguageChange: (language: ProgrammingLanguage) => ProgrammingLanguage;
    projectTypes: ProjectType[];
    selectedProjectType: ProjectType;
    onProjectTypeChange: (projectType: ProjectType) => ProjectType;

    staticCodeAnalysisAllowed: boolean;
    onStaticCodeAnalysisChanged: () => void;
    maxPenaltyPattern: string;

    problemStatementLoaded: boolean;
    templateParticipationResultLoaded: boolean;
    hasUnsavedChanges: boolean;
    rerenderSubject: Observable<void>;
    sequentialTestRunsAllowed: boolean;
    checkoutSolutionRepositoryAllowed: boolean;
    validIdeSelection: () => boolean | undefined;
    inProductionEnvironment: boolean;
    recreateBuildPlans: boolean;
    onRecreateBuildPlanOrUpdateTemplateChange: () => void;
    updateTemplate: boolean;

    isEdit: boolean;
    shouldHidePreview: boolean;
}

export class ProgrammingExerciseUpdateService {
    config: ExerciseUpdateConfig;

    constructor() {}

    public configure(stepInputs: ExerciseUpdateConfig) {
        this.config = stepInputs;
    }
}
