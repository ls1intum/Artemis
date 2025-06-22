import { Component, inject, input } from '@angular/core';
import { Subject } from 'rxjs';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Exercise, getCourseId } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RouterModule } from '@angular/router';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AlertService } from 'app/shared/service/alert.service';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';

@Component({
    selector: 'jhi-plagiarism-header',
    styleUrls: ['./plagiarism-header.component.scss'],
    templateUrl: './plagiarism-header.component.html',
    imports: [TranslateDirective, ArtemisTranslatePipe, RouterModule, FaIconComponent],
})
export class PlagiarismHeaderComponent {
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private modalService = inject(NgbModal);
    private alertService = inject(AlertService);

    readonly comparison = input<PlagiarismComparison | undefined>(undefined);
    readonly exercise = input.required<Exercise>();
    readonly splitControlSubject = input.required<Subject<string>>();

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
        if (this.comparison()?.status === PlagiarismStatus.CONFIRMED) {
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
        const comparison = this.comparison();
        if (comparison && comparison.id) {
            const courseId = getCourseId(this.exercise());
            if (courseId === undefined) {
                this.alertService.error('error.courseIdUndefined');
                this.isLoading = false;
                return;
            }
            this.plagiarismCasesService.updatePlagiarismComparisonStatus(courseId, comparison.id, status).subscribe(() => {
                comparison.status = status;
                this.isLoading = false;
            });
        }
    }

    expandSplitPane(pane: 'left' | 'right') {
        this.splitControlSubject()?.next(pane);
    }

    resetSplitPanes() {
        this.splitControlSubject()?.next('even');
    }

    protected readonly faCircleInfo = faCircleInfo;
}
