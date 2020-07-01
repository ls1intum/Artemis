import { Component, Input } from '@angular/core';
import { TextSubmission } from 'app/entities/text-submission.model';

@Component({
    selector: 'jhi-text-exam-summary',
    template: '{{ submission.text }}',
    styles: [
        `
            :host {
                white-space: pre-wrap;
                display: block;
                background-color: #f8f9fa;
            }
        `,
    ],
})
export class TextExamSummaryComponent {
    @Input() submission: TextSubmission;
}
