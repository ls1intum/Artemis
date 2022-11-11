import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faArrowRight, faCheck, faHandshakeAngle } from '@fortawesome/free-solid-svg-icons';
import { ValidationReason } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-bottom-bar',
    templateUrl: './programming-exercise-update-wizard-bottom-bar.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardBottomBarComponent {
    @Input() currentStep: number;
    @Output() onNextStep: EventEmitter<any> = new EventEmitter();

    @Input() toggleMode: () => void;
    @Input() isSaving: boolean;
    @Input() getInvalidReasons: () => ValidationReason[];

    faCheck = faCheck;
    faHandShakeAngle = faHandshakeAngle;
    faArrowRight = faArrowRight;

    /**
     * Checks if the given step has already been completed
     */
    isCompleted(step: number) {
        return this.currentStep > step;
    }

    isCurrentStep(step: number) {
        return this.currentStep === step;
    }

    getNextIcon() {
        return this.currentStep < 5 ? faArrowRight : faCheck;
    }

    getNextText() {
        return this.currentStep < 5 ? 'artemisApp.programmingExercise.home.nextStepLabel' : 'entity.action.finish';
    }

    nextStep() {
        this.onNextStep.emit();
    }
}
