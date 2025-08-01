import { Component, input } from '@angular/core';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgStyle } from '@angular/common';

@Component({
    selector: 'jhi-assessment-correction-round-badge',
    templateUrl: './assessment-correction-round-badge.component.html',
    styleUrls: ['./assessment-correction-round-badge.component.scss'],
    imports: [TranslateDirective, NgStyle],
})
export class AssessmentCorrectionRoundBadgeComponent {
    readonly feedback = input.required<Feedback>();
    readonly highlightDifferences = input(false);
}
