import { Component, Input } from '@angular/core';
import { Submission } from 'app/entities/submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-language-table-cell',
    template: "<span>{{ 'artemisApp.exerciseAssessmentDashboard.languages.' + (textSubmission.language || 'UNKNOWN') | artemisTranslate }}</span>",
    imports: [ArtemisTranslatePipe],
})
export class LanguageTableCellComponent {
    textSubmission: TextSubmission;

    @Input()
    set submission(submission: Submission) {
        this.textSubmission = submission as TextSubmission;
    }
}
