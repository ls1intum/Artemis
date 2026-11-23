import { Component, input } from '@angular/core';
import { TumAetUiTagComponent } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PresentationAssessment, PresentationAssessmentInstance, PresentationGradingMethod } from 'app/presentation/shared/entities/presentation-assessment.model';

/** Shows the result of an assessed instance as its grading method expresses it. */
@Component({
    selector: 'jhi-presentation-result-tag',
    templateUrl: './presentation-result-tag.component.html',
    imports: [TumAetUiTagComponent, ArtemisTranslatePipe],
})
export class PresentationResultTagComponent {
    readonly instance = input.required<PresentationAssessmentInstance>();
    readonly presentationAssessment = input.required<PresentationAssessment>();

    protected readonly PresentationGradingMethod = PresentationGradingMethod;
}
