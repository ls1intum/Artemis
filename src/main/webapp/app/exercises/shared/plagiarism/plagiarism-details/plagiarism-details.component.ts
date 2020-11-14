import { Component, Input } from '@angular/core';
import { Subject } from 'rxjs';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';

@Component({
    selector: 'jhi-plagiarism-details',
    styleUrls: ['./plagiarism-details.component.scss'],
    templateUrl: './plagiarism-details.component.html',
})
export class PlagiarismDetailsComponent {
    @Input() comparison?: PlagiarismComparison;

    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();

    /**
     * Handle the 'splitViewChange' event emitted by PlagiarismHeaderComponent.
     *
     * @param pane
     */
    handleSplitViewChange(pane: string) {
        this.splitControlSubject.next(pane);
    }
}
