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
    private latestResult?: Result;
    private lastHandledResult?: Result;
    private notification?: Notification;
    private closePersistentNotification?: () => void;
    /** Invalidates display promises when the page, user, participation or build changes. */
    private generation = 0;
    private permissionRequest = 0;

    constructor() {
        effect((onCleanup) => {
            const userId = this.userId();
            const exerciseId = this.exerciseId();
            const participationId = this.participationId();
            untracked(() => {
                this.optedIn.set(userId !== undefined && this.localStorage.retrieve<boolean>(BUILD_NOTIFICATIONS_LOCAL_STORAGE_KEY_PREFIX + userId) === true);
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
                        this.lastHandledResult = result ?? getLatestResultOfStudentParticipation(this.participation(), true);
                        return;
                    }
                    this.onResult(result);
                });
                const submissionSubscription = this.submissionService
                    .getLatestPendingSubmissionByParticipationId(participationId, exerciseId, true)
                    .subscribe(({ submissionState, submission }) => {
                        hasNoPendingSubmission = submissionState === ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION;
                        if (submissionState === ProgrammingSubmissionState.IS_QUEUED || submissionState === ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION) {
                            if (this.pendingSubmissionId !== submission?.id) {
                                this.clearFeedback();
                            }
                            this.pendingSubmissionId = submission?.id;
                            this.setFavicon('building');
                            // The initial pending-submission GET can resolve after the result websocket.
                            // Reconcile its snapshot with the result we already received.
                            if (!this.isHandledResult(this.latestResult)) {
                                this.onResult(this.latestResult);
                            }
                            if (this.pendingSubmissionId !== undefined) {
                                const pendingSubmissionId = this.pendingSubmissionId;
                                const generation = this.generation;
                                // An initial REST snapshot can synchronously replay building -> no-pending
                                // before the UI renders. Drop only that transient progress state. A later
                                // fallback GET replaying an old result must not hide a genuine rebuild.
                                window.queueMicrotask(() => {
                                    if (
                                        !this.destroyRef.destroyed &&
                                        generation === this.generation &&
                                        hasNoPendingSubmission &&
                                        this.pendingSubmissionId === pendingSubmissionId
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
                    this.latestResult = undefined;
                    this.lastHandledResult = undefined;
                    this.permissionRequest++;
                    this.requestingPermission.set(false);
                    this.notificationError.set(false);
                    this.clearFeedback();
                });
            });
        });
        this.document.addEventListener('visibilitychange', this.onReturn);
        window.addEventListener('focus', this.onReturn);
        this.destroyRef.onDestroy(() => {
            this.document.removeEventListener('visibilitychange', this.onReturn);
            window.removeEventListener('focus', this.onReturn);
            this.clearFeedback();
        });
    }

    protected async toggleNotifications(): Promise<void> {
        this.notificationError.set(false);
        const userId = this.userId();
        if (!this.supported || userId === undefined || this.requestingPermission()) {
            return;
        }
        if (this.enabled()) {
            this.optedIn.set(false);
            this.localStorage.store(BUILD_NOTIFICATIONS_LOCAL_STORAGE_KEY_PREFIX + userId, false);
            this.clearFeedback();
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

    private onResult(result: Result | undefined): void {
        if (result) {
            this.latestResult = result;
        }
        if (this.pendingSubmissionId === undefined || result?.submission?.id !== this.pendingSubmissionId || this.isHandledResult(result)) {
            return;
        }
        const submissionId = this.pendingSubmissionId;
        this.lastHandledResult = result;
        this.pendingSubmissionId = undefined;
        this.clearFeedback();
        if (!this.isAway()) {
            return;
        }
        this.setFavicon('result');
        if (this.enabled() && Notification.permission === 'granted') {
            void this.showNotification(submissionId);
        }
    }

    private isAway(): boolean {
        return this.document.hidden || !this.document.hasFocus();
    }

    private isHandledResult(result: Result | undefined): boolean {
        if (result === this.lastHandledResult) {
            return true;
        }
        if (result?.id === undefined || result.id !== this.lastHandledResult?.id) {
            return false;
        }
        // Fallback GETs can replay a previously handled result as a new object. Semi-automatic
        // results can also be updated in place after a rebuild, keeping their ID but changing completion time.
        const completion = result.completionDate ? dayjs(result.completionDate).valueOf() : undefined;
        const previousCompletion = this.lastHandledResult.completionDate ? dayjs(this.lastHandledResult.completionDate).valueOf() : undefined;
        return completion === previousCompletion;
    }

    private readonly onReturn = () => {
        if (this.supported) {
            this.permission.set(Notification.permission);
        }
        if (!this.isAway()) {
            this.clearFeedback();
        }
    };

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

    private clearFeedback(): void {
        this.generation++;
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
        this.notification?.close();
        this.notification = undefined;
        this.closePersistentNotification?.();
        this.closePersistentNotification = undefined;
    }

    private async showNotification(submissionId: number): Promise<void> {
        const generation = this.generation;
        const tag = 'artemis-build-' + this.userId() + '-' + this.participationId() + '-' + submissionId;
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
            if (registration) {
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
