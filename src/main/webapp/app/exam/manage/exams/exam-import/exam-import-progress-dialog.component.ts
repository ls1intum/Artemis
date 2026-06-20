import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Observable, Subscription } from 'rxjs';
import { DialogModule } from 'primeng/dialog';
import { ProgressBarModule } from 'primeng/progressbar';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheckCircle, faExclamationTriangle, faInfoCircle, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamImportProgress } from 'app/exam/shared/entities/exam-import-progress.model';
import { ExamImportResultDTO, ExerciseGroupImportResultDTO } from 'app/exam/shared/entities/exam-import-result.model';

/**
 * A modal that shows live progress of an exam (or exercise-group) import and a persistent, must-dismiss summary of the
 * outcome (success, or which exercises were skipped / left incomplete).
 * <p>
 * Modelled on {@code CourseOperationProgressComponent}: a PrimeNG dialog with a progress bar that is only closable once the
 * operation finished. Unlike a transient toast, the instructor has to acknowledge the result, so skipped/incomplete
 * exercises cannot be overlooked.
 * <p>
 * Usage: call {@link runImport} with a freshly generated {@code importId} and the import request observable. The returned
 * promise resolves with the import response once the user dismisses the dialog. Live progress arrives over the websocket
 * channel for that {@code importId}; the (authoritative) HTTP response decides the final skipped/incomplete summary.
 */
@Component({
    selector: 'jhi-exam-import-progress-dialog',
    imports: [DialogModule, ProgressBarModule, FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './exam-import-progress-dialog.component.html',
})
export class ExamImportProgressDialogComponent implements OnDestroy {
    private readonly examManagementService = inject(ExamManagementService);

    protected readonly faSpinner = faSpinner;
    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faInfoCircle = faInfoCircle;

    readonly visible = signal<boolean>(false);
    /** Set once the (authoritative) HTTP response arrived; gates closing the dialog. */
    readonly ready = signal<boolean>(false);
    readonly totalExercises = signal<number>(0);
    readonly processedExercises = signal<number>(0);
    readonly currentExerciseTitle = signal<string | undefined>(undefined);
    readonly skippedExercises = signal<string[]>([]);
    readonly incompleteExercises = signal<string[]>([]);

    readonly hasIssues = computed(() => this.skippedExercises().length > 0 || this.incompleteExercises().length > 0);
    readonly importedCount = computed(() => Math.max(0, this.totalExercises() - this.skippedExercises().length - this.incompleteExercises().length));
    readonly progressPercentage = computed(() => {
        const total = this.totalExercises();
        return total > 0 ? Math.round((this.processedExercises() / total) * 100) : 0;
    });

    private progressSubscription?: Subscription;
    private requestSubscription?: Subscription;
    private resolve?: (response: HttpResponse<ExamImportResultDTO | ExerciseGroupImportResultDTO>) => void;
    private lastResponse?: HttpResponse<ExamImportResultDTO | ExerciseGroupImportResultDTO>;

    /**
     * Runs the given import with a live-progress dialog.
     *
     * @param importId the id passed to the import request; the dialog subscribes to the matching websocket channel
     * @param totalExercises the number of exercises being imported (known by the caller); the final summary is computed from
     *        this and the (authoritative) HTTP response, so the counts are correct even if no websocket event ever arrives
     * @param request$ the import request (already carrying the importId)
     * @return a promise resolving with the import response once the user dismisses the dialog; rejects with the request
     *         error (e.g. a validation failure returned before the import starts) without showing a summary
     */
    runImport<T extends ExamImportResultDTO | ExerciseGroupImportResultDTO>(
        importId: string,
        totalExercises: number,
        request$: Observable<HttpResponse<T>>,
    ): Promise<HttpResponse<T>> {
        this.reset();
        this.totalExercises.set(totalExercises);
        this.progressSubscription = this.examManagementService.subscribeToImportProgress(importId).subscribe((progress) => this.applyProgress(progress));

        return new Promise<HttpResponse<T>>((resolve, reject) => {
            this.requestSubscription = request$.subscribe({
                next: (response) => {
                    // The HTTP response is authoritative for the final outcome (robust even if the terminal websocket event is lost):
                    // the skipped/incomplete titles come from the response, the total is the caller-provided count, so the summary is correct.
                    this.skippedExercises.set(response.body?.skippedExercises ?? []);
                    this.incompleteExercises.set(response.body?.incompleteExercises ?? []);
                    this.processedExercises.set(this.totalExercises());
                    this.ready.set(true);
                    this.visible.set(true);
                    this.lastResponse = response;
                    this.resolve = resolve as (response: HttpResponse<ExamImportResultDTO | ExerciseGroupImportResultDTO>) => void;
                },
                error: (error) => {
                    // Validation errors (e.g. duplicate short name) are returned before any progress; let the caller handle them as before.
                    this.cleanup();
                    reject(error);
                },
            });
        });
    }

    /**
     * Dismisses the result dialog and resolves the promise returned by {@link runImport}. No-op while the import is running.
     */
    onDismiss(): void {
        if (!this.resolve) {
            return;
        }
        const resolve = this.resolve;
        const response = this.lastResponse;
        this.cleanup();
        if (response) {
            resolve(response);
        }
    }

    private applyProgress(progress: ExamImportProgress): void {
        // The total is authoritative from the caller; live events only drive the progress bar and the current-exercise label.
        // Once the authoritative HTTP response arrived we stop letting live events move the bar/labels backwards.
        if (!this.ready()) {
            this.processedExercises.set(progress.processedExercises);
            this.currentExerciseTitle.set(progress.currentExerciseTitle);
            this.visible.set(true);
        }
    }

    private reset(): void {
        this.unsubscribeAll();
        this.resolve = undefined;
        this.lastResponse = undefined;
        this.ready.set(false);
        this.totalExercises.set(0);
        this.processedExercises.set(0);
        this.currentExerciseTitle.set(undefined);
        this.skippedExercises.set([]);
        this.incompleteExercises.set([]);
        this.visible.set(false);
    }

    private cleanup(): void {
        this.unsubscribeAll();
        this.resolve = undefined;
        this.visible.set(false);
    }

    private unsubscribeAll(): void {
        this.progressSubscription?.unsubscribe();
        this.progressSubscription = undefined;
        this.requestSubscription?.unsubscribe();
        this.requestSubscription = undefined;
    }

    ngOnDestroy(): void {
        this.unsubscribeAll();
    }
}
