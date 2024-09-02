import { Component, Input } from '@angular/core';
import { Submission } from 'app/entities/submission.model';
import { TextSubmission } from 'app/entities/text/text-submission.model';

@Component({
    selector: 'jhi-language-table-cell',
    template: "<span>{{ 'artemisApp.exerciseAssessmentDashboard.languages.' + (textSubmission.language || 'UNKNOWN') | artemisTranslate }}</span>",
})
export class LanguageTableCellComponent {
    textSubmission: TextSubmission;

    @Input()
    set submission(submission: Submission) {
        this.textSubmission = submission as TextSubmission;
    }
}
