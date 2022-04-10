import { Component, Input } from '@angular/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';

@Component({
    selector: 'jhi-plagiarism-case-review',
    templateUrl: './plagiarism-case-review.component.html',
})
export class PlagiarismCaseReviewComponent {
    @Input() plagiarismCase: PlagiarismCase;
}
