import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faArrowRight, faCheck, faHandshakeAngle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard',
    templateUrl: './programming-exercise-update-wizard.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardComponent implements OnInit {
    @Input() toggleModeFunction: () => void;
    @Input() isSaving: boolean;

    @Input() isImport: boolean;

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
