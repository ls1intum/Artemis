import { DestroyRef, Service, inject, signal } from '@angular/core';
import { HttpClient, HttpContext, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, ReplaySubject, Subscription, filter, finalize, interval, skip, take, takeUntil } from 'rxjs';
import { TumAetUiDialogRef, TumAetUiDialogService } from '@tumaet/ui-angular';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { AlertService } from 'app/foundation/service/alert.service';
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

/** Options of a deletion run. */
export interface EntityDeletionOptions {
    /**
     * Neither shows the progress dialog nor the success alert. For callers that delete many entities at once and refresh their view when the
     * batch ended; the result observable still tells how each deletion ended. A deletion that fails is reported with an alert by this service
     * itself, so the failure is not lost when the caller is gone by then.
     */
    silent?: boolean;
}

/** The part of a run that other runs of the same entity and the service need. */
interface DeletionRun {
    readonly result$: ReplaySubject<EntityDeletionProgress>;
    listen(): void;
    stop(): void;
    startFallback(): void;
    apply(message: EntityDeletionProgress): void;
    readStatus(): void;
    openDialog(): void;
    /** Opens the dialog again if the instructor sent it to the background. */
    reattach(): void;
    /** Makes the run the one that follows its entity. */
    register(): void;
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
 * when it ended. Callers that react to the end by navigating must only do so while they are still on screen and still show the deleted entity.
 * <p>
 * There is at most one run per entity: {@link resume} attaches to the run that is already following the entity and reopens its dialog,
 * instead of starting a second one that would poll and alert on its own.
 * <p>
 * The service never judges a running deletion by the clock of the client. A deletion that stopped reporting is ended by the server, which
 * then reports it as FAILED.
 */
@Service()
export class EntityDeletionService {
    private readonly http = inject(HttpClient);
    private readonly websocketService = inject(WebsocketService);
    private readonly dialogService = inject(TumAetUiDialogService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);

    /** The runs that follow a deletion right now, one per entity. */
    private readonly activeRuns = new Map<string, DeletionRun>();

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
                run.register();
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
     * started it. Does nothing when no deletion is running. When this client already follows the deletion, for example because the instructor
     * sent it to the background and came back, that run is reused and its dialog is shown again.
     *
     * @param target the entity that may be deleted right now
     * @param lifetime lifetime of the caller: the {@link DestroyRef} of the caller, or an observable that emits when the check is no longer wanted,
     *        for example because a reused component now shows another entity. The status request is cancelled then, and no dialog is opened afterwards
     * @return emits the final progress when the deletion that was found ended; completes without a value if none was running
     */
    resume(target: EntityDeletionTarget, lifetime: DestroyRef | Observable<unknown>): Observable<EntityDeletionProgress> {
        const existing = this.activeRuns.get(this.runKey(target));
        if (existing) {
            existing.reattach();
            return existing.result$.asObservable();
        }
        const result$ = new ReplaySubject<EntityDeletionProgress>(1);
        let attached = false;
        const until$ =
            lifetime instanceof Observable
                ? lifetime
                : new Observable<void>((subscriber) =>
                      lifetime.onDestroy(() => {
                          subscriber.next();
                          subscriber.complete();
                      }),
                  );
        this.http
            .get<EntityDeletionProgress>(this.statusUrl(target), { observe: 'response', context: this.silentContext() })
            .pipe(takeUntil(until$))
            .subscribe({
                next: (response) => {
                    const progress = response.body;
                    if (response.status !== 200 || !progress || progress.status !== EntityDeletionStatus.IN_PROGRESS) {
                        return;
                    }
                    if (progress.entityType !== target.entityType || progress.entityId !== target.entityId) {
                        // The exam status is shared by the exam and its exercise groups: a deletion of something else must not block this page.
                        this.alertService.info('artemisApp.deletionProgress.otherDeletionRunning');
                        return;
                    }
                    attached = true;
                    // Another resume may have started following the entity while the status was on its way.
                    const meanwhile = this.activeRuns.get(this.runKey(target));
                    if (meanwhile) {
                        meanwhile.reattach();
                        meanwhile.result$.subscribe(result$);
                        return;
                    }
                    const run = this.createRun(target, result$, {});
                    run.register();
                    run.listen();
                    run.openDialog();
                    run.apply(progress);
                    run.startFallback();
                },
                error: () => result$.complete(),
                // The status is a single response, so without a running deletion to follow there is nothing left to wait for.
                complete: () => {
                    if (!attached) {
                        result$.complete();
                    }
                },
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

    private runKey(target: EntityDeletionTarget): string {
        return `${target.entityType}:${target.entityId}`;
    }

    /** A status read that fails, typically because the entity is already gone, must not pop up an error alert. */
    private silentContext(): HttpContext {
        return new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true);
    }

    private createRun(target: EntityDeletionTarget, result$: ReplaySubject<EntityDeletionProgress>, options: EntityDeletionOptions): DeletionRun {
        const progress = signal<EntityDeletionProgress | undefined>(undefined);
        let subscription: Subscription | undefined;
        let fallback: Subscription | undefined;
        let dialogRef: TumAetUiDialogRef | undefined;
        let dialogOpen = false;
        let finished = false;
        let continuedInBackground = false;

        const matchesTarget = (message: EntityDeletionProgress) => message.entityType === target.entityType && message.entityId === target.entityId;

        // At most one status read is in flight per run: when a read takes longer than the polling interval, the next trigger is remembered
        // and runs once the read ended, instead of piling up requests.
        let readInFlight = false;
        let readAgain = false;
        let statusRead: Subscription | undefined;

        /** The progress that ends a run on the client side, when the server did not send the terminal message. */
        const endedProgress = (status: EntityDeletionStatus.COMPLETED | EntityDeletionStatus.UNKNOWN): EntityDeletionProgress => {
            const last = progress();
            return {
                entityType: target.entityType,
                entityId: target.entityId,
                status,
                stepsCompleted: last?.totalSteps ?? 0,
                totalSteps: last?.totalSteps ?? 0,
                itemsProcessed: 0,
                totalItems: 0,
                weightedProgressPercent: 100,
            };
        };

        const run: DeletionRun = {
            result$,
            register: () => {
                // A run whose terminal message arrived before the response of the request is over; later resumes must check the server instead
                // of replaying its old outcome.
                if (!finished) {
                    this.activeRuns.set(this.runKey(target), run);
                }
            },
            listen: () => {
                subscription = this.websocketService.subscribe<EntityDeletionProgress>(this.topic(target)).subscribe((message) => run.apply(message));
            },
            stop: () => {
                subscription?.unsubscribe();
                subscription = undefined;
                fallback?.unsubscribe();
                fallback = undefined;
                readAgain = false;
                statusRead?.unsubscribe();
                statusRead = undefined;
                readInFlight = false;
                if (this.activeRuns.get(this.runKey(target)) === run) {
                    this.activeRuns.delete(this.runKey(target));
                }
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
                if (message.status === EntityDeletionStatus.UNKNOWN) {
                    // The dialog has nothing to tell about an outcome that is not known; the caller refreshes its view from the server.
                    dialogRef?.close();
                } else if (message.status === EntityDeletionStatus.COMPLETED && !options.silent) {
                    this.alertService.success(target.successAlertKey, { param: target.title });
                } else if (message.status === EntityDeletionStatus.FAILED && (continuedInBackground || options.silent)) {
                    // The dialog that would have shown the failure is gone or was never shown, so the failure must not pass unnoticed.
                    this.alertService.error('artemisApp.deletionProgress.failedInBackground', { title: target.title, error: message.errorMessage ?? '' });
                }
                result$.next(message);
                result$.complete();
            },
            readStatus: () => {
                if (finished) {
                    return;
                }
                if (readInFlight) {
                    readAgain = true;
                    return;
                }
                readInFlight = true;
                // Covers messages that were sent before the subscription was established.
                statusRead = this.http
                    .get<EntityDeletionProgress>(this.statusUrl(target), { observe: 'response', context: this.silentContext() })
                    .pipe(
                        finalize(() => {
                            readInFlight = false;
                            if (readAgain) {
                                readAgain = false;
                                run.readStatus();
                            }
                        }),
                    )
                    .subscribe({
                        next: (response: HttpResponse<EntityDeletionProgress>) => {
                            const status = response.body;
                            if (response.status !== 200 || !status || finished) {
                                return;
                            }
                            if (matchesTarget(status)) {
                                run.apply(status);
                                return;
                            }
                            // The exam status is shared by the exam and its exercise groups and only describes the latest operation. A status of
                            // another entity means that ours is over, but how it ended is not known any more. The run ends without any alert and
                            // reports an UNKNOWN outcome, so the caller refreshes its view from the server, which is the only reliable source.
                            run.apply(endedProgress(EntityDeletionStatus.UNKNOWN));
                        },
                        error: (error: unknown) => {
                            // After an accepted delete, an exercise or an exam that cannot be read any more is gone when the status endpoint answers
                            // 404: the deletion completed and its terminal message was missed. A 403 is not proof of that, the instructor may have
                            // lost access while the entity still exists, so the outcome is unknown. Any other failure must not disturb the running
                            // deletion, the websocket stays the source of truth. (The exam of an exercise group stays, so its status remains readable.)
                            if (!(error instanceof HttpErrorResponse) || target.entityType === EntityDeletionType.EXERCISE_GROUP) {
                                return;
                            }
                            if (error.status === 404) {
                                run.apply(endedProgress(EntityDeletionStatus.COMPLETED));
                            } else if (error.status === 403) {
                                run.apply(endedProgress(EntityDeletionStatus.UNKNOWN));
                            }
                        },
                    });
            },
            openDialog: () => {
                const data: EntityDeletionProgressDialogData = {
                    target,
                    progress: progress.asReadonly(),
                    continueInBackground: () => {
                        continuedInBackground = true;
                        dialogOpen = false;
                    },
                };
                dialogOpen = true;
                dialogRef = this.dialogService.open<EntityDeletionProgressDialogComponent, unknown, EntityDeletionProgressDialogData>(EntityDeletionProgressDialogComponent, {
                    ariaLabel: this.translateService.instant('artemisApp.deletionProgress.progressLabel'),
                    size: 'small',
                    closable: false,
                    closeOnEscape: false,
                    dismissableMask: false,
                    showHeader: false,
                    data,
                });
            },
            reattach: () => {
                if (finished || dialogOpen || options.silent) {
                    return;
                }
                // The dialog shows a failure itself again, so the alert for a deletion in the background is not needed any more.
                continuedInBackground = false;
                run.openDialog();
            },
        };
        return run;
    }
}
