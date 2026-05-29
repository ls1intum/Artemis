import { Component, inject, input, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { Exercise, getCourseId } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RouterModule } from '@angular/router';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DialogModule } from 'primeng/dialog';
import { AlertService } from 'app/foundation/service/alert.service';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';

@Component({
    selector: 'jhi-plagiarism-header',
    styleUrls: ['./plagiarism-header.component.scss'],
    templateUrl: './plagiarism-header.component.html',
    imports: [TranslateDirective, ArtemisTranslatePipe, RouterModule, FaIconComponent, DialogModule],
})
export class PlagiarismHeaderComponent {
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private alertService = inject(AlertService);

    readonly comparison = input<PlagiarismComparison>();
    readonly exercise = input.required<Exercise>();
    readonly splitControlSubject = input.required<Subject<string>>();

    readonly plagiarismStatus = PlagiarismStatus;
    isLoading = false;

    /**
     * Controls the visibility of the "deny after confirm" confirmation dialog.
     */
    readonly denyAfterConfirmDialogVisible = signal(false);
    private denyAfterConfirmCallback: () => void = () => {};
    private denyAfterConfirmConfirmed = false;

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
        this.denyAfterConfirmConfirmed = false;
        this.denyAfterConfirmCallback = onConfirm;
        this.denyAfterConfirmDialogVisible.set(true);
    }

    /**
     * Confirm denying a previously confirmed plagiarism: mark as confirmed and close the dialog.
     * The actual callback is run in {@link onDenyAfterConfirmHide} once the dialog has closed.
     */
    confirmDenyAfterConfirm() {
        this.denyAfterConfirmConfirmed = true;
        this.denyAfterConfirmDialogVisible.set(false);
    }

    /**
     * Cancel denying a previously confirmed plagiarism by closing the dialog.
     * The loading state is reset in {@link onDenyAfterConfirmHide}.
     */
    cancelDenyAfterConfirm() {
        this.denyAfterConfirmDialogVisible.set(false);
    }

    /**
     * Handles the dialog closing (via confirm, cancel button, escape, mask or close icon).
     * Runs the stored callback if the user confirmed, otherwise resets the loading state.
     */
    onDenyAfterConfirmHide() {
        if (this.denyAfterConfirmConfirmed) {
            this.denyAfterConfirmConfirmed = false;
            this.denyAfterConfirmCallback();
        } else {
            this.isLoading = false;
        }
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
