import { TumAetUiTagComponent } from '@tumaet/ui-angular';
import { Component, input } from '@angular/core';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-assessment-correction-round-badge',
    templateUrl: './assessment-correction-round-badge.component.html',
    imports: [ArtemisTranslatePipe, TumAetUiTagComponent],
})
export class AssessmentCorrectionRoundBadgeComponent {
    readonly feedback = input.required<Feedback>();
    readonly highlightDifferences = input(false);
}
