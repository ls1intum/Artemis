import { Component, OnInit, Input } from '@angular/core';
import { TextSubmission } from 'app/entities/text-submission.model';

@Component({
    selector: 'jhi-text-exam-summary',
    templateUrl: './text-exam-summary.component.html',
    styles: [],
})
export class TextExamSummaryComponent {
    @Input()
    submission: TextSubmission;

    constructor() {}
}
