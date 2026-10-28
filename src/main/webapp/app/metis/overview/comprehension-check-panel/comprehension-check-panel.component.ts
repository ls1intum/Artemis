import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiInputDirective, TumAetUiMessageComponent, TumAetUiTagComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { Subscription } from 'rxjs';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { UserService } from 'app/account/user/shared/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { QuestionTimerComponent } from 'app/metis/overview/question-timer/question-timer.component';
import { MetisCheckStatus, MetisQuestionDTO, MetisStudentCheckDTO } from 'app/metis/shared/entities/metis.model';
import { FocusChipComponent } from 'app/metis/shared/focus-chip/focus-chip.component';
import { RatingSquareComponent } from 'app/metis/shared/rating-square/rating-square.component';
import { MetisService } from 'app/metis/shared/services/metis.service';

/** What the panel shows; derived from the check's status, its answer window, the student's AI decision and the server clock. */
type ComprehensionCheckView = 'loading' | 'consent' | 'preparing' | 'review' | 'before' | 'ready' | 'answering' | 'done' | 'results' | 'closed' | 'unavailable';

/** One question of the thread: a main question carries its number, a follow-up carries none. */
interface ThreadEntry {
    question: MetisQuestionDTO;
    mainNumber?: number;
    answered: boolean;
}

/** The skip reasons the student can resolve by choosing an AI option: no decision yet, or on-premise AI where Metis uses a cloud model. */
const CONSENT_REASONS: readonly string[] = ['NO_AI_CONSENT', 'CLOUD_AI_REQUIRED'];

/** Added to a reload at a window boundary, so it lands after the boundary the server compares against. */
const BOUNDARY_SLACK_MS = 1000;
/** No reload waits longer than this; a boundary further ahead is approached hour by hour. */
const MAX_REFRESH_DELAY_MS = 60 * 60 * 1000;
/** How often a check is reloaded while its questions are prepared or reviewed after the window has opened. */
const PREPARING_REFRESH_MS = 30 * 1000;
/** How often a started check without a current question is reloaded while an answer still waits for its rating. */
const WAITING_REFRESH_MS = 10 * 1000;
/**
 * How long the panel waits before it loads the check again after a failed reload or a failed answer; the wait doubles
 * with every further failure until a check is applied again, up to the longest reload delay.
 */
const FIRST_RETRY_DELAY_MS = 30 * 1000;

/**
 * Whether the server refused a start or an answer for the student's AI decision. Both need the same access to the exercise as
 * the load of the check, which succeeded; what they check in addition is that the decision covers the AI model of this
 * server, and a refusal of that is the only 403 they answer with.
 */
function isConsentRefusal(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 403;
}

/**
 * Student tab of the Metis comprehension check for one programming exercise.
 *
 * It reads the student's own check through {@link MetisService}; the server resolves the participation from the login,
 * so the exercise is all the panel needs. Every state the student can meet is derived from the check DTO, the
 * student's AI decision and the server clock: the consent request, the waiting phases before the questions exist,
 * before the window opens, ready, answering (a thread of question and answer bubbles with a timer per question),
 * done (or waiting for the rating of an answer), results, closed and unavailable. Time-based changes are re-fetched
 * at their boundary by a refresh timer, so nothing polls once the window has ended; a failed reload is tried again,
 * and a failed answer loads the check again, since the timer runs out only once.
 * The panel is destroyed on every tab switch, so it loads the check in `ngOnInit` and keeps no state of its own beyond
 * the draft of the current answer and a refusal of the server for the student's AI decision.
 */
@Component({
    selector: 'jhi-comprehension-check-panel',
    templateUrl: './comprehension-check-panel.component.html',
    styleUrl: './comprehension-check-panel.component.scss',
    imports: [
        NgTemplateOutlet,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        ArtemisTimeAgoPipe,
        MarkdownDirective,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiInputDirective,
        TumAetUiMessageComponent,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
        QuestionTimerComponent,
        RatingSquareComponent,
        FocusChipComponent,
    ],
    host: { class: 'flex h-full min-h-0 flex-col gap-2' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComprehensionCheckPanelComponent implements OnInit, OnDestroy {
    private readonly metisService = inject(MetisService);
    private readonly accountService = inject(AccountService);
    private readonly userService = inject(UserService);
    private readonly llmModalService = inject(LLMSelectionModalService);
    private readonly serverDateService = inject(ArtemisServerDateService);
    private readonly destroyRef = inject(DestroyRef);

    readonly exerciseId = input.required<number>();

    protected readonly faCircleInfo = faCircleInfo;

    /** The student's check as the server last reported it; undefined until loaded. */
    protected readonly check = signal<MetisStudentCheckDTO | undefined>(undefined);
    /** Whether the check could not be loaded; the panel then says that there is no check instead of loading forever. */
    protected readonly loadFailed = signal(false);
    /** The draft of the answer to the current question, which only the panel holds; it is dropped once the server shows another question. */
    protected readonly draft = signal('');
    protected readonly submitting = signal(false);
    /** Whether the most recent answer of this session went out because its timer ran out. */
    protected readonly lastAnswerExpired = signal(false);
    /**
     * Whether the server refused a start or an answer because the student's AI decision does not cover its AI model, for
     * instance on-premise AI where Metis uses a cloud model. The panel then asks for a decision until the dialog stored one.
     */
    private readonly consentRefused = signal(false);
    /** Server time of the last load or mutation; the states are derived against it. */
    private readonly now = signal(this.serverDateService.now());

    private loadSubscription?: Subscription;
    private refreshTimer?: number;
    /** The wait before the next reload after a failure; it starts over with every check that is applied. */
    private retryDelay = FIRST_RETRY_DELAY_MS;
    private destroyed = false;

    /** Students who opted out of AI features get a notice instead of the check; it takes priority over every other state. */
    protected readonly declined = computed(() => this.accountService.userIdentity()?.selectedLLMUsage === LLMSelectionDecision.NO_AI);

    protected readonly view = computed<ComprehensionCheckView>(() => {
        const check = this.check();
        if (!check) {
            return this.loadFailed() ? 'unavailable' : 'loading';
        }
        const now = this.now();
        const windowEnded = !!check.windowEnd && !now.isBefore(check.windowEnd);
        // Once the window has ended, a decision can no longer lead to questions, so the student sees why the check is closed or unavailable instead.
        if (!windowEnded && this.needsConsent(check)) {
            return 'consent';
        }
        switch (check.status) {
            // A check that was never started is closed once the window has ended, whether or not its questions were ready in time.
            case MetisCheckStatus.PENDING:
            case MetisCheckStatus.GENERATING:
                return windowEnded ? 'closed' : 'preparing';
            case MetisCheckStatus.IN_REVIEW:
                return windowEnded ? 'closed' : 'review';
            case MetisCheckStatus.READY:
                if (windowEnded) {
                    return 'closed';
                }
                return check.windowStart && now.isBefore(check.windowStart) ? 'before' : 'ready';
            case MetisCheckStatus.IN_PROGRESS:
                if (windowEnded) {
                    return 'closed';
                }
                return check.currentQuestion ? 'answering' : 'done';
            case MetisCheckStatus.COMPLETED:
                return check.ratingsVisible && (windowEnded || !check.windowEnd) ? 'results' : 'done';
            default:
                return 'unavailable';
        }
    });

    /**
     * A started check without a current question: an answer of another tab, or of the session before a reload, still
     * waits for its rating, or the window leaves no time for another question. The panel says that the answer is saved
     * instead of thanking the student for a check that is not complete.
     */
    protected readonly waiting = computed(() => this.view() === 'done' && this.check()?.status === MetisCheckStatus.IN_PROGRESS);

    /**
     * Whether the consent request asks for the cloud option: the student chose on-premise AI, but Metis uses a cloud model
     * here. The server names that reason for a skipped check; a refused start or answer under that decision means the same.
     */
    protected readonly cloudConsentRequired = computed(
        () =>
            this.check()?.failureReason === 'CLOUD_AI_REQUIRED' ||
            (this.consentRefused() && this.accountService.userIdentity()?.selectedLLMUsage === LLMSelectionDecision.LOCAL_AI),
    );

    protected readonly showThread = computed(() => this.view() === 'answering' || this.view() === 'done');
    protected readonly mainCount = computed(() => this.check()?.mainQuestionCount ?? 0);
    protected readonly points = computed(() => this.check()?.likertScalePoints ?? 0);
    protected readonly answeredMainCount = computed(() => this.check()?.answeredQuestions.filter((question) => !question.parentQuestionId).length ?? 0);
    /** A closed check shows the ratings of the questions the student answered before the window ended. */
    protected readonly showClosedResults = computed(() => {
        const check = this.check();
        return !!check && check.ratingsVisible && check.answeredQuestions.length > 0;
    });

    /** The answered questions followed by the current one, numbered along the main questions. */
    protected readonly thread = computed<ThreadEntry[]>(() => {
        const check = this.check();
        if (!check) {
            return [];
        }
        let mainNumber = 0;
        const entries: ThreadEntry[] = check.answeredQuestions.map((question) => {
            const isMain = !question.parentQuestionId;
            if (isMain) {
                mainNumber += 1;
            }
            return { question, mainNumber: isMain ? mainNumber : undefined, answered: true };
        });
        if (check.currentQuestion) {
            entries.push({ question: check.currentQuestion, mainNumber: check.currentQuestion.parentQuestionId ? undefined : mainNumber + 1, answered: false });
        }
        return entries;
    });

    protected readonly currentEntry = computed(() => this.thread().find((entry) => !entry.answered));
    /** The answered questions of the thread, one result card each; a question still open when the window closed has no card. */
    protected readonly resultEntries = computed(() => this.thread().filter((entry) => entry.answered));
    /** Index in the thread of the most recently answered question, under which the receipt line is shown. */
    protected readonly lastAnsweredIndex = computed(() => (this.check()?.answeredQuestions.length ?? 0) - 1);
    /**
     * Server time at which the current question has to be answered: its limit, cut to the window end when that comes
     * first, the deadline the server enforces. A question without `shownAt` starts at the moment the check was loaded.
     */
    protected readonly currentDeadline = computed(() => {
        const check = this.check();
        if (!check?.currentQuestion) {
            return undefined;
        }
        const question = check.currentQuestion;
        const limitEnd = (question.shownAt ?? this.now()).add(question.timeLimitSeconds, 'second');
        return check.windowEnd?.isBefore(limitEnd) ? check.windowEnd : limitEnd;
    });
    protected readonly currentLimitSeconds = computed(() => this.check()?.currentQuestion?.timeLimitSeconds ?? 0);

    ngOnInit(): void {
        this.load();
    }

    ngOnDestroy(): void {
        this.destroyed = true;
        this.clearRefreshTimer();
        this.loadSubscription?.unsubscribe();
    }

    /**
     * Starts the check: the server switches it to IN_PROGRESS and hands back the first question. A start refused for the
     * student's AI decision leads to the consent request; any other refusal (the window has just closed, or leaves no time
     * for a question) loads the check again, so the panel shows why.
     */
    protected start(): void {
        this.metisService
            .startCheck(this.exerciseId())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (check) => this.applyCheck(check),
                error: (error: unknown) => {
                    if (isConsentRefusal(error)) {
                        this.consentRefused.set(true);
                    } else {
                        this.load();
                    }
                },
            });
    }

    /**
     * Opens the dialog in which the student chooses how Artemis may use AI. A choice is stored on the account and the
     * check is loaded again: with consent, the server queues a skipped check for generation once more, and a start or an
     * answer it refused can be tried again.
     */
    protected async openConsentDialog(): Promise<void> {
        const choice = await this.llmModalService.open(this.accountService.userIdentity()?.selectedLLMUsage);
        if (choice === LLM_MODAL_DISMISSED) {
            return;
        }
        // Not tied to the panel: the choice belongs to the account and is stored even if the student left the tab meanwhile.
        this.userService.updateLLMSelectionDecision(choice).subscribe(() => {
            this.accountService.setUserLLMSelectionDecision(choice);
            if (!this.destroyed) {
                this.consentRefused.set(false);
                this.load();
            }
        });
    }

    /** Enter submits the draft; Shift+Enter keeps the browser's own line break. */
    protected onAnswerKeydown(event: KeyboardEvent): void {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            this.submit();
        }
    }

    /** The timer ran out: whatever has been typed goes out as it is, even nothing. */
    protected onTimerExpired(): void {
        this.submit(true);
    }

    /**
     * Sends the draft as the answer to the current question and shows what the server hands back next: a follow-up,
     * the next main question or the completed check. A refused answer keeps the draft; one refused for the student's AI
     * decision leads to the consent request. Any other failure loads the check again after the retry wait, also for an
     * answer sent by hand: the timer runs out only once and sends nothing while an answer is on its way, an empty draft
     * cannot be sent by hand, and an open question is not reloaded without a window end. The server then shows the next
     * question if it stored the answer, or the same one, whose new deadline lets the timer run out again at once and send
     * the draft once more.
     * @param expired whether the question's timer ran out, in which case an empty draft is sent too
     */
    protected submit(expired = false): void {
        const question = this.check()?.currentQuestion;
        if (!question || this.submitting() || (!expired && !this.draft().trim())) {
            return;
        }
        this.submitting.set(true);
        this.lastAnswerExpired.set(expired);
        this.metisService
            .submitAnswer(this.exerciseId(), question.id, this.draft())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (check) => {
                    this.draft.set('');
                    this.applyCheck(check);
                    this.submitting.set(false);
                },
                error: (error: unknown) => {
                    if (isConsentRefusal(error)) {
                        this.consentRefused.set(true);
                    } else {
                        this.scheduleRetry();
                    }
                    this.submitting.set(false);
                },
            });
    }

    /** Loads the student's check; the refresh timer calls it again at the next boundary of the window. */
    private load(): void {
        this.loadSubscription?.unsubscribe();
        this.loadSubscription = this.metisService.studentCheck(this.exerciseId()).subscribe({
            next: (check) => this.applyCheck(check),
            error: () => this.onLoadFailed(),
        });
    }

    /**
     * A failed first load leaves nothing to show, so the panel says that there is no check. A failed reload keeps the last
     * check on screen and is tried again later, so a passing failure, such as a whole cohort reloading at the window start,
     * does not freeze the panel at a boundary; each further failure doubles the wait.
     */
    private onLoadFailed(): void {
        if (!this.check()) {
            this.loadFailed.set(true);
            return;
        }
        this.scheduleRetry();
    }

    /** Replaces the pending reload with one after the retry wait, and doubles the wait for a further failure until a check is applied. */
    private scheduleRetry(): void {
        this.clearRefreshTimer();
        this.refreshTimer = window.setTimeout(() => this.load(), this.retryDelay);
        this.retryDelay = Math.min(2 * this.retryDelay, MAX_REFRESH_DELAY_MS);
    }

    /**
     * Takes over a check from the server together with the server time it was read at, and schedules the next reload. The draft
     * belongs to the question it was typed for, so it is dropped when the check shows another question or none: after an answer
     * whose response was lost, the reload shows the next question, whose timer would otherwise send the old draft as its answer.
     */
    private applyCheck(check: MetisStudentCheckDTO): void {
        if (check.currentQuestion?.id !== this.check()?.currentQuestion?.id) {
            this.draft.set('');
        }
        this.now.set(this.serverDateService.now());
        this.loadFailed.set(false);
        this.retryDelay = FIRST_RETRY_DELAY_MS;
        this.check.set(check);
        this.scheduleRefresh(check);
    }

    /**
     * Whether choosing an AI option is what the check waits for: it was skipped for the student's decision, the server
     * refused a start or an answer for it, or the student has no decision while the questions are still to be generated,
     * which happens only with consent. A check generated under a decision is shown as it is even when this page knows of
     * none: the student most likely decided in another tab, and should the server refuse the start, the request follows.
     * The caller asks only while the window has not ended.
     */
    private needsConsent(check: MetisStudentCheckDTO): boolean {
        switch (check.status) {
            case MetisCheckStatus.SKIPPED:
                return check.failureReason !== undefined && CONSENT_REASONS.includes(check.failureReason);
            case MetisCheckStatus.PENDING:
            case MetisCheckStatus.GENERATING:
                return !this.accountService.userIdentity()?.selectedLLMUsage;
            case MetisCheckStatus.IN_REVIEW:
            case MetisCheckStatus.READY:
            case MetisCheckStatus.IN_PROGRESS:
                return this.consentRefused();
            default:
                return false;
        }
    }

    /**
     * Replaces the pending reload with one at the next moment the view can change on its own; a view that cannot change gets none.
     * @param check the check that was just applied
     */
    private scheduleRefresh(check: MetisStudentCheckDTO): void {
        this.clearRefreshTimer();
        const delay = this.declined() ? undefined : this.refreshDelay(check);
        if (delay !== undefined) {
            this.refreshTimer = window.setTimeout(() => this.load(), Math.min(delay, MAX_REFRESH_DELAY_MS));
        }
    }

    private clearRefreshTimer(): void {
        if (this.refreshTimer !== undefined) {
            window.clearTimeout(this.refreshTimer);
            this.refreshTimer = undefined;
        }
    }

    /**
     * Milliseconds until the view can change without an action of the student, or undefined when it cannot: at the
     * window start before the window, polled while the questions are prepared or an answer is rated, at the window end
     * while the check is open or waits for the student's consent, and never once the window has ended.
     * @param check the check that was just applied
     */
    private refreshDelay(check: MetisStudentCheckDTO): number | undefined {
        switch (this.view()) {
            case 'before':
                return this.untilBoundary(check.windowStart);
            case 'preparing':
            case 'review':
                if (!check.windowStart) {
                    return MAX_REFRESH_DELAY_MS;
                }
                return this.now().isBefore(check.windowStart) ? this.untilBoundary(check.windowStart) : PREPARING_REFRESH_MS;
            case 'consent':
            case 'ready':
            case 'answering':
                return this.untilBoundary(check.windowEnd);
            case 'done':
                return check.status === MetisCheckStatus.IN_PROGRESS ? WAITING_REFRESH_MS : this.untilBoundary(check.windowEnd);
            case 'loading':
            case 'results':
            case 'closed':
            case 'unavailable':
                return undefined;
        }
    }

    /** Milliseconds from the last load until just after a boundary of the window, or undefined without the boundary. */
    private untilBoundary(boundary: dayjs.Dayjs | undefined): number | undefined {
        return boundary ? Math.max(0, boundary.diff(this.now())) + BOUNDARY_SLACK_MS : undefined;
    }
}
