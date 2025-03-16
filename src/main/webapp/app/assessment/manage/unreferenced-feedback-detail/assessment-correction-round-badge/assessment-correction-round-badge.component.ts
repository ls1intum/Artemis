import { Component, Input } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgStyle } from '@angular/common';

@Component({
    selector: 'jhi-assessment-correction-round-badge',
    templateUrl: './assessment-correction-round-badge.component.html',
    styleUrls: ['./assessment-correction-round-badge.component.scss'],
    imports: [TranslateDirective, NgStyle],
})
export class AssessmentCorrectionRoundBadgeComponent {
    @Input() feedback: Feedback;
    @Input() highlightDifferences = false;
}
