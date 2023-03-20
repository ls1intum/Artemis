import { Component, Input } from '@angular/core';
import { faCheck, faDotCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-step',
    templateUrl: './programming-exercise-update-wizard-step.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardStepComponent {
    @Input()
    currentlySelected: boolean;

    @Input()
    isPerformed: boolean;

    @Input()
    labelTranslationKey: string;

    @Input()
    descriptionTranslationKey: string;

    faCheck = faCheck;
    faDotCircle = faDotCircle;
}
