import { Component, Input } from '@angular/core';
import { Subject } from 'rxjs';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';

// False-positives:
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-plagiarism-header',
    styleUrls: ['./plagiarism-header.component.scss'],
    templateUrl: './plagiarism-header.component.html',
})
export class PlagiarismHeaderComponent {
    @Input() comparison: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    @Input() course: Course;
    @Input() splitControlSubject: Subject<string>;

    readonly plagiarismStatus = PlagiarismStatus;

    constructor(private plagiarismCasesService: PlagiarismCasesService) {}

    /**
     * Set the status of the currently selected comparison to CONFIRMED.
     */
    confirmPlagiarism() {
        this.updatePlagiarismStatus(PlagiarismStatus.CONFIRMED);
    }

    /**
     * Set the status of the currently selected comparison to DENIED.
     */
    denyPlagiarism() {
        this.updatePlagiarismStatus(PlagiarismStatus.DENIED);
    }

    /**
     * Update the status of the currently selected comparison.
     * @param status the new status of the comparison
     */
    updatePlagiarismStatus(status: PlagiarismStatus) {
        // store comparison in variable in case comparison changes while request is made
        const comparison = this.comparison;
        this.plagiarismCasesService.updatePlagiarismComparisonStatus(this.course.id!, comparison.id, status).subscribe(() => {
            comparison.status = status;
        });
    }

    expandSplitPane(pane: 'left' | 'right') {
        this.splitControlSubject.next(pane);
    }

    resetSplitPanes() {
        this.splitControlSubject.next('even');
    }
}
