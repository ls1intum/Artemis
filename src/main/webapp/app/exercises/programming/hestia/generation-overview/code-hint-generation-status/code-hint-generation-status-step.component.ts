import { Component, Input } from '@angular/core';
import { faCheck, faDotCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-code-hint-generation-status-step',
    templateUrl: './code-hint-generation-status-step.component.html',
    styleUrls: ['./code-hint-generation-status.component.scss'],
})
export class CodeHintGenerationStatusStepComponent {
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
