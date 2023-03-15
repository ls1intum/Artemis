import { Component, Input } from '@angular/core';
import { Subject } from 'rxjs';

import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';

@Component({
    selector: 'jhi-plagiarism-case-review',
    templateUrl: './plagiarism-case-review.component.html',
})
export class PlagiarismCaseReviewComponent {
    @Input() plagiarismCase: PlagiarismCase;
    @Input() forStudent = true;

    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();
}
