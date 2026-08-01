import { Component, DestroyRef, OnDestroy, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, Observable, Subject, Subscription, catchError, filter, map, of, switchMap } from 'rxjs';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiMessageComponent } from 'app/shared-ui/tum-ui/message/tum-ui-message.component';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { ProblemStatementSsrRenderService } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr-render.service';
import { ProblemStatementResultHydrationService } from 'app/programming/shared/instructions-render/ssr/problem-statement-result-hydration.service';
import {
    ProblemStatementRenderRequest,
    RenderedProblemStatement,
    SSR_TASK_STATUSES,
    SsrTask,
    SsrTaskStatus,
} from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';
import { ProgrammingExerciseInstructionSsrContentComponent } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr-content.component';
import { ProgrammingExerciseInstructionSsrStepWizardComponent } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr-step-wizard.component';

export type SsrLiveUpdates = 'none' | 'personal';

/**
 * Identity of the exercise and participation a render belongs to.
 *
 * Deliberately *not* part of `ProblemStatementRenderRequest`: that interface is the HTTP body and feeds the render
 * cache key, and neither may depend on who is looking at the statement. The identity only exists to decide whether
 * the markup currently on screen still belongs to the bound inputs.
 */
interface RenderedContext {
    exerciseId?: number;
    participationId?: number;
}

/** Internal pairing of a render request with the context it was issued for; only the request goes on the wire. */
interface RenderEnvelope {
    request: ProblemStatementRenderRequest;
    context: RenderedContext;
}

/**
 * Read-only problem statement rendered by the server.
 *
 * This component owns the state: hydration of the effective result, the render request lifecycle, the failure states
 * and the feedback dialog. It deliberately uses **default** encapsulation so its chrome (spinner, banners) is styled
 * by the application's global CSS. The server-rendered markup itself lives in the shadow-DOM child component, which is
 * the only thing that may sit behind a shadow boundary (see its class comment).
 */
@Component({
    selector: 'jhi-programming-exercise-instruction-ssr',
    templateUrl: './programming-exercise-instruction-ssr.component.html',
    styleUrls: ['./programming-exercise-instruction-ssr.component.scss'],
    imports: [
        FaIconComponent,
        TumUiMessageComponent,
        ArtemisTranslatePipe,
        ProgrammingExerciseInstructionSsrContentComponent,
        ProgrammingExerciseInstructionSsrStepWizardComponent,
    ],
})
export class ProgrammingExerciseInstructionSsrComponent implements OnDestroy {
    private renderService = inject(ProblemStatementSsrRenderService);
    private hydrationService = inject(ProblemStatementResultHydrationService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    private themeService = inject(ThemeService);
    private destroyRef = inject(DestroyRef);

    private readonly locale = getCurrentLocaleSignal(this.translateService);
    // `undefined` is the cancellation signal: it supersedes whatever render is in flight without starting a new one.
    private readonly renderRequests = new Subject<RenderEnvelope | undefined>();
    private readonly hydrationRequests = new Subject<{ participation?: Participation; result?: Result }>();
    private readonly hydrationSettled = signal(false);
    private readonly hydrationFailed = signal(false);

    // Deliberately optional, matching the legacy component's contract: `input.required` only guarantees that the
    // binding exists, not that its value is defined, and several hosts bind a class field that is still undefined on
    // the first render pass (for example `signal<ProgrammingExercise>(undefined!)` in the repository view).
    readonly exercise = input<ProgrammingExercise>();
    readonly participation = input<Participation>();
    readonly result = input<Result>();
    readonly liveUpdates = input<SsrLiveUpdates>('none');

    readonly onNoInstructionsAvailable = output<void>();

    readonly renderedHtml = signal<string | undefined>(undefined);
    readonly tasks = signal<SsrTask[]>([]);
    readonly isLoading = signal(false);
    readonly isRefreshing = signal(false);
    readonly initialLoadFailed = signal(false);
    readonly refreshFailed = signal(false);
    readonly errorStatus = signal<number | undefined>(undefined);

    readonly errorMessageKey = computed(() => {
        const base = 'artemisApp.programmingExercise.problemStatement.';
        return base + (this.errorStatus() === 429 ? 'renderRateLimited' : this.errorStatus() === 422 ? 'renderRejected' : 'renderFailed');
    });

    readonly faCircleNotch = faCircleNotch;

    private latestResult = signal<Result | undefined>(undefined);

    /** Identity the markup currently on screen was rendered for; `undefined` while nothing is rendered. */
    private readonly renderedContext = signal<RenderedContext | undefined>(undefined);

    /**
     * Tasks are only interactive when a feedback dialog can actually be opened *for the bound inputs*. After a switch
     * to another exercise or participation the retained DOM still shows the previous statement, whose tasks must not
     * pair the previous result with the new participation.
     */
    readonly canOpenFeedback = computed(() => !!this.latestResult() && !!this.participation() && this.rendersCurrentContext());

    private resultSubscription?: Subscription;
    private contentHash?: string;

    constructor() {
        // One cancelable hydration stream for all three result sources (host input, persisted latest result,
        // websocket push). switchMap guarantees last-wins: a slow hydration for a superseded result can never
        // overwrite a newer one. Every result reaching latestResult() has its feedback details loaded, which is what
        // the render mapping contract requires (feedbacks === undefined must never be sent as []).
        this.hydrationRequests
            .pipe(
                switchMap((request) =>
                    this.hydrate(request).pipe(
                        map((result) => ({ result, failed: false })),
                        catchError(() => of({ result: undefined, failed: true })),
                    ),
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(({ result, failed }) => {
                if (failed) {
                    // A hydration failure is a load failure, not "no result": rendering it would send testResults:null
                    // and replace valid content with neutral tasks. Surface it like a render failure instead.
                    this.isLoading.set(false);
                    this.isRefreshing.set(false);
                    // errorStatus describes the last *render* (HTTP) error. A hydration failure must not inherit it,
                    // or the banner would claim e.g. rate limiting for an entirely unrelated failure.
                    this.errorStatus.set(undefined);
                    if (this.renderedHtml() === undefined) {
                        this.initialLoadFailed.set(true);
                    } else {
                        this.refreshFailed.set(true);
                    }
                } else {
                    // Only a successful hydration may replace the current result. Blanking it on failure would make
                    // canOpenFeedback() false while the untouched DOM still advertises its tasks as buttons.
                    this.latestResult.set(result);
                }
                this.hydrationFailed.set(failed);
                this.hydrationSettled.set(true);
            });

        // Any change of the bound participation/result restarts hydration (and cancels the previous one).
        effect(() => {
            const participation = this.participation();
            const result = this.result();
            untracked(() => this.startHydration({ participation, result }));
        });

        effect(() => {
            const exercise = this.exercise();
            const darkMode = this.themeService.currentTheme() === Theme.DARK;
            const locale = this.locale();
            const result = this.latestResult();
            const settled = this.hydrationSettled();
            const failed = this.hydrationFailed();
            // Render only once hydration settled successfully: rendering earlier fires a spurious "no result"
            // request, and rendering after a failure would discard valid stale content.
            if (settled && !failed) {
                untracked(() => this.requestRender(exercise, result, locale, darkMode));
            }
        });

        effect(() => this.setupResultSubscription());

        // switchMap gives single-in-flight, last-wins semantics: a stale response can never overwrite a newer render.
        // An `undefined` envelope maps to EMPTY, which unsubscribes the in-flight render without issuing a new one.
        // Because EMPTY invokes no subscriber callback, every producer of `undefined` owns the loading flags itself.
        this.renderRequests
            .pipe(
                switchMap((envelope) =>
                    envelope === undefined
                        ? EMPTY
                        : this.renderService.render(envelope.request).pipe(
                              map((rendered) => ({ rendered, error: undefined, context: envelope.context })),
                              catchError((error: HttpErrorResponse) => of({ rendered: undefined, error, context: envelope.context })),
                          ),
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(({ rendered, error, context }) => (rendered ? this.applyRendered(rendered, context) : this.applyError(error)));
    }

    ngOnDestroy(): void {
        this.resultSubscription?.unsubscribe();
    }

    private setupResultSubscription(): void {
        const mode = this.liveUpdates();
        const participationId = this.participation()?.id;
        this.resultSubscription?.unsubscribe();
        this.resultSubscription = undefined;
        if (mode === 'none' || participationId === undefined) {
            return;
        }
        this.resultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(participationId, mode === 'personal', this.exercise()?.id)
            // The websocket emits undefined before the first push; pushed results go through hydration like any other
            // source, because they may arrive without feedback details.
            .pipe(filter((result): result is Result => !!result))
            .subscribe((result) => {
                const participation = this.participation();
                if (participation?.id !== participationId) {
                    // The bound participation already moved on and this effect has not torn the old subject down yet.
                    // Hydrating anyway would label the previous participation's result as the new participation's.
                    return;
                }
                this.startHydration({ participation, result });
            });
    }

    /** Starts (and thereby cancels any in-flight) hydration, resetting the settled/failed state first. */
    private startHydration(request: { participation?: Participation; result?: Result }): void {
        // The real cancellation point of the render lifecycle. The render effect refuses to run while hydration is
        // unsettled or failed, so without this a render started for the previous inputs would keep running and paint
        // the pane once the new hydration stalls or fails.
        this.renderRequests.next(undefined);
        this.hydrationSettled.set(false);
        this.hydrationFailed.set(false);
        // Loading starts here, not when the render request is issued: hydration itself may take a round trip, and a
        // previous failure must stop being displayed as soon as a fresh attempt begins.
        this.enterPendingState();
        this.hydrationRequests.next(request);
    }

    /** Resolves the effective result for a hydration request; errors are surfaced, never swallowed into "no result". */
    private hydrate(request: { participation?: Participation; result?: Result }): Observable<Result | undefined> {
        if (request.result) {
            return this.hydrationService.withFeedbackDetails(request.participation, request.result);
        }
        if (request.participation?.id) {
            return this.hydrationService.initialResult(request.participation);
        }
        return of(undefined);
    }

    private requestRender(exercise: ProgrammingExercise | undefined, result: Result | undefined, locale: string, darkMode: boolean): void {
        if (!exercise) {
            // The exercise is still loading. That is not the same as "this exercise has no problem statement", so the
            // loading indicator stays on and onNoInstructionsAvailable must not fire (it permanently hides the pane in
            // the code editor). The effect re-runs as soon as the exercise arrives. A render started for the previous
            // exercise must not survive this, hence the cancellation; EMPTY reports nothing, so the flags are set here.
            this.renderRequests.next(undefined);
            this.enterPendingState();
            return;
        }
        const markdown = exercise.problemStatement?.trim();
        if (!markdown) {
            // A render for the previous statement must not paint over the pane that is about to be emptied.
            this.renderRequests.next(undefined);
            // startHydration already switched on a loading indicator; there is nothing to render, so clear it again
            // or the spinner would stay forever on an exercise without a problem statement.
            this.isLoading.set(false);
            this.isRefreshing.set(false);
            this.renderedHtml.set(undefined);
            this.tasks.set([]);
            // Cleared alongside the html: otherwise a statement that goes blank and later returns to a previously
            // rendered value would hit the render cache, match the retained hash, and stay blank forever.
            this.contentHash = undefined;
            // Nothing is on screen any more, so no identity may keep claiming that it is.
            this.renderedContext.set(undefined);
            this.onNoInstructionsAvailable.emit();
            return;
        }
        this.renderRequests.next({
            request: { markdown, testResults: this.renderService.mapFeedbacksToTestInputs(result), locale, darkMode },
            context: { exerciseId: exercise.id, participationId: this.participation()?.id },
        });
    }

    /**
     * Switches the chrome to "an answer is on its way": the indicator matching what is on screen, and no leftover
     * error. Every transition that ends up here has stopped showing an answer for whatever the error described, so
     * a spinner next to a stale failure banner is a state the user must never see.
     */
    private enterPendingState(): void {
        const hasContent = this.renderedHtml() !== undefined;
        this.isLoading.set(!hasContent);
        this.isRefreshing.set(hasContent);
        this.initialLoadFailed.set(false);
        this.refreshFailed.set(false);
        // The status describes the previous render error; a fresh attempt must not inherit it.
        this.errorStatus.set(undefined);
    }

    /** Whether the markup on screen was rendered for exactly the exercise and participation currently bound. */
    private rendersCurrentContext(): boolean {
        const rendered = this.renderedContext();
        return rendered !== undefined && rendered.exerciseId === this.exercise()?.id && rendered.participationId === this.participation()?.id;
    }

    private applyRendered(rendered: RenderedProblemStatement, context: RenderedContext): void {
        this.isLoading.set(false);
        this.isRefreshing.set(false);
        this.refreshFailed.set(false);
        this.initialLoadFailed.set(false);
        // Adopted before the hash check: byte-identical output for another participation keeps the DOM, but that DOM
        // now belongs to the new identity. Installing it only past the early return would leave it gated forever.
        this.renderedContext.set(context);
        if (rendered.contentHash === this.contentHash) {
            // Identical output: keep the current DOM so scroll position, focus and the rendered formulas survive
            // untouched. Nothing has to be scheduled here: the content component re-applies the task accessibility
            // attributes by itself whenever the interactivity gating changes.
            return;
        }
        this.contentHash = rendered.contentHash;
        const { html, tasks } = this.extractRenderableHtml(rendered.html);
        this.renderedHtml.set(html);
        this.tasks.set(tasks);
    }

    private applyError(error: HttpErrorResponse): void {
        this.isLoading.set(false);
        this.isRefreshing.set(false);
        this.errorStatus.set(error.status);
        // Never blank an already rendered statement because a refresh failed: show a stale hint instead.
        if (this.renderedHtml() === undefined) {
            this.initialLoadFailed.set(true);
        } else {
            this.refreshFailed.set(true);
        }
    }

    /**
     * Parses the returned document, keeps only the problem-statement fragment plus its stylesheets, and drops every
     * script (the endpoint still appends KaTeX scripts even with includeJs=false; KaTeX runs from Angular instead).
     *
     * The server prepends the stylesheet and the KaTeX link to the fragment inside the body, so both head and body
     * must be searched. Querying the head alone would silently drop all CSS.
     */
    private extractRenderableHtml(document_: string): { html: string; tasks: SsrTask[] } {
        const parsed = new DOMParser().parseFromString(document_, 'text/html');
        parsed.querySelectorAll('script').forEach((script) => script.remove());

        const styles = [...parsed.querySelectorAll('style, link[rel="stylesheet"]')].map((node) => node.outerHTML).join('');
        const fragment = parsed.querySelector('.artemis-problem-statement');
        const tasks = [...(fragment?.querySelectorAll('.artemis-task') ?? [])].map((element, index) => ({
            index,
            taskName: element.getAttribute('data-task-name') ?? '',
            testIds: (element.getAttribute('data-test-ids') ?? '')
                .split(',')
                .filter((value) => value.length > 0)
                .map((value) => Number(value)),
            status: this.parseStatus(element.getAttribute('data-test-status')),
            authoredCount: Number(element.getAttribute('data-authored-count') ?? '0'),
            notExecutedCount: Number(element.getAttribute('data-not-executed-count') ?? '0'),
        }));
        return { html: styles + (fragment?.outerHTML ?? ''), tasks };
    }

    /**
     * Narrows the server's `data-test-status` to the known vocabulary. An unknown value can only come from a server
     * that emits a status this client does not know yet; it degrades to the neutral "no result" circle.
     */
    private parseStatus(value: string | null): SsrTaskStatus {
        return SSR_TASK_STATUSES.find((status) => status === value) ?? 'no-result';
    }

    /** Handles a task activation reported by the content component, identified by its document position. */
    onTaskActivated(index: number): void {
        const task = this.tasks()[index];
        if (task) {
            this.openTaskFeedback(task);
        }
    }

    /**
     * Opens the shared feedback dialog for a task. The not-executed count comes from the server metadata, so the
     * client never recomputes test status.
     *
     * The context match is enforced here rather than only in the accessibility gating: both activation paths (the
     * shadow content and the step wizard) can reach this method, and stale markup must never pair a previous result
     * with the newly bound participation.
     */
    openTaskFeedback(task: SsrTask): void {
        const result = this.latestResult();
        const participation = this.participation();
        const exercise = this.exercise();
        if (!result || !participation || !exercise || !task.testIds.length || !this.rendersCurrentContext()) {
            return;
        }
        this.dialogService.open(FeedbackComponent, {
            header: this.translateService.instant('artemisApp.result.detail.feedbackForTask', { taskName: task.taskName }),
            width: '50rem',
            breakpoints: { '850px': '95vw' },
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: true,
            inputValues: {
                exercise,
                result,
                participation,
                feedbackFilter: task.testIds,
                taskName: task.taskName,
                numberOfNotExecutedTests: task.notExecutedCount,
            },
        });
    }
}
