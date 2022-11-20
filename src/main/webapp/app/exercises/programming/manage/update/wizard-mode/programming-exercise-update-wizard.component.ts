import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { Observable } from 'rxjs';
import { ValidationReason } from 'app/entities/exercise.model';

export type InfoStepInputs = {
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
};

export type LanguageStepInputs = {
    appNamePatternForSwift: string;
    modePickerOptions: ModePickerOption<ProjectType>[];
    withDependencies: boolean;
    packageNameRequired: boolean;
    packageNamePattern: string;
    supportsJava: boolean;
    supportsPython: boolean;
    supportsC: boolean;
    supportsHaskell: boolean;
    supportsKotlin: boolean;
    supportsVHDL: boolean;
    supportsAssembler: boolean;
    supportsSwift: boolean;
    supportsOCaml: boolean;
    supportsEmpty: boolean;
    selectedProgrammingLanguage: ProgrammingLanguage;
    onProgrammingLanguageChange: (language: ProgrammingLanguage) => ProgrammingLanguage;
    projectTypes: ProjectType[];
    selectedProjectType: ProjectType;
    onProjectTypeChange: (projectType: ProjectType) => ProjectType;
};

export type GradingStepInputs = {
    staticCodeAnalysisAllowed: boolean;
    onStaticCodeAnalysisChanged: () => void;
    maxPenaltyPattern: string;
};

export type ProblemStepInputs = {
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
};

@Component({
    selector: 'jhi-programming-exercise-update-wizard',
    templateUrl: './programming-exercise-update-wizard.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardComponent implements OnInit {
    programmingExercise: ProgrammingExercise;

    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();

    @Input()
    get exercise() {
        return this.programmingExercise;
    }

    set exercise(exercise: ProgrammingExercise) {
        this.programmingExercise = exercise;
        this.exerciseChange.emit(this.programmingExercise);
    }

    @Input() toggleMode: () => void;
    @Input() isSaving: boolean;
    @Input() currentStep: number;
    @Output() onNextStep: EventEmitter<any> = new EventEmitter();
    @Input() getInvalidReasons: () => ValidationReason[];
    @Input() isImport: boolean;

    @Input() infoStepInputs: InfoStepInputs;
    @Input() languageStepInputs: LanguageStepInputs;
    @Input() gradingStepInputs: GradingStepInputs;
    @Input() problemStepInputs: ProblemStepInputs;

    constructor(protected activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
    }

    nextStep() {
        this.onNextStep.emit();
    }
}
