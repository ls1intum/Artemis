import { Component, Input } from '@angular/core';
import { Subject } from 'rxjs';

import { Exercise } from 'app/entities/exercise.model';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
// False-positives:
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
// eslint-disable-next-line @typescript-eslint/no-unused-vars

@Component({
    selector: 'jhi-plagiarism-details',
    styleUrls: ['./plagiarism-details.component.scss'],
    templateUrl: './plagiarism-details.component.html',
})
export class PlagiarismDetailsComponent {
    @Input() comparison?: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    @Input() exercise: Exercise;

    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();
}
