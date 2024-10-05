import { Component, Input, inject } from '@angular/core';
import { Subject } from 'rxjs';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Exercise, getCourseId } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-plagiarism-header',
    styleUrls: ['./plagiarism-header.component.scss'],
    templateUrl: './plagiarism-header.component.html',
})
export class PlagiarismHeaderComponent {
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private modalService = inject(NgbModal);

    @Input() comparison: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    @Input() exercise: Exercise;
    @Input() splitControlSubject: Subject<string>;

    readonly plagiarismStatus = PlagiarismStatus;
    isLoading = false;

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
        if (this.comparison.status === PlagiarismStatus.CONFIRMED) {
            this.askForConfirmationOfDenying(() => this.updatePlagiarismStatus(PlagiarismStatus.DENIED));
        } else {
            this.updatePlagiarismStatus(PlagiarismStatus.DENIED);
        }
    }

    private askForConfirmationOfDenying(onConfirm: () => void) {
        this.isLoading = true;

        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.title = 'artemisApp.plagiarism.denyAfterConfirmModalTitle';
        modalRef.componentInstance.text = 'artemisApp.plagiarism.denyAfterConfirmModalText';
        modalRef.componentInstance.translateText = true;
        modalRef.result.then(onConfirm, () => (this.isLoading = false));
    }

    /**
     * Update the status of the currently selected comparison.
     * @param status the new status of the comparison
     */
    updatePlagiarismStatus(status: PlagiarismStatus) {
        this.isLoading = true;
        // store comparison in variable in case comparison changes while request is made
        const comparison = this.comparison;
        this.plagiarismCasesService.updatePlagiarismComparisonStatus(getCourseId(this.exercise)!, comparison.id, status).subscribe(() => {
            comparison.status = status;
            this.isLoading = false;
        });
    }

    expandSplitPane(pane: 'left' | 'right') {
        this.splitControlSubject.next(pane);
    }

    resetSplitPanes() {
        this.splitControlSubject.next('even');
    }
}
