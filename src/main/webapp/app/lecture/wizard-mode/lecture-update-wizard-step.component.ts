import { Component, Input } from '@angular/core';
import { faCheck, faDotCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-lecture-update-wizard-step',
    templateUrl: './lecture-update-wizard-step.component.html',
    styleUrls: ['./lecture-update-wizard.component.scss'],
})
export class LectureUpdateWizardStepComponent {
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
