import { Component, computed, input } from '@angular/core';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-language-table-cell',
    template: "<span>{{ 'artemisApp.exerciseAssessmentDashboard.languages.' + (textSubmission().language || 'UNKNOWN') | artemisTranslate }}</span>",
    imports: [ArtemisTranslatePipe],
})
export class LanguageTableCellComponent {
    readonly submission = input.required<Submission>();
    readonly textSubmission = computed(() => this.submission as TextSubmission);
}
