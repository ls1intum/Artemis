import { Service, inject, signal } from '@angular/core';
import { HttpClient, HttpContext, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, ReplaySubject, Subscription, filter, interval, skip, take } from 'rxjs';
import { TumAetUiDialogService } from '@tumaet/ui-angular';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import {
    EntityDeletionProgress,
    EntityDeletionProgressDialogData,
    EntityDeletionStatus,
    EntityDeletionTarget,
    EntityDeletionType,
    isTerminalDeletionStatus,
} from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';
import { EntityDeletionProgressDialogComponent } from 'app/shared-ui/delete-dialog/component/entity-deletion-progress-dialog.component';

/** How often the status endpoint is read while a deletion runs, as a fallback for a websocket message that was missed. */
export const DELETION_STATUS_POLL_INTERVAL_MS = 5000;

/** A running deletion whose last update is older than this is treated as abandoned (e.g. its node restarted) and is not shown as a running deletion. */
export const STALE_DELETION_PROGRESS_MS = 10 * 60 * 1000;

/** Options of a deletion run. */
export interface EntityDeletionOptions {
    /**
     * Neither shows the progress dialog nor the success alert. For callers that delete many entities at once and report on the whole batch
     * themselves; the result observable still tells how each deletion ended.
     */
    silent?: boolean;
}

/**
 * Runs the deletion of an exercise, an exam or an exercise group, which the server executes in the background.
 * <p>
 * The server answers the DELETE request with 202 Accepted right away and reports progress over a websocket topic. This service
 * subscribes to that topic before it sends the request, shows a progress dialog and tells the caller how the deletion ended, so callers only
 * have to refresh their own state.
 * <p>
 * The websocket message that ends a deletion can get lost, for example while the connection is re-established. Therefore a running
 * deletion also re-reads the status endpoint when the websocket reconnects and at a regular interval until it ended.
 * <p>
 * The instructor can dismiss the dialog with "Continue in background" while the deletion runs. This only closes the dialog: the deletion
 * keeps running on the server and the run keeps following it, so the success alert is still shown and the result observable still emits
 * when it ended. Callers that react to the end by navigating must only do so while they are still on screen.
 */
@Service()
export class EntityDeletionService {
    private readonly http = inject(HttpClient);
    private readonly websocketService = inject(WebsocketService);
    private readonly dialogService = inject(TumAetUiDialogService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);

    /**
     * Sends the delete request and follows the deletion until it ended.
     *
     * @param target the entity that is deleted
     * @param request$ the DELETE request; the server must answer with 202 Accepted
     * @param options see {@link EntityDeletionOptions}
     * @return emits exactly once with the final progress (COMPLETED or FAILED) and completes; errors with the error of the request when
     *         the server refuses to start the deletion, in which case no dialog was shown. Unsubscribing does not stop the deletion
     *         and does not close the dialog.
     */
    delete(target: EntityDeletionTarget, request$: Observable<unknown>, options: EntityDeletionOptions = {}): Observable<EntityDeletionProgress> {
        const result$ = new ReplaySubject<EntityDeletionProgress>(1);
        const run = this.createRun(target, result$, options);

        // Subscribe first: the deletion may finish before the response of the request arrives.
        run.listen();
        request$.pipe(take(1)).subscribe({
            next: () => {
                if (!options.silent) {
                    run.openDialog();
                }
                run.readStatus();
                run.startFallback();
            },
            error: (error: unknown) => {
                run.stop();
                result$.error(error);
            },
        });
        return result$.asObservable();
    }

    /**
     * Shows the progress dialog of a deletion that is already running, for example after the page was reloaded or another instructor
     * started it. Does nothing when no deletion is running.
     *
     * @param target the entity that may be deleted right now
     * @return emits the final progress when the deletion that was found ended; completes without a value if none was running
     */
    resume(target: EntityDeletionTarget): Observable<EntityDeletionProgress> {
        const result$ = new ReplaySubject<EntityDeletionProgress>(1);
        const run = this.createRun(target, result$, {});
        this.http.get<EntityDeletionProgress>(this.statusUrl(target), { observe: 'response', context: this.silentContext() }).subscribe({
            next: (response) => {
                const progress = response.body;
                if (response.status !== 200 || !progress || progress.status !== EntityDeletionStatus.IN_PROGRESS || this.isStale(progress)) {
                    result$.complete();
                } else if (progress.entityType !== target.entityType || progress.entityId !== target.entityId) {
                    // The exam status is shared by the exam and its exercise groups: a deletion of something else must not block this page.
                    this.alertService.info('artemisApp.deletionProgress.otherDeletionRunning');
                    result$.complete();
                } else {
                    run.listen();
                    run.openDialog();
                    run.apply(progress);
                    run.startFallback();
                }
            },
            error: () => result$.complete(),
        });
        return result$.asObservable();
    }

    /** Websocket topic that carries the progress of the given deletion. */
    topic(target: EntityDeletionTarget): string {
        switch (target.entityType) {
            case EntityDeletionType.EXERCISE:
                return `/topic/exercises/${target.entityId}/deletion-progress`;
            case EntityDeletionType.EXAM:
            case EntityDeletionType.EXERCISE_GROUP:
                return `/topic/courses/${target.courseId}/exams/${target.examId ?? target.entityId}/deletion-progress`;
            default:
                throw new Error('Unknown entity deletion type');
        }
    }

    /** Endpoint that returns the latest known progress of the given deletion. */
    statusUrl(target: EntityDeletionTarget): string {
        switch (target.entityType) {
            case EntityDeletionType.EXERCISE:
                return `api/exercise/exercises/${target.entityId}/deletion-progress`;
            case EntityDeletionType.EXAM:
            case EntityDeletionType.EXERCISE_GROUP:
                return `api/exam/courses/${target.courseId}/exams/${target.examId ?? target.entityId}/deletion-progress`;
            default:
                throw new Error('Unknown entity deletion type');
        }
    }

    private isStale(progress: EntityDeletionProgress): boolean {
        return !!progress.updatedAt && dayjs().diff(dayjs(progress.updatedAt)) > STALE_DELETION_PROGRESS_MS;
    }

    /** A status read that fails, typically with 404 because the entity is already gone, must not pop up an error alert. */
    private silentContext(): HttpContext {
        return new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true);
    }

    private createRun(target: EntityDeletionTarget, result$: ReplaySubject<EntityDeletionProgress>, options: EntityDeletionOptions) {
        const progress = signal<EntityDeletionProgress | undefined>(undefined);
        let subscription: Subscription | undefined;
        let fallback: Subscription | undefined;
        let finished = false;
        let continuedInBackground = false;

        const matchesTarget = (message: EntityDeletionProgress) => message.entityType === target.entityType && message.entityId === target.entityId;

        const run = {
            listen: () => {
                subscription = this.websocketService.subscribe<EntityDeletionProgress>(this.topic(target)).subscribe((message) => run.apply(message));
            },
            stop: () => {
                subscription?.unsubscribe();
                subscription = undefined;
                fallback?.unsubscribe();
                fallback = undefined;
            },
            /** Re-reads the status when the websocket reconnects and at an interval, in case the message that ended the deletion was missed. */
            startFallback: () => {
                if (finished || fallback) {
                    return;
                }
                fallback = interval(DELETION_STATUS_POLL_INTERVAL_MS).subscribe(() => run.readStatus());
                fallback.add(
                    this.websocketService.connectionState
                        .pipe(
                            skip(1),
                            filter((state) => state.connected),
                        )
                        .subscribe(() => run.readStatus()),
                );
            },
            apply: (message: EntityDeletionProgress) => {
                // An exam topic also carries the exercise group deletions of that exam, and a late message must not move a finished run back.
                if (finished || !matchesTarget(message)) {
                    return;
                }
                if (message.startedAt) {
                    message.startedAt = dayjs(message.startedAt);
                }
                if (message.updatedAt) {
                    message.updatedAt = dayjs(message.updatedAt);
                }
                // Messages are sent through an async executor and can overtake each other, so the bar must never move backwards.
                const previousPercent = progress()?.weightedProgressPercent ?? 0;
                if (!isTerminalDeletionStatus(message.status) && message.weightedProgressPercent < previousPercent) {
                    message.weightedProgressPercent = previousPercent;
                }
                progress.set(message);
                if (!isTerminalDeletionStatus(message.status)) {
                    return;
                }
                finished = true;
                run.stop();
                if (message.status === EntityDeletionStatus.COMPLETED && !options.silent) {
                    this.alertService.success(target.successAlertKey, { param: target.title });
                } else if (message.status === EntityDeletionStatus.FAILED && continuedInBackground) {
                    // The dialog that would have shown the failure is gone, so the failure must not pass unnoticed.
                    this.alertService.error('artemisApp.deletionProgress.failedInBackground', { title: target.title, error: message.errorMessage ?? '' });
                }
                result$.next(message);
                result$.complete();
            },
            readStatus: () => {
                // Covers messages that were sent before the subscription was established.
                this.http.get<EntityDeletionProgress>(this.statusUrl(target), { observe: 'response', context: this.silentContext() }).subscribe({
                    next: (response: HttpResponse<EntityDeletionProgress>) => {
                        if (response.status === 200 && response.body) {
                            const progress = response.body;
                            // A deletion that stopped reporting (e.g. its node restarted) never sends a terminal message: end the run instead of polling forever.
                            run.apply(
                                progress.status === EntityDeletionStatus.IN_PROGRESS && this.isStale(progress)
                                    ? cloneWith(progress, { status: EntityDeletionStatus.FAILED })
                                    : progress,
                            );
                        }
                    },
                    error: (error: unknown) => {
                        // The status endpoint of an exercise or an exam answers 404 once the entity is gone, which after an accepted delete means the
                        // deletion completed and the terminal message was missed. Any other failure must not disturb the running deletion: the
                        // websocket stays the source of truth. (An exercise group's exam stays, so its status remains readable.)
                        if (error instanceof HttpErrorResponse && error.status === 404 && target.entityType !== EntityDeletionType.EXERCISE_GROUP) {
                            run.apply(run.goneProgress());
                        }
                    },
                });
            },
            goneProgress: (): EntityDeletionProgress => {
                const last = progress();
                return {
                    entityType: target.entityType,
                    entityId: target.entityId,
                    status: EntityDeletionStatus.COMPLETED,
                    stepsCompleted: last?.totalSteps ?? 0,
                    totalSteps: last?.totalSteps ?? 0,
                    itemsProcessed: 0,
                    totalItems: 0,
                    weightedProgressPercent: 100,
                };
            },
            openDialog: () => {
                const data: EntityDeletionProgressDialogData = {
                    target,
                    progress: progress.asReadonly(),
                    continueInBackground: () => {
                        continuedInBackground = true;
                    },
                };
                this.dialogService.open<EntityDeletionProgressDialogComponent, unknown, EntityDeletionProgressDialogData>(EntityDeletionProgressDialogComponent, {
                    ariaLabel: this.translateService.instant('artemisApp.deletionProgress.progressLabel'),
                    size: 'small',
                    closable: false,
                    closeOnEscape: false,
                    dismissableMask: false,
                    showHeader: false,
                    data,
                });
            },
        };
        return run;
    }
}
