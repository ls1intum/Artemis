import { Component, Input } from '@angular/core';
import { Subject } from 'rxjs';
import { PlagiarismComparison } from 'app/plagiarism/shared/types/PlagiarismComparison';
import { Exercise } from 'app/exercise/entities/exercise.model';
import { TextSubmissionElement } from 'app/plagiarism/shared/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/plagiarism/shared/types/modeling/ModelingSubmissionElement';
import { PlagiarismHeaderComponent } from '../plagiarism-header/plagiarism-header.component';
import { PlagiarismSplitViewComponent } from '../plagiarism-split-view/plagiarism-split-view.component';

@Component({
    selector: 'jhi-plagiarism-details',
    styleUrls: ['./plagiarism-details.component.scss'],
    templateUrl: './plagiarism-details.component.html',
    imports: [PlagiarismHeaderComponent, PlagiarismSplitViewComponent],
})
export class PlagiarismDetailsComponent {
    @Input() comparison?: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    @Input() exercise: Exercise;

    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();
}
