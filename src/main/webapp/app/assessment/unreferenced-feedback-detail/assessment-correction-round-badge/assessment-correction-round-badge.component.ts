import { Component, Input } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-assessment-correction-round-badge',
    templateUrl: './assessment-correction-round-badge.component.html',
    styleUrls: ['./assessment-correction-round-badge.component.scss'],
})
export class AssessmentCorrectionRoundBadgeComponent {
    @Input() feedback: Feedback;
    @Input() highlightDifferences = false;
}
