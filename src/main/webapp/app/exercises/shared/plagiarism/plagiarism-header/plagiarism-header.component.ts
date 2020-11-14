import { Component, Input } from '@angular/core';
import { Subject } from 'rxjs';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';

@Component({
    selector: 'jhi-plagiarism-header',
    styleUrls: ['./plagiarism-header.component.scss'],
    templateUrl: './plagiarism-header.component.html',
})
export class PlagiarismHeaderComponent {
    @Input() comparison: PlagiarismComparison;
    @Input() splitControlSubject: Subject<string>;

    /**
     * Update the status of the currently selected comparison.
     */
    confirmPlagiarism() {
        this.comparison.status = PlagiarismStatus.CONFIRMED;
    }

    /**
     * Update the status of the currently selected comparison.
     */
    denyPlagiarism() {
        this.comparison.status = PlagiarismStatus.DENIED;
    }

    expandSplitPane(pane: 'left' | 'right') {
        this.splitControlSubject.next(pane);
    }

    resetSplitPanes() {
        this.splitControlSubject.next('even');
    }
}
