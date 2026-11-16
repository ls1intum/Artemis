import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiInputDirective, TumAetUiMessageComponent, TumAetUiTagComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { Subscription, finalize } from 'rxjs';
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
import { MetisAnswerDrafts } from 'app/metis/overview/comprehension-check-panel/metis-answer-drafts.service';
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
 * The waits after which an answer whose request broke off or went unanswered is sent again. The server stores an answer as
 * it was written only up to its grace after the deadline, 15 seconds by default, so the reload after the retry wait could come
 * too late and leave the answer empty; these attempts all go out within the grace. Sending it again is safe, because the
 * server stores an answer once: if the first one arrived and only its response got lost, the next attempt brings back the
 * check as the server has it, with the question it opened meanwhile, whose timer is already running.
 */
const ANSWER_RETRY_DELAYS_MS: readonly number[] = [1000, 3000, 6000];
/** How long the panel first waits before it loads a check again whose shown answers still wait for their ratings; the wait doubles with every such reload. */
const RATING_REFRESH_MS = 10 * 1000;

/**
 * Whether the server refused a start or an answer for the student's AI decision. Both need the same access to the exercise as
 * the load of the check, which succeeded; what they check in addition is that the decision covers the AI model of this
 * server, and a refusal of that is the only 403 they answer with.
 */
function isConsentRefusal(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 403;
}

/**
 * Whether a failed answer may get through when it is sent again at once: the connection broke off, or the server or a
 * gateway did not answer. A refusal of the server stays a refusal on the next attempt.
 */
function isTransientFailure(error: unknown): boolean {
    return !(error instanceof HttpErrorResponse) || error.status === 0 || error.status >= 500;
}

/**
 * Student tab of the Metis comprehension check for one programming exercise.
 *
 * It reads the student's own check through {@link MetisService}; the server resolves the participation from the login,
 * so the exercise is all the panel needs. Every state the student can meet is derived from the check DTO, the
 * student's AI decision and the server clock: the consent request, the waiting phases before the questions exist,
 * before the window opens, ready, answering (a thread of question and answer bubbles with a timer per question),
 * done (or waiting for the rating of an answer), results, closed and unavailable. Time-based changes are re-fetched
 * at their boundary by a refresh timer, so once the window has ended nothing polls but the reloads for ratings the
 * server still owes; a failed reload is tried again, an answer whose request broke off or went unanswered, timed or sent
 * by hand, is sent again within the grace of the server, and a refused answer, or one whose attempts all failed, loads
 * the check again after the retry wait, since the timer runs out only once.
 * The split panel keeps the panel alive while another tab is open, so a decision about AI that the student makes there,
 * for instance in the Iris tab next to it, reaches it only through the account: a decision other than the one the check
 * was last loaded under loads the check again. Beyond the check, the panel keeps only the draft of the current answer and
 * a refusal of the server for the student's AI decision. The draft also goes to {@link MetisAnswerDrafts}, which outlives
 * the panel, so that a panel created anew while the question is open, for instance when the split view changes its layout,
 * shows it again.
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
    private readonly drafts = inject(MetisAnswerDrafts);

    readonly exerciseId = input.required<number>();

    protected readonly faCircleInfo = faCircleInfo;

    /** The student's check as the server last reported it; undefined until loaded. */
    protected readonly check = signal<MetisStudentCheckDTO | undefined>(undefined);
    /** Whether the check could not be loaded; the panel then says that there is no check instead of loading forever. */
    protected readonly loadFailed = signal(false);
    /**
     * The draft of the answer to the current question, kept in {@link MetisAnswerDrafts} as well; it is dropped once the server shows another question.
     */
    protected readonly draft = signal('');
    protected readonly submitting = signal(false);
    /**
     * Whether a start is on its way. A second start, for instance from a double click, could come back after an answer given through the first and
     * bring back the check as it was before that answer.
     */
    protected readonly starting = signal(false);
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
    /** The start on its way, cancelled when the panel is handed another exercise. */
    private startSubscription?: Subscription;
    /** The answer on its way, cancelled when the panel is handed another exercise. */
    private answerSubscription?: Subscription;
    /** The exercise whose check the panel shows; set by the first load. */
    private shownExerciseId?: number;
    private refreshTimer?: number;
    /** The pending attempt to send an answer again; a reload of the check, which replaces the refresh timer, leaves it alone. */
    private resendTimer?: number;
    /** The wait before the next reload after a failure; it starts over with every check that is applied. */
    private retryDelay = FIRST_RETRY_DELAY_MS;
    /** The wait before the next reload while shown answers wait for their ratings; it starts over once none is missing. */
    private ratingRefreshDelay = RATING_REFRESH_MS;
    private destroyed = false;
    /** The student's AI decision when the check was last loaded; a different one loads it again. */
    private decisionOfLastLoad = this.accountService.userIdentity()?.selectedLLMUsage;

    /** Students who opted out of AI features get a notice instead of the check; it takes priority over every other state. */
    protected readonly declined = computed(() => this.accountService.userIdentity()?.selectedLLMUsage === LLMSelectionDecision.NO_AI);

    /** Whether the student's window has ended. */
    protected readonly windowEnded = computed(() => {
        const windowEnd = this.check()?.windowEnd;
        return !!windowEnd && !this.now().isBefore(windowEnd);
    });

    protected readonly view = computed<ComprehensionCheckView>(() => {
        const check = this.check();
        if (!check) {
            return this.loadFailed() ? 'unavailable' : 'loading';
        }
        const now = this.now();
        const windowEnded = this.windowEnded();
        // A check that was not started can no longer be started once the window leaves less time than one question needs
        const tooLateToStart = windowEnded || (!!check.newQuestionsUntil && now.isAfter(check.newQuestionsUntil));
        // Once no question can be shown any more, a decision can no longer lead to one, so the student sees why the check is closed or unavailable instead.
        // A started check still takes the answer to its open question until the window ends.
        const decisionsTooLate = check.status === MetisCheckStatus.IN_PROGRESS ? windowEnded : tooLateToStart;
        if (!decisionsTooLate && this.needsConsent(check)) {
            return 'consent';
        }
        switch (check.status) {
            // A check that was never started is closed once it can no longer be started, whether or not its questions were ready in time.
            case MetisCheckStatus.PENDING:
            case MetisCheckStatus.GENERATING:
                return tooLateToStart ? 'closed' : 'preparing';
            case MetisCheckStatus.IN_REVIEW:
                return tooLateToStart ? 'closed' : 'review';
            case MetisCheckStatus.READY:
                if (tooLateToStart) {
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

    constructor() {
        // A host may hand the panel another exercise without creating it anew, for instance when the student moves on to the next exercise and the page and
        // its tab stay: what the panel holds of the previous exercise goes, and the check of the new one is loaded
        effect(() => {
            const exerciseId = this.exerciseId();
            untracked(() => {
                if (this.shownExerciseId !== undefined && exerciseId !== this.shownExerciseId) {
                    this.startOver();
                }
            });
        });
        // With a new decision the server may have put a skipped check back into the queue, and a refused start or answer can go out again
        effect(() => {
            const decision = this.accountService.userIdentity()?.selectedLLMUsage;
            untracked(() => {
                if (decision !== this.decisionOfLastLoad) {
                    this.consentRefused.set(false);
                    this.load();
                }
            });
        });
    }

    ngOnInit(): void {
        this.shownExerciseId = this.exerciseId();
        this.load();
    }

    ngOnDestroy(): void {
        this.destroyed = true;
        this.cancelPending();
    }

    /** Cancels every request and timer of the panel: the load, a start or an answer on its way, the pending reload and the pending resend of an answer. */
    private cancelPending(): void {
        this.clearRefreshTimer();
        if (this.resendTimer !== undefined) {
            window.clearTimeout(this.resendTimer);
            this.resendTimer = undefined;
        }
        this.loadSubscription?.unsubscribe();
        this.startSubscription?.unsubscribe();
        this.answerSubscription?.unsubscribe();
    }

    /**
     * Forgets the check of the previous exercise and loads the one of the current exercise. Whatever belonged to the previous exercise is cancelled, so
     * that neither its check nor its draft is shown here, and no start, reload or resend of it goes out against this exercise with a question of the other.
     */
    private startOver(): void {
        this.shownExerciseId = this.exerciseId();
        this.cancelPending();
        this.check.set(undefined);
        this.loadFailed.set(false);
        this.draft.set('');
        this.submitting.set(false);
        this.starting.set(false);
        this.lastAnswerExpired.set(false);
        this.consentRefused.set(false);
        this.retryDelay = FIRST_RETRY_DELAY_MS;
        this.ratingRefreshDelay = RATING_REFRESH_MS;
        this.now.set(this.serverDateService.now());
        this.load();
    }

    /**
     * Starts the check: the server switches it to IN_PROGRESS and hands back the first question. A start refused for the
     * student's AI decision leads to the consent request; any other refusal (the window has just closed, or leaves no time
     * for a question) loads the check again, so the panel shows why. Only one start is on its way at a time.
     */
    protected start(): void {
        if (this.starting()) {
            return;
        }
        this.starting.set(true);
        this.startSubscription = this.metisService
            .startCheck(this.exerciseId())
            .pipe(
                finalize(() => this.starting.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (check) => this.applyChangedCheck(check),
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

    /**
     * Takes the text of the answer box as the draft of the current question and keeps it beyond the panel.
     * @param text the text of the answer box
     */
    protected updateDraft(text: string): void {
        this.draft.set(text);
        const questionId = this.check()?.currentQuestion?.id;
        if (questionId !== undefined) {
            this.drafts.keep(this.exerciseId(), questionId, text);
        }
    }

    /**
     * Enter submits the draft; Shift+Enter keeps the browser's own line break, and an Enter that confirms the text of an input method editor, for instance
     * for Chinese or Japanese, belongs to the editor.
     */
    protected onAnswerKeydown(event: KeyboardEvent): void {
        // Safari may confirm a candidate after compositionend with keyCode 229 and isComposing=false.
        if (event.isComposing || event.keyCode === 229) {
            return;
        }
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
     * the draft once more. An answer whose request broke off or went unanswered, timed or sent by hand, is first sent again
     * within the grace of the server.
     * @param expired whether the question's timer ran out, in which case an empty draft is sent too
     */
    protected submit(expired = false): void {
        const question = this.check()?.currentQuestion;
        if (!question || this.submitting() || (!expired && !this.draft().trim())) {
            return;
        }
        this.lastAnswerExpired.set(expired);
        this.sendAnswer(question.id, this.draft(), 0);
    }

    /**
     * Sends an answer and applies the check the server hands back. An answer whose request broke off or went unanswered is
     * sent again after each of the short waits, with the text it had then and the answer box locked meanwhile: either the
     * server stores it now, or it had stored it and hands back the check with the question it opened meanwhile, which the
     * student sees at once instead of after the retry wait, when its time might have run out. Once those attempts are
     * spent, and for every other failure, the check is loaded after the retry wait.
     * @param questionId the question the answer is for
     * @param text the answer as it goes out
     * @param attempt how often the answer was sent again already
     */
    private sendAnswer(questionId: number, text: string, attempt: number): void {
        this.submitting.set(true);
        this.answerSubscription = this.metisService
            .submitAnswer(this.exerciseId(), questionId, text)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (check) => {
                    this.draft.set('');
                    this.applyChangedCheck(check);
                    this.submitting.set(false);
                },
                error: (error: unknown) => {
                    if (attempt < ANSWER_RETRY_DELAYS_MS.length && isTransientFailure(error)) {
                        // The answer box stays locked, so nothing else goes out until this attempt is answered; a reload meanwhile neither cancels it nor
                        // unlocks the box, and the attempt either stores the answer or brings back the check as the server has it
                        this.resendTimer = window.setTimeout(() => {
                            this.resendTimer = undefined;
                            this.sendAnswer(questionId, text, attempt + 1);
                        }, ANSWER_RETRY_DELAYS_MS[attempt]);
                        return;
                    }
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
        this.decisionOfLastLoad = this.accountService.userIdentity()?.selectedLLMUsage;
        this.loadSubscription?.unsubscribe();
        this.loadSubscription = this.metisService.studentCheck(this.exerciseId()).subscribe({
            next: (check) => this.applyCheck(check),
            error: (error: unknown) => this.onLoadFailed(error),
        });
    }

    /**
     * A failed first load leaves nothing to show, so the panel says that there is no check. A failed reload keeps the last
     * check on screen and is tried again later, so a passing failure, such as a whole cohort reloading at the window start,
     * does not freeze the panel at a boundary; each further failure doubles the wait. A first load that broke off or went
     * unanswered is tried again as well: the panel is also created anew while a question is open, when the split view
     * changes its layout, and has to come back to that question and its kept draft. A first load the server refused stays.
     * @param error what the load failed with
     */
    private onLoadFailed(error: unknown): void {
        if (!this.check()) {
            this.loadFailed.set(true);
            if (!isTransientFailure(error)) {
                return;
            }
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
     * Takes over the check that a start or an answer brought back. A load still on its way read the check before the change, or
     * while it was being made, so it is cancelled: its response, should it arrive later, would put the older state back, for
     * instance a check in progress over the completed one, after which the panel would stop asking for it.
     */
    private applyChangedCheck(check: MetisStudentCheckDTO): void {
        this.loadSubscription?.unsubscribe();
        this.applyCheck(check);
    }

    /**
     * Takes over a check from the server together with the server time it was read at, and schedules the next reload. The draft
     * belongs to the question it was typed for, so it is dropped when the check shows another question or none: after an answer
     * whose response was lost, the reload shows the next question, whose timer would otherwise send the old draft as its answer.
     * A panel created anew while the question is open gets back the draft that the panel before it kept.
     */
    private applyCheck(check: MetisStudentCheckDTO): void {
        const questionId = check.currentQuestion?.id;
        if (questionId === undefined) {
            this.drafts.drop(this.exerciseId());
            this.draft.set('');
        } else if (questionId !== this.check()?.currentQuestion?.id) {
            this.draft.set(this.drafts.restore(this.exerciseId(), questionId));
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
     * window start before the window, polled while the questions are prepared or an answer is rated, while the check
     * is open or waits for the student's consent at the moment it can no longer be started or, once started, at the
     * window end, at the window end for a check that closed before it, and once the window has ended only while shown
     * ratings are still missing.
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
                return this.untilBoundary(check.status === MetisCheckStatus.IN_PROGRESS ? check.windowEnd : check.newQuestionsUntil);
            case 'ready':
                return this.untilBoundary(check.newQuestionsUntil);
            case 'answering':
                return this.untilBoundary(check.windowEnd);
            case 'done':
                return check.status === MetisCheckStatus.IN_PROGRESS ? WAITING_REFRESH_MS : this.untilBoundary(check.windowEnd);
            case 'closed':
                if (!this.windowEnded()) {
                    // Closed because the rest of the window is too short for a question: loaded once more at its end, after which the text says it has ended
                    return this.untilBoundary(check.windowEnd);
                }
                return this.untilMissingRatings(check);
            case 'results':
                return this.untilMissingRatings(check);
            case 'loading':
            case 'unavailable':
                return undefined;
        }
    }

    /**
     * The wait before the next reload while the shown ratings are incomplete: an answer whose live rating failed is rated
     * later by the server, and the student would otherwise see its score only after reloading the page. The wait doubles
     * with every reload that still finds a rating missing, so a server that cannot rate is asked less and less often.
     * @param check the check that was just applied
     * @returns the wait, or undefined when no shown answer misses its rating
     */
    private untilMissingRatings(check: MetisStudentCheckDTO): number | undefined {
        if (!check.ratingsVisible || check.answeredQuestions.every((question) => question.llmScore !== undefined)) {
            this.ratingRefreshDelay = RATING_REFRESH_MS;
            return undefined;
        }
        const delay = this.ratingRefreshDelay;
        this.ratingRefreshDelay = Math.min(2 * delay, MAX_REFRESH_DELAY_MS);
        return delay;
    }

    /** Milliseconds from the last load until just after a boundary of the window, or undefined without the boundary. */
    private untilBoundary(boundary: dayjs.Dayjs | undefined): number | undefined {
        return boundary ? Math.max(0, boundary.diff(this.now())) + BOUNDARY_SLACK_MS : undefined;
    }
}
