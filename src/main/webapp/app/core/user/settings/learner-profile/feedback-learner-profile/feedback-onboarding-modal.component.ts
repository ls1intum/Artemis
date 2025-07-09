import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TextResultComponent } from 'app/text/overview/text-result/text-result.component';
import { FEEDBACK_EXAMPLES } from './feedback-examples';

@Component({
    selector: 'jhi-feedback-onboarding-modal',
    standalone: true,
    templateUrl: './feedback-onboarding-modal.component.html',
    styleUrls: ['./feedback-onboarding-modal.component.scss'],
    imports: [CommonModule, TextResultComponent],
})
export class FeedbackOnboardingModalComponent {
    step = 0;
    readonly totalSteps = 3;
    selected: (number | null)[] = [null, null, null];
    feedbackExamples = FEEDBACK_EXAMPLES;

    next() {
        if (this.step < this.totalSteps - 1) {
            this.step++;
        }
    }
    back() {
        if (this.step > 0) {
            this.step--;
        }
    }
    select(step: number, choice: number) {
        this.selected[step] = choice;
    }
    close() {
        // To be implemented: emit close event or call modal service
    }
    finish() {
        // To be implemented: save preferences and close
    }
}
