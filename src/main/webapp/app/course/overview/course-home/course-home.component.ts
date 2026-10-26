import { ChangeDetectionStrategy, Component, DestroyRef, Injector, afterNextRender, computed, effect, inject, signal, untracked } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { Subscription, catchError, debounceTime, filter, fromEvent, map, of, take, timeout, timer } from 'rxjs';
import { TumAetUiButtonDirective, TumAetUiCardComponent, TumAetUiMessageComponent, TumAetUiProgressBarComponent, TumAetUiTagComponent } from '@tumaet/ui-angular';
import { AccountService } from 'app/core/auth/account.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseTabRefreshService } from 'app/course/overview/services/course-tab-refresh.service';
import { CourseNotificationWebsocketService } from 'app/notification/course-notification/course-notification-websocket.service';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { CourseHome, CourseHomeGeneratedWelcome, CourseHomeTeachingTeam } from './course-home.model';
import { CourseHomeService } from './course-home.service';
import { CourseHomeWelcomeService } from './course-home-welcome.service';
import { courseHomeTarget } from './course-home-target.util';
import { CourseHomeQuestionsComponent } from './course-home-questions.component';
import { CourseHomeIrisHistoryComponent } from './course-home-iris-history.component';
import { CourseHomeTeachingTeamComponent } from './course-home-teaching-team.component';

@Component({
    selector: 'jhi-course-home',
    templateUrl: './course-home.component.html',
    styleUrl: './course-home.component.scss',
    imports: [
        RouterLink,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        TumAetUiButtonDirective,
        TumAetUiCardComponent,
        TumAetUiMessageComponent,
        TumAetUiProgressBarComponent,
        TumAetUiTagComponent,
        CourseHomeQuestionsComponent,
        CourseHomeIrisHistoryComponent,
        CourseHomeTeachingTeamComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseHomeComponent {
    private readonly api = inject(CourseHomeService);
    private readonly tabs = inject(CourseTabRefreshService);
    private readonly notifications = inject(CourseNotificationWebsocketService);
    private readonly serverDate = inject(ArtemisServerDateService);
    private readonly document = inject(DOCUMENT);
    private readonly welcomes = inject(CourseHomeWelcomeService);
    private readonly account = inject(AccountService);
    private readonly courses = inject(CourseStorageService);
    private readonly route = inject(ActivatedRoute);
    private readonly translate = inject(TranslateService);
    private readonly injector = inject(Injector);
    private readonly destroyRef = inject(DestroyRef);
    private readonly params = toSignal(this.route.parent!.paramMap);
    private readonly languageChange = toSignal(this.translate.onLangChange);
    private readonly consent = computed(() => `${this.account.userIdentity()?.selectedLLMUsage}:${this.account.userIdentity()?.selectedLLMUsageTimestamp?.valueOf()}`);

    readonly home = signal<CourseHome | undefined>(undefined);
    protected readonly loading = signal(true);
    protected readonly failed = signal(false);
    protected readonly refreshFailed = signal(false);
    protected readonly aiMessage = signal<string | undefined>(undefined);
    protected readonly team = signal<CourseHomeTeachingTeam>({ hasMore: false, page: -1 });
    protected readonly teamLoading = signal(false);
    protected readonly teamFailed = signal(false);
    protected readonly target = courseHomeTarget;
    protected readonly courseId = computed(() => Number(this.params()?.get('courseId')));
    protected readonly locale = computed(() => ((this.languageChange()?.lang ?? this.translate.getCurrentLang()) === 'de' ? 'de' : 'en'));
    protected readonly welcomeKeys = computed(() => {
        const home = this.home();
        return home ? this.welcomes.keys(home) : undefined;
    });
    protected readonly welcomeParams = computed(() => ({
        courseTitle: this.home()?.course.title,
        nameSuffix: this.account.userIdentity()?.firstName ? `, ${this.account.userIdentity()!.firstName}` : '',
    }));
    protected readonly progressPercent = computed(() => {
        const p = this.home()?.progress;
        return p?.releasedExercises ? (100 * p.submittedExercises) / p.releasedExercises : 0;
    });
    protected readonly allowIris = computed(() => this.account.userIdentity()?.selectedLLMUsage !== LLMSelectionDecision.NO_AI);

    private scope?: string;
    private generation = 0;
    private scopedUserId?: number;
    private scopedCourseId?: number;
    private baseline?: string;
    private loadSubscription?: Subscription;
    private aiSubscription?: Subscription;
    private teamSubscription?: Subscription;
    private boundarySubscription?: Subscription;
    private rendered?: CourseHome;
    private acknowledged = false;
    private welcomeAttempted = false;

    constructor() {
        effect(() => {
            const user = this.account.userIdentity();
            const courseId = this.courseId();
            const course = this.courses.currentCourse();
            const ready = user?.id && Number.isSafeInteger(courseId) && courseId > 0 && course?.id === courseId;
            const scope = ready ? `${user.id}:${courseId}` : undefined;
            if (scope !== this.scope)
                untracked(() => {
                    this.closeScope();
                    this.scope = scope;
                    this.scopedUserId = ready ? user.id : undefined;
                    this.scopedCourseId = ready ? courseId : undefined;
                    this.home.set(undefined);
                    this.aiMessage.set(undefined);
                    this.loading.set(true);
                    this.failed.set(false);
                    this.refreshFailed.set(false);
                    this.team.set({ hasMore: false, page: -1 });
                    this.teamLoading.set(false);
                    this.teamFailed.set(false);
                    this.baseline = undefined;
                    this.rendered = undefined;
                    this.acknowledged = false;
                    this.welcomeAttempted = false;
                    if (ready) this.refresh();
                });
        });
        effect(() => {
            this.locale();
            this.consent();
            untracked(() => {
                this.cancelWelcome();
                this.aiMessage.set(undefined);
                const snapshot = this.home();
                if (snapshot && !this.allowIris()) {
                    const hadIris = snapshot.nextStep?.target.kind === 'IRIS_SESSION' || !!snapshot.irisSessions.items?.length;
                    this.home.set(this.applyIrisConsent(snapshot));
                    if (this.rendered) this.rendered = this.applyIrisConsent(this.rendered);
                    if (hadIris) this.refresh();
                }
            });
        });
        this.tabs
            .reselections(this.route)
            .pipe(takeUntilDestroyed())
            .subscribe(() => this.refresh());
        this.notifications.websocketNotification$
            .pipe(
                filter((notification) => notification.courseId === this.scopedCourseId),
                map(() => this.generation),
                debounceTime(300),
                filter((generation) => generation === this.generation),
                takeUntilDestroyed(),
            )
            .subscribe(() => this.refresh());
        fromEvent(this.document, 'visibilitychange')
            .pipe(
                filter(() => !this.document.hidden),
                takeUntilDestroyed(),
            )
            .subscribe(() => this.refresh());
        this.destroyRef.onDestroy(() => this.closeScope());
    }

    refresh(): void {
        if (!this.scope || !this.scopedCourseId) return;
        const scope = this.scope;
        const generation = this.generation;
        this.loadSubscription?.unsubscribe();
        this.loading.set(!this.home());
        this.failed.set(false);
        this.refreshFailed.set(false);
        this.loadSubscription = this.api.load(this.scopedCourseId, this.baseline).subscribe({
            next: (snapshot) => {
                if (this.scope !== scope || this.generation !== generation) return;
                snapshot = this.applyIrisConsent(snapshot);
                const previous = this.home();
                this.baseline ??= snapshot.visit.newSince;
                if (previous && previous.welcome.contextFingerprint === snapshot.welcome.contextFingerprint) {
                    snapshot = cloneWith(snapshot, {
                        visit: previous.visit,
                        welcome: cloneWith(snapshot.welcome, {
                            welcomeCase: previous.welcome.welcomeCase,
                            variantIndex: previous.welcome.variantIndex,
                            params: previous.welcome.params,
                        }),
                    });
                } else if (previous) {
                    this.cancelWelcome();
                    this.aiMessage.set(undefined);
                    snapshot = cloneWith(snapshot, { visit: cloneWith(snapshot.visit, { firstVisit: previous.visit.firstVisit }) });
                }
                this.home.set(snapshot);
                if (!snapshot.welcome.aiEligible) this.cancelWelcome();
                this.scheduleBoundary(snapshot);
                this.loading.set(false);
                if (!previous) {
                    this.team.set(cloneWith(snapshot.teachingTeam, { page: -1 }));
                    const rendered = snapshot;
                    afterNextRender(
                        () => {
                            if (this.scope !== scope || this.generation !== generation || this.rendered) return;
                            this.rendered = rendered;
                            this.startWelcome(rendered);
                        },
                        { injector: this.injector },
                    );
                }
            },
            error: (error: unknown) => {
                if (this.scope === scope && this.generation === generation) {
                    if (this.isAccessError(error)) {
                        this.clearProtectedData();
                        return;
                    }
                    this.loading.set(false);
                    if (this.home()) this.refreshFailed.set(true);
                    else this.failed.set(true);
                }
            },
        });
    }

    loadMoreTeam(): void {
        if (!this.scope || !this.scopedCourseId || this.teamLoading() || !this.team().hasMore) return;
        const scope = this.scope;
        const generation = this.generation;
        const page = this.team().page + 1;
        this.teamLoading.set(true);
        this.teamFailed.set(false);
        this.teamSubscription = this.api.teachingTeam(this.scopedCourseId, page).subscribe({
            next: (team) => {
                if (scope !== this.scope) return;
                const members = page === 0 ? (team.items ?? []) : [...(this.team().items ?? []), ...(team.items ?? [])];
                this.team.set(cloneWith(team, { items: members.filter((member, index) => members.findIndex((other) => other.id === member.id) === index) }));
                this.teamLoading.set(false);
            },
            error: (error: unknown) => {
                if (scope === this.scope && generation === this.generation) {
                    if (this.isAccessError(error)) {
                        this.clearProtectedData();
                        return;
                    }
                    this.teamLoading.set(false);
                    this.teamFailed.set(true);
                }
            },
        });
    }

    private applyIrisConsent(snapshot: CourseHome): CourseHome {
        if (this.allowIris()) return snapshot;
        return cloneWith(snapshot, {
            nextStep: snapshot.nextStep?.target.kind === 'IRIS_SESSION' ? undefined : snapshot.nextStep,
            irisSessions: { status: 'DISABLED', items: [] },
        });
    }

    private scheduleBoundary(snapshot: CourseHome): void {
        this.boundarySubscription?.unsubscribe();
        if (!snapshot.nextRefreshAt) return;
        const delay = Date.parse(snapshot.nextRefreshAt) - this.serverDate.now().valueOf();
        if (!Number.isFinite(delay)) return;
        this.boundarySubscription = timer(Math.max(100, Math.min(delay + 50, 2147483647))).subscribe(() => {
            this.cancelWelcome();
            this.refresh();
        });
    }

    private startWelcome(snapshot: CourseHome): void {
        if (this.welcomeAttempted) return;
        this.welcomeAttempted = true;
        const preference = this.account.userIdentity()?.selectedLLMUsage;
        if (
            snapshot.welcome.contextFingerprint !== this.home()?.welcome.contextFingerprint ||
            !snapshot.welcome.aiEligible ||
            (preference !== LLMSelectionDecision.LOCAL_AI && preference !== LLMSelectionDecision.CLOUD_AI)
        ) {
            this.acknowledgeRendered();
            return;
        }
        const scope = this.scope;
        const generation = this.generation;
        const locale = this.locale();
        const fingerprint = snapshot.welcome.contextFingerprint;
        this.aiSubscription = this.api
            .generateWelcome(snapshot.course.id, fingerprint, locale)
            .pipe(
                timeout(3500),
                take(1),
                catchError((error: unknown) => {
                    if (scope === this.scope && generation === this.generation && this.isAccessError(error)) this.clearProtectedData();
                    return of(undefined);
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (response: CourseHomeGeneratedWelcome | undefined) => {
                    if (
                        scope === this.scope &&
                        generation === this.generation &&
                        this.locale() === locale &&
                        this.account.userIdentity()?.selectedLLMUsage === preference &&
                        this.home()?.welcome.contextFingerprint === fingerprint &&
                        response?.contextFingerprint === fingerprint &&
                        response.source === 'AI' &&
                        response.message?.trim() &&
                        response.message.length <= 500
                    )
                        this.aiMessage.set(response.message);
                },
                complete: () => this.acknowledgeRendered(),
            });
    }
    private cancelWelcome(): void {
        this.aiSubscription?.unsubscribe();
        this.aiSubscription = undefined;
        this.aiMessage.set(undefined);
        this.acknowledgeRendered();
    }
    private acknowledgeRendered(): void {
        if (!this.rendered || this.acknowledged || this.account.userIdentity()?.id !== this.scopedUserId) return;
        this.acknowledged = true;
        this.api
            .acknowledgeVisit(this.rendered.course.id, this.rendered.visit.snapshotAt, this.rendered.visit.variantCursor)
            .pipe(
                take(1),
                catchError(() => of(undefined)),
            )
            .subscribe();
    }
    private isAccessError(error: unknown): boolean {
        return error instanceof HttpErrorResponse && [401, 403, 404].includes(error.status);
    }
    private clearProtectedData(): void {
        // Known permission loss invalidates the rendered snapshot and all pending work.
        this.rendered = undefined;
        this.closeScope();
        this.home.set(undefined);
        this.team.set({ hasMore: false, page: -1 });
        this.loading.set(false);
        this.failed.set(true);
        this.refreshFailed.set(false);
        this.teamLoading.set(false);
        this.teamFailed.set(false);
        this.baseline = undefined;
        this.acknowledged = false;
        this.welcomeAttempted = false;
    }
    private closeScope(): void {
        this.generation++;
        this.cancelWelcome();
        this.loadSubscription?.unsubscribe();
        this.teamSubscription?.unsubscribe();
        this.boundarySubscription?.unsubscribe();
    }
}
