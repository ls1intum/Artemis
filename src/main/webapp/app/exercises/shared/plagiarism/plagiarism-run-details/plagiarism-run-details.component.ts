import { Component, Input } from '@angular/core';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';

@Component({
    selector: 'jhi-plagiarism-run-details',
    styleUrls: ['./plagiarism-run-details.component.scss'],
    templateUrl: './plagiarism-run-details.component.html',
})
export class PlagiarismRunDetailsComponent {
    /**
     * Result of the automated plagiarism detection
     */
    @Input() plagiarismResult: TextPlagiarismResult | ModelingPlagiarismResult;
}
