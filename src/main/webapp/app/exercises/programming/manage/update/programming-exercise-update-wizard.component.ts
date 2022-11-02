import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faArrowRight, faCheck, faHandshakeAngle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-update-wizard',
    templateUrl: './programming-exercise-update-wizard.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardComponent implements OnInit {
    @Input() toggleModeFunction: () => void;
    @Input() isSaving: boolean;

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
