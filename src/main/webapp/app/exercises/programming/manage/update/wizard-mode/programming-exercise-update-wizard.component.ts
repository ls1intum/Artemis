import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { Observable } from 'rxjs';
import { ValidationReason } from 'app/entities/exercise.model';

type firstStepInputs = {
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

    // Information Step
    @Input() inputs: firstStepInputs;

    @Input() titleNamePattern: string;
    @Input() shortNamePattern: RegExp;

    @Input() invalidRepositoryNamePattern: RegExp;
    @Input() invalidDirectoryNamePattern: RegExp;
    @Input() updateRepositoryName: (auxiliaryRepository: AuxiliaryRepository) => (newValue: any) => string | undefined;
    @Input() updateCheckoutDirectory: (editedAuxiliaryRepository: AuxiliaryRepository) => (newValue: any) => string | undefined;
    @Input() refreshAuxiliaryRepositoryChecks: () => void;
    @Input() auxiliaryRepositoryDuplicateNames: boolean;
    @Input() auxiliaryRepositoryDuplicateDirectories: boolean;

    @Input() exerciseCategories: ExerciseCategory[];
    @Input() existingCategories: ExerciseCategory[];
    @Input() updateCategories: (categories: ExerciseCategory[]) => void;

    // Programming Language Step
    @Input() appNamePatternForSwift: string;
    @Input() modePickerOptions: ModePickerOption<ProjectType>[];
    @Input() withDependencies: boolean;
    @Input() packageNameRequired: boolean;
    @Input() packageNamePattern: string;

    @Input() supportsJava: boolean;
    @Input() supportsPython: boolean;
    @Input() supportsC: boolean;
    @Input() supportsHaskell: boolean;
    @Input() supportsKotlin: boolean;
    @Input() supportsVHDL: boolean;
    @Input() supportsAssembler: boolean;
    @Input() supportsSwift: boolean;
    @Input() supportsOCaml: boolean;
    @Input() supportsEmpty: boolean;

    @Input() selectedProgrammingLanguage: ProgrammingLanguage;
    @Input() onProgrammingLanguageChange: (language: ProgrammingLanguage) => ProgrammingLanguage;
    @Input() projectTypes: ProjectType[];
    @Input() selectedProjectType: ProjectType;
    @Input() onProjectTypeChange: (projectType: ProjectType) => ProjectType;

    // Grading Step
    @Input() staticCodeAnalysisAllowed: boolean;
    @Input() onStaticCodeAnalysisChanged: () => void;
    @Input() maxPenaltyPattern: string;

    // Problem Step
    @Input() problemStatementLoaded: boolean;
    @Input() templateParticipationResultLoaded: boolean;
    @Input() hasUnsavedChanges: boolean;
    @Input() rerenderSubject: Observable<void>;
    @Input() sequentialTestRunsAllowed: boolean;
    @Input() checkoutSolutionRepositoryAllowed: boolean;
    @Input() validIdeSelection: () => boolean | undefined;
    @Input() inProductionEnvironment: boolean;
    @Input() recreateBuildPlans: boolean;
    @Input() onRecreateBuildPlanOrUpdateTemplateChange: () => void;
    @Input() updateTemplate: boolean;

    constructor(protected activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
    }

    nextStep() {
        this.onNextStep.emit();
    }
}
