import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBell, faBellSlash } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiButtonDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { AccountService } from 'app/core/auth/account.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { BUILD_NOTIFICATIONS_LOCAL_STORAGE_KEY_PREFIX, LocalStorageService } from 'app/foundation/service/local-storage.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming/shared/services/programming-submission.service';
import dayjs from 'dayjs/esm';
import { getLatestResultOfStudentParticipation } from 'app/exercise/participation/participation.utils';

/** Observes only the participation displayed on the student exercise page; leaving it restores the tab. */
@Component({
    selector: 'jhi-programming-build-notifications',
    templateUrl: './programming-build-notifications.component.html',
    imports: [FaIconComponent, TumAetUiButtonDirective, TumAetUiTooltipDirective, ArtemisTranslatePipe],
})
export class ProgrammingBuildNotificationsComponent {
    private readonly document = inject(DOCUMENT);
    private readonly submissionService = inject(ProgrammingSubmissionService);
    private readonly participationWebsocketService = inject(ParticipationWebsocketService);
    private readonly accountService = inject(AccountService);
    private readonly localStorage = inject(LocalStorageService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    readonly exercise = input.required<Exercise>();
    readonly participation = input<StudentParticipation>();

    protected readonly supported = typeof Notification !== 'undefined' && window.isSecureContext;
    protected readonly permission = signal<NotificationPermission>(this.supported ? Notification.permission : 'denied');
    protected readonly optedIn = signal(false);
    protected readonly requestingPermission = signal(false);
    protected readonly notificationError = signal(false);
    protected readonly enabled = computed(() => this.optedIn() && this.permission() === 'granted');
    protected readonly toggleLabel = computed(() => {
        if (this.notificationError()) {
            return 'artemisApp.programmingExercise.buildNotifications.error';
        }
        if (this.permission() === 'denied') {
            return 'artemisApp.programmingExercise.buildNotifications.blocked';
        }
        return this.enabled() ? 'artemisApp.programmingExercise.buildNotifications.disable' : 'artemisApp.programmingExercise.buildNotifications.enable';
    });
    protected readonly faBell = faBell;
    protected readonly faBellSlash = faBellSlash;

    private readonly userId = computed(() => this.accountService.userIdentity()?.id);
    private readonly exerciseId = computed(() => this.exercise().id);
    private readonly participationId = computed(() => this.participation()?.id);
    private readonly originalIcons = new Map<HTMLLinkElement, { href: string | null; type: string | null; sizes: string | null }>();
    private pendingSubmissionId?: number;
    private pendingBuildStartedAt?: number;
    private latestResult?: Result;
    private initialReplay?: Result;
    private handledResult?: { source?: Result; revision?: string; submissionId?: number; completion?: number };
    private notification?: Notification;
    private closePersistentNotification?: () => void;
    /** Invalidates display promises when the page, user, participation or build changes. */
    private generation = 0;
    private feedbackGeneration = 0;
    private permissionRequest = 0;

    /** Owns observers only for the displayed participation and releases them with the page or account context. */
    constructor() {
        effect((onCleanup) => {
            const userId = this.userId();
            const exerciseId = this.exerciseId();
            const participationId = this.participationId();
            untracked(() => {
                this.readPreference();
                if (userId === undefined || exerciseId === undefined || participationId === undefined || this.exercise().type !== ExerciseType.PROGRAMMING) {
                    return;
                }
                let initialResult = true;
                let hasNoPendingSubmission = false;
                const resultSubscription = this.participationWebsocketService.subscribeForLatestResultOfParticipation(participationId, true, exerciseId).subscribe((result) => {
                    if (initialResult) {
                        // This BehaviorSubject immediately replays its previous result. It is the
                        // baseline, not completion of a build that may already be running again.
                        initialResult = false;
                        this.latestResult = result;
                        this.initialReplay = result;
                        this.rememberHandledResult(result ?? getLatestResultOfStudentParticipation(this.participation(), true));
                        return;
                    }
                    this.onResult(result);
                });
                const submissionSubscription = this.submissionService
                    .getLatestPendingSubmissionByParticipationId(participationId, exerciseId, true)
                    .subscribe(({ submissionState, submission, buildTimingInfo }) => {
                        hasNoPendingSubmission = submissionState === ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION;
                        if (submissionState === ProgrammingSubmissionState.IS_QUEUED || submissionState === ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION) {
                            const buildStart = buildTimingInfo?.buildStartDate ?? submission?.buildStartDate;
                            const startedAt = buildStart ? dayjs(buildStart).valueOf() : undefined;
                            if (
                                startedAt !== undefined &&
                                this.handledResult?.submissionId === submission?.id &&
                                this.latestResult !== this.initialReplay &&
                                this.isHandledResult(this.latestResult) &&
                                this.handledResult?.completion !== undefined &&
                                this.handledResult.completion >= startedAt
                            ) {
                                return;
                            }
                            if (this.pendingSubmissionId !== submission?.id) {
                                this.clearFeedback();
                            }
                            this.pendingSubmissionId = submission?.id;
                            this.pendingBuildStartedAt = startedAt;
                            this.setFavicon('building');
                            // The initial pending-submission GET can resolve after the result websocket.
                            // Reconcile its snapshot with the result we already received.
                            const replayCompletion = this.completionTime(this.initialReplay);
                            const initialReplayMatches =
                                this.latestResult === this.initialReplay && startedAt !== undefined && replayCompletion !== undefined && replayCompletion >= startedAt;
                            this.onResult(this.latestResult, initialReplayMatches);
                            if (this.pendingSubmissionId !== undefined) {
                                const pendingSubmissionId = this.pendingSubmissionId;
                                const feedbackGeneration = this.feedbackGeneration;
                                // An initial REST snapshot can synchronously replay building -> no-pending
                                // before the UI renders. Drop only that transient progress state. A later
                                // fallback GET replaying an old result must not hide a genuine rebuild.
                                window.queueMicrotask(() => {
                                    if (
                                        !this.destroyRef.destroyed &&
                                        feedbackGeneration === this.feedbackGeneration &&
                                        hasNoPendingSubmission &&
                                        this.pendingSubmissionId === pendingSubmissionId &&
                                        !this.predatesPendingBuild(this.latestResult)
                                    ) {
                                        this.clearFeedback();
                                    }
                                });
                            }
                        } else if (submissionState === ProgrammingSubmissionState.HAS_FAILED_SUBMISSION) {
                            // A timeout is not completion. Keep the observed submission relevant if its real
                            // result arrives later, while removing the now-unreliable progress indication.
                            this.clearFeedback();
                        }
                        // HAS_NO_PENDING_SUBMISSION is also emitted before the result reaches us. Only the
                        // matching result below confirms completion; an empty state alone is not a result.
                    });
                onCleanup(() => {
                    submissionSubscription.unsubscribe();
                    resultSubscription.unsubscribe();
                    this.submissionService.unsubscribeForLatestSubmissionOfParticipation(participationId);
                    this.pendingSubmissionId = undefined;
                    this.pendingBuildStartedAt = undefined;
                    this.latestResult = undefined;
                    this.initialReplay = undefined;
                    this.handledResult = undefined;
                    this.permissionRequest++;
                    this.requestingPermission.set(false);
                    this.notificationError.set(false);
                    this.clearFeedback();
                });
            });
        });
        this.document.addEventListener('visibilitychange', this.onReturn);
        window.addEventListener('focus', this.onReturn);
        window.addEventListener('storage', this.onStorage);
        this.destroyRef.onDestroy(() => {
            this.document.removeEventListener('visibilitychange', this.onReturn);
            window.removeEventListener('focus', this.onReturn);
            window.removeEventListener('storage', this.onStorage);
            this.clearFeedback();
        });
    }

    /** Requests permission only from the opt-in gesture, and keeps notification opt-out independent of favicons. */
    protected async toggleNotifications(): Promise<void> {
        this.notificationError.set(false);
        const userId = this.userId();
        if (!this.supported || userId === undefined || this.requestingPermission()) {
            return;
        }
        if (this.enabled()) {
            this.optedIn.set(false);
            this.clearNotifications();
            try {
                this.localStorage.store(BUILD_NOTIFICATIONS_LOCAL_STORAGE_KEY_PREFIX + userId, false);
            } catch {
                this.notificationError.set(true);
            }
            return;
        }
        const permissionRequest = ++this.permissionRequest;
        this.requestingPermission.set(true);
        try {
            // Keep this call directly in the click handler: browsers require a user gesture.
            const permission = Notification.permission === 'default' ? await Notification.requestPermission() : Notification.permission;
            if (this.destroyRef.destroyed || permissionRequest !== this.permissionRequest || userId !== this.userId()) {
                return;
            }
            this.permission.set(permission);
            this.optedIn.set(permission === 'granted');
            this.localStorage.store(BUILD_NOTIFICATIONS_LOCAL_STORAGE_KEY_PREFIX + userId, permission === 'granted');
        } catch {
            if (!this.destroyRef.destroyed && permissionRequest === this.permissionRequest) {
                this.notificationError.set(true);
            }
        } finally {
            if (!this.destroyRef.destroyed && permissionRequest === this.permissionRequest) {
                this.requestingPermission.set(false);
            }
        }
    }

    /** Accepts only a fresh matching result, using build timing when the existing stream supplies it. */
    private onResult(result: Result | undefined, initialReplayMatches = false): void {
        if (result) {
            this.latestResult = result;
        }
        if (
            this.pendingSubmissionId === undefined ||
            result?.submission?.id !== this.pendingSubmissionId ||
            (this.isHandledResult(result) && !initialReplayMatches) ||
            this.predatesPendingBuild(result)
        ) {
            return;
        }
        const submissionId = this.pendingSubmissionId;
        this.rememberHandledResult(result);
        this.initialReplay = undefined;
        this.pendingSubmissionId = undefined;
        this.pendingBuildStartedAt = undefined;
        this.clearFeedback();
        if (!this.isAway()) {
            return;
        }
        this.setFavicon('result');
        if (this.enabled() && Notification.permission === 'granted') {
            void this.showNotification(submissionId, this.resultRevision(result) ?? 'event-' + this.generation);
        }
    }

    private isAway(): boolean {
        return this.document.hidden || !this.document.hasFocus();
    }

    /** Snapshots value identity so a producer can mutate and re-emit the same result object safely. */
    private rememberHandledResult(result: Result | undefined): void {
        this.handledResult = {
            source: result,
            revision: this.resultRevision(result),
            submissionId: result?.submission?.id,
            completion: this.completionTime(result),
        };
    }

    /** Deduplicates persisted revisions, while retaining reference identity for unpersisted results. */
    private isHandledResult(result: Result | undefined): boolean {
        return result?.id === undefined ? result === this.handledResult?.source : this.resultRevision(result) === this.handledResult?.revision;
    }

    /** A semi-automatic result may keep its ID across builds, so completion time is part of its revision. */
    private resultRevision(result: Result | undefined): string | undefined {
        if (result?.id === undefined) {
            return undefined;
        }
        const completion = this.completionTime(result);
        return String(result.id) + (completion === undefined ? '' : '@' + completion);
    }

    /** Normalizes both websocket date strings and converted Dayjs dates without inventing a date for missing values. */
    private completionTime(result: Result | undefined): number | undefined {
        return result?.completionDate ? dayjs(result.completionDate).valueOf() : undefined;
    }

    /** An old completion cannot finish a rebuild whose processing is known to have started later. */
    private predatesPendingBuild(result: Result | undefined): boolean {
        const completion = this.completionTime(result);
        return completion !== undefined && this.pendingBuildStartedAt !== undefined && completion < this.pendingBuildStartedAt;
    }

    /** Preference storage is optional; a blocked storage API must not prevent build subscriptions. */
    private readPreference(): void {
        try {
            const userId = this.userId();
            this.optedIn.set(userId !== undefined && this.localStorage.retrieve<boolean>(BUILD_NOTIFICATIONS_LOCAL_STORAGE_KEY_PREFIX + userId) === true);
        } catch {
            this.optedIn.set(false);
        }
    }

    /** Applies another tab's preference changes without clearing the independent favicon feedback. */
    private readonly onStorage = (event: StorageEvent) => {
        if (event.key === null || event.key === BUILD_NOTIFICATIONS_LOCAL_STORAGE_KEY_PREFIX + this.userId()) {
            this.permissionRequest++;
            this.requestingPermission.set(false);
            if (this.supported) {
                this.permission.set(Notification.permission);
            }
            this.readPreference();
            if (!this.optedIn()) {
                this.clearNotifications();
            }
        }
    };

    /** Acknowledges feedback only when the exercise tab is visible and focused. */
    private readonly onReturn = () => {
        if (this.supported) {
            this.permission.set(Notification.permission);
        }
        if (!this.isAway()) {
            this.clearFeedback();
        }
    };

    /** Updates every advertised icon variant, retaining all attributes needed to restore the original links. */
    private setFavicon(state: 'building' | 'result'): void {
        this.document.querySelectorAll<HTMLLinkElement>('link[rel~="icon"]').forEach((icon) => {
            if (!this.originalIcons.has(icon)) {
                this.originalIcons.set(icon, { href: icon.getAttribute('href'), type: icon.getAttribute('type'), sizes: icon.getAttribute('sizes') });
            }
            icon.href = 'content/images/programming/favicon-' + state + '.svg';
            icon.type = 'image/svg+xml';
            icon.setAttribute('sizes', 'any');
        });
    }

    /** Restores tab branding and discards notification work for the acknowledged or abandoned build. */
    private clearFeedback(): void {
        this.feedbackGeneration++;
        this.clearNotifications();
        this.originalIcons.forEach((attributes, icon) => {
            for (const [name, value] of Object.entries(attributes)) {
                if (value === null) {
                    icon.removeAttribute(name);
                } else {
                    icon.setAttribute(name, value);
                }
            }
        });
        this.originalIcons.clear();
    }

    /** Cancels only notification resources, leaving build correlation and favicon feedback intact. */
    private clearNotifications(): void {
        this.generation++;
        this.notification?.close();
        this.notification = undefined;
        this.closePersistentNotification?.();
        this.closePersistentNotification = undefined;
    }

    /** Uses an active worker where available and scopes asynchronous cleanup to this result revision. */
    private async showNotification(submissionId: number, revision: string): Promise<void> {
        const generation = this.generation;
        const tag = 'artemis-build-' + this.userId() + '-' + this.participationId() + '-' + submissionId + '-' + revision;
        const title = this.translateService.instant('artemisApp.programmingExercise.buildNotifications.resultReceived');
        const options: NotificationOptions = {
            body: this.exercise().title,
            icon: new URL('logo/android-chrome-192x192.png', this.document.baseURI).href,
            tag,
            silent: true,
            data: { onActionClick: { default: { operation: 'navigateLastFocusedOrOpen', url: this.document.location.href } } },
        };
        const stillRelevant = () => !this.destroyRef.destroyed && generation === this.generation && this.enabled() && this.isAway() && Notification.permission === 'granted';
        try {
            const registration = await navigator.serviceWorker?.getRegistration();
            if (!stillRelevant()) {
                return;
            }
            if (registration?.active) {
                const close = () => {
                    void registration
                        .getNotifications({ tag })
                        .then((notifications) => notifications.forEach((notification) => notification.close()))
                        .catch(() => undefined);
                };
                this.closePersistentNotification = close;
                await registration.showNotification(title, options);
                if (!stillRelevant()) {
                    close();
                }
            } else {
                this.notification = new Notification(title, options);
                this.notification.onclick = () => {
                    window.focus();
                    this.clearFeedback();
                };
            }
        } catch {
            // Mobile browsers may expose Notification but require a service worker. Delivery can also be
            // blocked by platform settings. The favicon remains usable when system notification delivery fails.
            if (!this.destroyRef.destroyed && generation === this.generation) {
                this.notificationError.set(true);
            }
        }
    }
}
