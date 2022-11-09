import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faArrowRight, faCheck, faHandshakeAngle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-update-wizard',
    templateUrl: './programming-exercise-update-wizard.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardComponent implements OnInit {
    @Input() toggleModeFunction: () => void;
    @Input() isSaving: boolean;

    @Input() isImport: boolean;

    // Information Step
    @Input() titleNamePattern: string;
    @Input() programmingExercise: ProgrammingExercise;
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

    // Problem Step
    @Input() problemStatementLoaded: boolean;
    @Input() templateParticipationResultLoaded: boolean;
    @Input() hasUnsavedChanges: boolean;
    @Input() rerenderSubject: Subject<void>;
    @Input() sequentialTestRunsAllowed: boolean;
    @Input() checkoutSolutionRepositoryAllowed: boolean;
    @Input() validIdeSelection: () => boolean | undefined;
    @Input() inProductionEnvironment: boolean;
    @Input() recreateBuildPlans: boolean;
    @Input() onRecreateBuildPlanOrUpdateTemplateChange: () => void;
    @Input() updateTemplate: boolean;

    currentStep: number;

    // Icons
    faCheck = faCheck;
    faHandShakeAngle = faHandshakeAngle;
    faArrowRight = faArrowRight;

    constructor(protected activatedRoute: ActivatedRoute, private navigationUtilService: ArtemisNavigationUtilService, private router: Router) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.isSaving = false;
        this.currentStep = 1;
    }

    /**
     * Progress to the next step of the wizard mode
     */
    next() {
        this.currentStep++;
    }

    /**
     * Checks if the given step has already been completed
     */
    isCompleted(step: number) {
        return this.currentStep > step;
    }

    /**
     * Checks if the given step is the current one
     */
    isCurrent(step: number) {
        return this.currentStep === step;
    }

    getNextIcon() {
        return this.currentStep < 5 ? faArrowRight : faCheck;
    }

    getNextText() {
        return this.currentStep < 5 ? 'artemisApp.programmingExercise.home.nextStepLabel' : 'entity.action.finish';
    }

    toggleWizardMode() {
        this.toggleModeFunction();
    }
}
