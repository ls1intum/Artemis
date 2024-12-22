import { Component, input } from '@angular/core';
import { Subject } from 'rxjs';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { Exercise } from 'app/entities/exercise.model';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { PlagiarismHeaderComponent } from '../plagiarism-header/plagiarism-header.component';
import { PlagiarismSplitViewComponent } from '../plagiarism-split-view/plagiarism-split-view.component';

@Component({
    selector: 'jhi-plagiarism-details',
    styleUrls: ['./plagiarism-details.component.scss'],
    templateUrl: './plagiarism-details.component.html',
    standalone: true,
    imports: [PlagiarismHeaderComponent, PlagiarismSplitViewComponent],
})
export class PlagiarismDetailsComponent {
    comparison = input<PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>>();
    exercise = input.required<Exercise>();

    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();
}
