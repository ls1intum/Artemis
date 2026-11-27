import { Component, inject, input, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiConfirmDialogComponent, TumAetUiConfirmationService } from '@tumaet/ui-angular/confirm-dialog';
import { Exercise, getCourseId } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RouterModule } from '@angular/router';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons/faCircleInfo';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AlertService } from 'app/foundation/service/alert.service';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';

@Component({
    selector: 'jhi-plagiarism-header',
    styleUrls: ['./plagiarism-header.component.css'],
    templateUrl: './plagiarism-header.component.html',
    imports: [TranslateDirective, ArtemisTranslatePipe, RouterModule, FaIconComponent, TumAetUiButtonDirective, TumAetUiConfirmDialogComponent],
    providers: [TumAetUiConfirmationService],
})
export class PlagiarismHeaderComponent {
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private confirmationService = inject(TumAetUiConfirmationService);
    private translateService = inject(TranslateService);
    private alertService = inject(AlertService);

    readonly comparison = input<PlagiarismComparison>();
    readonly exercise = input.required<Exercise>();
    readonly splitControlSubject = input.required<Subject<string>>();

    readonly plagiarismStatus = PlagiarismStatus;
    readonly isLoading = signal(false);

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
        this.isLoading.set(true);

        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.plagiarism.denyAfterConfirmModalTitle'),
            message: this.translateService.instant('artemisApp.plagiarism.denyAfterConfirmModalText'),
            acceptLabel: this.translateService.instant('global.form.confirm'),
            acceptSeverity: 'danger',
            rejectLabel: this.translateService.instant('global.form.cancel'),
            accept: onConfirm,
            reject: () => this.isLoading.set(false),
        });
    }

    /**
     * Update the status of the currently selected comparison.
     * @param status the new status of the comparison
     */
    updatePlagiarismStatus(status: PlagiarismStatus) {
        this.isLoading.set(true);
        // store comparison in variable in case comparison changes while request is made
        const comparison = this.comparison();
        if (comparison && comparison.id) {
            const courseId = getCourseId(this.exercise());
            if (courseId === undefined) {
                this.alertService.error('error.courseIdUndefined');
                this.isLoading.set(false);
                return;
            }
            this.plagiarismCasesService.updatePlagiarismComparisonStatus(courseId, comparison.id, status).subscribe(() => {
                comparison.status = status;
                this.isLoading.set(false);
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
