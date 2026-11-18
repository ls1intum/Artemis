import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PresentationAssessment, PresentationAssessmentInstance, PresentationAssessmentMode } from 'app/presentation/shared/entities/presentation-assessment.model';

/** Shows how and where a student presents. Given a presentation, its description and exercise are listed first. */
@Component({
    selector: 'jhi-presentation-assessment-instance-details',
    templateUrl: './presentation-assessment-instance-details.component.html',
    imports: [TranslateDirective, ArtemisTranslatePipe],
})
export class PresentationAssessmentInstanceDetailsComponent {
    protected readonly PresentationAssessmentMode = PresentationAssessmentMode;

    readonly instance = input.required<PresentationAssessmentInstance>();
    readonly presentationAssessment = input<PresentationAssessment>();
}
