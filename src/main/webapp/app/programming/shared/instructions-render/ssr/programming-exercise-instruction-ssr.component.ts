import {
    Component,
    DestroyRef,
    ElementRef,
    Injector,
    OnDestroy,
    ViewEncapsulation,
    afterNextRender,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
    viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, Subscription, catchError, filter, map, of, switchMap } from 'rxjs';
import { DialogService } from 'primeng/dynamicdialog';
import { MessageModule } from 'primeng/message';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import katex from 'katex';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { ProblemStatementSsrRenderService } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr-render.service';
import { ProblemStatementResultHydrationService } from 'app/programming/shared/instructions-render/ssr/problem-statement-result-hydration.service';
import { ProblemStatementRenderRequest, RenderedProblemStatement } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';

export interface SsrTask {
    /** Position in document order. Task names are not unique, so the index identifies a task. */
    index: number;
    taskName: string;
    testIds: number[];
    status: string;
    authoredCount: number;
    notExecutedCount: number;
}

export type SsrLiveUpdates = 'none' | 'personal' | 'exercise';

@Component({
    selector: 'jhi-programming-exercise-instruction-ssr',
    templateUrl: './programming-exercise-instruction-ssr.component.html',
    // The endpoint returns a self-contained stylesheet. Shadow DOM scopes it to this component and shields the
    // rendered problem statement from Artemis' global styles in both directions.
    encapsulation: ViewEncapsulation.ShadowDom,
    styleUrls: ['./programming-exercise-instruction-ssr.component.scss'],
    imports: [FaIconComponent, MessageModule, ArtemisTranslatePipe],
    host: {
        '(click)': 'onHostEvent($event)',
        '(keydown.enter)': 'onHostEvent($event)',
        '(keydown.space)': 'onHostEvent($event)',
    },
})
export class ProgrammingExerciseInstructionSsrComponent implements OnDestroy {
    private renderService = inject(ProblemStatementSsrRenderService);
    private hydrationService = inject(ProblemStatementResultHydrationService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    private themeService = inject(ThemeService);
    private sanitizer = inject(DomSanitizer);
    private destroyRef = inject(DestroyRef);
    private injector = inject(Injector);

    private readonly locale = getCurrentLocaleSignal(this.translateService);
    private readonly renderRequests = new Subject<ProblemStatementRenderRequest>();
    private readonly hydrationRequests = new Subject<{ participation?: Participation; result?: Result }>();
    private readonly hydrationSettled = signal(false);
    private readonly hydrationFailed = signal(false);

    readonly exercise = input.required<ProgrammingExercise>();
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

    readonly safeHtml = computed<SafeHtml | undefined>(() => {
        const html = this.renderedHtml();
        // The html is server-generated, sanitized server-side with a jsoup safelist, and all scripts are removed
        // in extractRenderableHtml before it ever reaches this point.
        return html === undefined ? undefined : this.sanitizer.bypassSecurityTrustHtml(html);
    });

    readonly faCircleNotch = faCircleNotch;

    private readonly renderTarget = viewChild<ElementRef<HTMLElement>>('renderTarget');

    private latestResult = signal<Result | undefined>(undefined);
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
                    if (this.renderedHtml() === undefined) {
                        this.initialLoadFailed.set(true);
                    } else {
                        this.refreshFailed.set(true);
                    }
                }
                this.hydrationFailed.set(failed);
                this.latestResult.set(result);
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
        this.renderRequests
            .pipe(
                switchMap((request) =>
                    this.renderService.render(request).pipe(
                        map((rendered) => ({ rendered, error: undefined })),
                        catchError((error: HttpErrorResponse) => of({ rendered: undefined, error })),
                    ),
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(({ rendered, error }) => (rendered ? this.applyRendered(rendered) : this.applyError(error)));
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
            .subscribeForLatestResultOfParticipation(participationId, mode === 'personal', this.exercise().id)
            // The websocket emits undefined before the first push; pushed results go through hydration like any other
            // source, because they may arrive without feedback details.
            .pipe(filter((result): result is Result => !!result))
            .subscribe((result) => this.startHydration({ participation: this.participation(), result }));
    }

    /** Starts (and thereby cancels any in-flight) hydration, resetting the settled/failed state first. */
    private startHydration(request: { participation?: Participation; result?: Result }): void {
        this.hydrationSettled.set(false);
        this.hydrationFailed.set(false);
        // Loading starts here, not when the render request is issued: hydration itself may take a round trip, and a
        // previous failure must stop being displayed as soon as a fresh attempt begins.
        if (this.renderedHtml() === undefined) {
            this.isLoading.set(true);
        } else {
            this.isRefreshing.set(true);
        }
        this.initialLoadFailed.set(false);
        this.refreshFailed.set(false);
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

    private requestRender(exercise: ProgrammingExercise, result: Result | undefined, locale: string, darkMode: boolean): void {
        const markdown = exercise.problemStatement?.trim();
        if (!markdown) {
            // startHydration already switched on a loading indicator; there is nothing to render, so clear it again
            // or the spinner would stay forever on an exercise without a problem statement.
            this.isLoading.set(false);
            this.isRefreshing.set(false);
            this.renderedHtml.set(undefined);
            this.tasks.set([]);
            this.onNoInstructionsAvailable.emit();
            return;
        }
        this.renderRequests.next({ markdown, testResults: this.renderService.mapFeedbacksToTestInputs(result), locale, darkMode });
    }

    private applyRendered(rendered: RenderedProblemStatement): void {
        this.isLoading.set(false);
        this.isRefreshing.set(false);
        this.refreshFailed.set(false);
        this.initialLoadFailed.set(false);
        if (rendered.contentHash === this.contentHash) {
            // Identical output: keep the current DOM so scroll position and focus survive untouched.
            return;
        }
        this.contentHash = rendered.contentHash;
        const focusedTaskIndex = this.focusedTaskIndex();
        const scrollTop = this.scrollTopOfNearestScrollParent();
        const { html, tasks } = this.extractRenderableHtml(rendered.html);
        this.renderedHtml.set(html);
        this.tasks.set(tasks);
        // The DOM is only updated after change detection has run, so post-processing must wait for the next render.
        afterNextRender(() => this.postProcessRenderedDom(focusedTaskIndex, scrollTop), { injector: this.injector });
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
     * must be searched — querying the head alone would silently drop all CSS.
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
            status: element.getAttribute('data-test-status') ?? 'no-result',
            authoredCount: Number(element.getAttribute('data-authored-count') ?? '0'),
            notExecutedCount: Number(element.getAttribute('data-not-executed-count') ?? '0'),
        }));
        return { html: styles + (fragment?.outerHTML ?? ''), tasks };
    }

    /** Index of the currently focused task inside the shadow root, so focus can be restored after a re-render. */
    private focusedTaskIndex(): number | undefined {
        const host = this.renderTarget()?.nativeElement;
        const active = (host?.getRootNode() as ShadowRoot | undefined)?.activeElement;
        if (!host || !active) {
            return undefined;
        }
        const index = this.taskElements(host).indexOf(active as HTMLElement);
        return index === -1 ? undefined : index;
    }

    private taskElements(host: HTMLElement): HTMLElement[] {
        return [...host.querySelectorAll<HTMLElement>('.artemis-task')];
    }

    /** The nearest scrollable ancestor outside the shadow root, whose position must survive a full re-render. */
    private scrollParent(): HTMLElement | undefined {
        let node = (this.renderTarget()?.nativeElement.getRootNode() as ShadowRoot | undefined)?.host?.parentElement ?? undefined;
        while (node) {
            if (node.scrollHeight > node.clientHeight && ['auto', 'scroll'].includes(getComputedStyle(node).overflowY)) {
                return node;
            }
            node = node.parentElement ?? undefined;
        }
        return undefined;
    }

    private scrollTopOfNearestScrollParent(): number | undefined {
        return this.scrollParent()?.scrollTop;
    }

    private postProcessRenderedDom(focusedTaskIndex: number | undefined, scrollTop: number | undefined): void {
        const host = this.renderTarget()?.nativeElement;
        if (!host) {
            return;
        }
        if (scrollTop !== undefined) {
            const scrollParent = this.scrollParent();
            if (scrollParent) {
                scrollParent.scrollTop = scrollTop;
            }
        }
        // The server emits inert <span class="katex-formula" data-formula data-display-mode> placeholders instead of
        // rendered math (its own script is stripped), so KaTeX must run over exactly those nodes.
        host.querySelectorAll<HTMLElement>('.katex-formula').forEach((element) => {
            const formula = element.getAttribute('data-formula') ?? '';
            try {
                katex.render(formula, element, { displayMode: element.getAttribute('data-display-mode') === 'true', throwOnError: false, output: 'html' });
            } catch {
                element.textContent = formula;
            }
        });

        // Tasks are only interactive when a feedback dialog can actually be opened.
        const interactive = this.canOpenFeedback();
        this.taskElements(host).forEach((element, index) => {
            const task = this.tasks()[index];
            element.setAttribute('aria-label', this.taskAriaLabel(task));
            if (interactive && task?.testIds.length) {
                element.setAttribute('role', 'button');
                element.setAttribute('tabindex', '0');
            } else {
                element.removeAttribute('role');
                element.removeAttribute('tabindex');
            }
        });

        if (focusedTaskIndex !== undefined) {
            this.taskElements(host)[focusedTaskIndex]?.focus();
        }
    }

    private taskAriaLabel(task: SsrTask | undefined): string {
        if (!task) {
            return '';
        }
        // Own key set: artemisApp.editor.testStatusLabels only defines noResult, noTests, testPassing and
        // totalTestsPassing, so there is no existing key for "failed" or "not executed".
        return `${task.taskName}: ${this.translateService.instant('artemisApp.programmingExercise.problemStatement.taskStatus.' + task.status)}`;
    }

    private canOpenFeedback(): boolean {
        return !!this.latestResult() && !!this.participation();
    }

    /**
     * Resolves a click or keyboard activation inside the shadow root to the task it happened on.
     *
     * Events are retargeted at the shadow boundary, so `event.target` is the host element from the outside;
     * `composedPath()` still contains the real node inside the shadow tree.
     */
    onHostEvent(event: Event): void {
        const taskElement = event.composedPath().find((target): target is HTMLElement => target instanceof HTMLElement && target.classList.contains('artemis-task'));
        const host = this.renderTarget()?.nativeElement;
        if (!taskElement || !host) {
            return;
        }
        // Resolve by document position, not by name: task names are not guaranteed to be unique.
        const index = this.taskElements(host).indexOf(taskElement);
        const task = index === -1 ? undefined : this.tasks()[index];
        if (task) {
            event.preventDefault();
            this.openTaskFeedback(task);
        }
    }

    /**
     * Opens the shared feedback dialog for a task. The not-executed count comes from the server metadata, so the
     * client never recomputes test status.
     */
    openTaskFeedback(task: SsrTask): void {
        const result = this.latestResult();
        const participation = this.participation();
        if (!result || !participation || !task.testIds.length) {
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
                exercise: this.exercise(),
                result,
                participation,
                feedbackFilter: task.testIds,
                taskName: task.taskName,
                numberOfNotExecutedTests: task.notExecutedCount,
            },
        });
    }
}
