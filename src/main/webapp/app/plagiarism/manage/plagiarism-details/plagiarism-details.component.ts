import { Component, input } from '@angular/core';
import { Subject } from 'rxjs';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { PlagiarismHeaderComponent } from '../plagiarism-header/plagiarism-header.component';
import { PlagiarismSplitViewComponent } from '../plagiarism-split-view/plagiarism-split-view.component';

@Component({
    selector: 'jhi-plagiarism-details',
    styleUrls: ['./plagiarism-details.component.scss'],
    templateUrl: './plagiarism-details.component.html',
    imports: [PlagiarismHeaderComponent, PlagiarismSplitViewComponent],
})
export class PlagiarismDetailsComponent {
    readonly comparison = input<PlagiarismComparison>();
    readonly exercise = input.required<Exercise>();

    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();
}
