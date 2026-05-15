import {
    ApplicationRef,
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    ComponentRef,
    EnvironmentInjector,
    OnDestroy,
    OnInit,
    ViewContainerRef,
    createComponent,
    effect,
    inject,
    input,
    output,
    untracked,
    viewChild,
} from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import type { PluginSimple } from 'markdown-it';
import { catchError, debounceTime, filter, map, mergeMap, switchMap, tap } from 'rxjs/operators';
import { Observable, Subject, Subscription, merge, of } from 'rxjs';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { ProgrammingExerciseTaskExtensionWrapper, taskRegex } from './extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { TaskArray } from 'app/programming/shared/instructions-render/task/programming-exercise-task.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { ResultService } from 'app/exercise/result/result.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { findLatestResult } from 'app/shared/util/utils';
import { faFileAlt, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/overview/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import diff from 'html-diff-ts';
import { ProgrammingExerciseInstructionService } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { escapeStringForUseInRegex } from 'app/shared/util/string-pure.utils';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/programming/shared/instructions-render/task/programming-exercise-instruction-task-status.component';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ProgrammingExerciseInstructionStepWizardComponent } from './step-wizard/programming-exercise-instruction-step-wizard.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-programming-exercise-instructions',
    templateUrl: './programming-exercise-instruction.component.html',
    styleUrls: ['./programming-exercise-instruction.scss'],
    imports: [ProgrammingExerciseInstructionStepWizardComponent, ExamExerciseUpdateHighlighterComponent, FaIconComponent, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgrammingExerciseInstructionComponent implements OnInit, OnDestroy {
    private viewContainerRef = inject(ViewContainerRef);
    private resultService = inject(ResultService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private programmingExerciseTaskWrapper = inject(ProgrammingExerciseTaskExtensionWrapper);
    private programmingExercisePlantUmlWrapper = inject(ProgrammingExercisePlantUmlExtensionWrapper);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private programmingExerciseGradingService = inject(ProgrammingExerciseGradingService);
    private sanitizer = inject(DomSanitizer);
    private programmingExerciseInstructionService = inject(ProgrammingExerciseInstructionService);
    private appRef = inject(ApplicationRef);
    private injector = inject(EnvironmentInjector);
    private themeService = inject(ThemeService);
    private cdr = inject(ChangeDetectorRef);

    readonly exercise = input<ProgrammingExercise | undefined>(undefined);
    public readonly participation = input<Participation | undefined>(undefined);
    readonly generateHtmlEvents = input<Observable<void>>();
    readonly personalParticipation = input.required<boolean>();

    public readonly onNoInstructionsAvailable = output();

    readonly examExerciseUpdateHighlighterComponent = viewChild(ExamExerciseUpdateHighlighterComponent);

    private problemStatement: string | undefined;
    private participationSubscription?: Subscription;
    private testCasesSubscription?: Subscription;

    protected isInitial = true;
    protected isLoading: boolean;
    latestResultValue?: Result;
    // Page-scoped so multiple problem statements (e.g. across exam exercise tabs) don't collide.
    private taskIndex = 0;
    protected tasks: TaskArray;
    private taskComponentRefs: ComponentRef<ProgrammingExerciseInstructionTaskStatusComponent>[] = [];
    private lastRenderedProblemStatement?: string;

    get latestResult() {
        return this.latestResultValue;
    }

    set latestResult(result: Result | undefined) {
        this.latestResultValue = result;
        this.programmingExercisePlantUmlWrapper.setLatestResult(this.latestResultValue);
        this.programmingExercisePlantUmlWrapper.setTestCases(this.testCases);
    }

    protected renderedMarkdown: SafeHtml | undefined;
    private injectableContentForMarkdownCallbacks: Array<() => void> = [];

    markdownExtensions: PluginSimple[];
    private injectableContentFoundSubscription: Subscription;
    private generateHtmlSubscription: Subscription;
    private testCases?: ProgrammingExerciseTestCase[];

    private problemStatementUpdateSubject = new Subject<void>();

    private lastSeenParticipation?: Participation;
    private lastSeenProblemStatement?: string;

    constructor() {
        this.programmingExerciseTaskWrapper.viewContainerRef = this.viewContainerRef;

        // Skip the initial synchronous run so ngOnInit owns the first render.
        let firstThemeRun = true;
        effect(() => {
            this.themeService.currentTheme();
            if (firstThemeRun) {
                firstThemeRun = false;
                return;
            }
            if (this.isInitial) return;
            this.lastRenderedProblemStatement = undefined;
            untracked(() => this.updateMarkdown());
        });

        this.problemStatementUpdateSubject.pipe(debounceTime(150), takeUntilDestroyed()).subscribe(() => {
            this.updateMarkdown();
        });

        let initialized = false;
        effect(() => {
            const participation = this.participation();
            this.exercise();
            this.generateHtmlEvents();

            if (!initialized) {
                initialized = true;
                this.lastSeenParticipation = participation;
                return;
            }

            const participationChanged = participation !== this.lastSeenParticipation;
            this.lastSeenParticipation = participation;
            this.processInputChanges({ participationChanged });
        });
    }

    ngOnInit(): void {
        if (this.exercise()) {
            this.processInputChanges();
        }
    }

    faSpinner = faSpinner;
    faFileAlt = faFileAlt;

    private processInputChanges({ participationChanged = true }: { participationChanged?: boolean } = {}) {
        const exercise = this.exercise();
        if (exercise?.isAtLeastTutor) {
            if (this.testCasesSubscription) {
                this.testCasesSubscription.unsubscribe();
            }
            this.testCasesSubscription = this.programmingExerciseGradingService
                .getTestCases(exercise.id!)
                .pipe(
                    tap((testCases) => {
                        this.testCases = testCases;
                    }),
                )
                .subscribe();
        }
        of(!!this.markdownExtensions)
            .pipe(
                tap((markdownExtensionsInitialized: boolean) => !markdownExtensionsInitialized && this.setupMarkdownSubscriptions()),
                map(() => participationChanged),
                tap((participationHasChanged: boolean) => {
                    if (participationHasChanged) {
                        this.isInitial = true;
                        if (this.generateHtmlSubscription) {
                            this.generateHtmlSubscription.unsubscribe();
                        }
                        const generateHtmlEvents = this.generateHtmlEvents();
                        if (generateHtmlEvents) {
                            this.generateHtmlSubscription = generateHtmlEvents.subscribe(() => {
                                this.lastRenderedProblemStatement = undefined;
                                this.updateMarkdown();
                            });
                        }
                        if (this.participation() && this.exercise()) {
                            this.setupResultWebsocket();
                        }
                    }
                }),
                switchMap((participationHasChanged: boolean) => {
                    const currentExercise = this.exercise();
                    if (!this.isLoading && currentExercise && this.participation() && (this.isInitial || participationHasChanged)) {
                        this.isLoading = true;
                        return of(currentExercise.problemStatement).pipe(
                            tap((problemStatement) => {
                                this.problemStatement = problemStatement?.trim() || undefined;
                                this.lastSeenProblemStatement = currentExercise.problemStatement;
                            }),
                            switchMap(() => this.loadInitialResult()),
                            tap((latestResult) => {
                                this.latestResult = latestResult;
                            }),
                            tap(() => {
                                this.updateMarkdown();
                                this.isInitial = false;
                                this.isLoading = false;
                            }),
                        );
                    } else if (this.problemStatementHasChangedFromLast() && !this.problemStatement) {
                        // Re-assign latestResult to refresh singleton task/UML extension state.
                        this.latestResult = this.latestResultValue;
                        this.problemStatement = currentExercise?.problemStatement?.trim() || undefined;
                        this.lastSeenProblemStatement = currentExercise?.problemStatement;
                        this.problemStatementUpdateSubject.next();
                        return of(undefined);
                    } else if (currentExercise && this.problemStatementHasChangedFromLast()) {
                        this.latestResult = this.latestResultValue;
                        this.problemStatement = currentExercise.problemStatement?.trim() || undefined;
                        this.lastSeenProblemStatement = currentExercise.problemStatement;
                        this.problemStatementUpdateSubject.next();
                        return of(undefined);
                    } else {
                        return of(undefined);
                    }
                }),
            )
            .subscribe();
    }

    private problemStatementHasChangedFromLast(): boolean {
        return this.lastSeenProblemStatement !== this.exercise()?.problemStatement;
    }

    /**
     * Setup the markdown extensions for parsing the tasks and tests and subscriptions necessary to receive injectable content.
     */
    private setupMarkdownSubscriptions() {
        this.markdownExtensions = [this.programmingExerciseTaskWrapper.getExtension(), this.programmingExercisePlantUmlWrapper.getExtension()];
        if (this.injectableContentFoundSubscription) {
            this.injectableContentFoundSubscription.unsubscribe();
        }
        this.injectableContentFoundSubscription = merge(this.programmingExercisePlantUmlWrapper.subscribeForInjectableElementsFound()).subscribe((injectableCallback) => {
            this.injectableContentForMarkdownCallbacks = [...this.injectableContentForMarkdownCallbacks, injectableCallback];
        });
    }

    /**
     * Set up the websocket for retrieving build results.
     * Online updates the build logs if the result is new, otherwise doesn't react.
     */
    private setupResultWebsocket() {
        if (this.participationSubscription) {
            this.participationSubscription.unsubscribe();
        }
        this.participationSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation()!.id!, this.personalParticipation(), this.exercise()!.id!)
            .pipe(filter((result) => !!result))
            .subscribe((result: Result) => {
                this.latestResult = result;
                this.programmingExercisePlantUmlWrapper.setLatestResult(this.latestResult);
                // Invalidate the render cache since the result changed (test status colors need updating)
                this.lastRenderedProblemStatement = undefined;
                this.updateMarkdown();
            });
    }

    /**
     * Render the markdown into html.
     */
    updateMarkdown() {
        const exercise = this.exercise();
        // Skip re-render if problem statement hasn't changed (optimization for live preview)
        const currentProblemStatement = exercise?.problemStatement?.trim();
        if (currentProblemStatement === this.lastRenderedProblemStatement && !this.isInitial) {
            return;
        }
        this.lastRenderedProblemStatement = currentProblemStatement;

        this.destroyTaskComponents();
        // Reset task index to start fresh for this render
        this.taskIndex = 0;
        // Set the exercise ID so PlantUML container IDs are scoped per exercise.
        // This prevents cross-contamination when multiple exercises are on the same page (e.g. in exams).
        this.programmingExercisePlantUmlWrapper.setExerciseId(exercise?.id);
        // make sure that always the correct result is set, before updating markdown
        // looks weird, but in setter of latestResult are setters of sub components invoked
        this.latestResult = this.latestResultValue;

        this.injectableContentForMarkdownCallbacks = [];
        this.renderMarkdown();
    }

    /**
     * Destroy all dynamically created task components to prevent memory leaks.
     */
    private destroyTaskComponents(): void {
        this.taskComponentRefs.forEach((ref) => {
            // Only detach if the view hasn't been destroyed yet (e.g., during test cleanup)
            if (!ref.hostView.destroyed) {
                this.appRef.detachView(ref.hostView);
                ref.destroy();
            }
        });
        this.taskComponentRefs = [];
    }

    renderUpdatedProblemStatement() {
        this.updateMarkdown();
    }

    /**
     * This method is used for initially loading the results so that the instructions can be rendered.
     */
    loadInitialResult(): Observable<Result | undefined> {
        const participation = this.participation();
        const results = getAllResultsOfAllSubmissions(participation?.submissions);
        if (participation?.id && results.length) {
            // Get the result with the highest id (most recent result)
            const latestResult = findLatestResult(results);
            if (!latestResult) {
                return of(undefined);
            }
            return latestResult.feedbacks ? of(latestResult) : this.loadAndAttachResultDetails(latestResult);
        } else if (participation && participation.id) {
            // Only load results if the exercise already is in our database, otherwise there can be no build result anyway
            return this.loadLatestResult();
        } else {
            return of(undefined);
        }
    }

    /**
     * Retrieve latest result for the participation/exercise/course combination.
     * If there is no result, return undefined.
     */
    loadLatestResult(): Observable<Result | undefined> {
        return this.programmingExerciseParticipationService.getLatestResultWithFeedback(this.participation()!.id!).pipe(
            catchError(() => of(undefined)),
            mergeMap((latestResult: Result) => (latestResult && !latestResult.feedbacks ? this.loadAndAttachResultDetails(latestResult) : of(latestResult))),
        );
    }

    /**
     * Fetches details for the result (if we received one) and attach them to the result.
     * Mutates the input parameter result.
     * @param result - result to which instructions will be attached.
     */
    loadAndAttachResultDetails(result: Result): Observable<Result> {
        const currentParticipation = this.participation();
        return this.resultService.getFeedbackDetailsForResult(currentParticipation!.id!, result).pipe(
            map((res) => res && res.body),
            map((feedbacks: Feedback[]) => {
                result.feedbacks = feedbacks;
                return result;
            }),
            catchError(() => of(result)),
        );
    }

    private renderMarkdown(): void {
        // Highlight differences between previous and current markdown
        const examExerciseUpdateHighlighterComponent = this.examExerciseUpdateHighlighterComponent();
        if (
            examExerciseUpdateHighlighterComponent?.showHighlightedDifferences() &&
            examExerciseUpdateHighlighterComponent.outdatedProblemStatement &&
            examExerciseUpdateHighlighterComponent.updatedProblemStatement
        ) {
            const outdatedMarkdown = htmlForMarkdown(examExerciseUpdateHighlighterComponent.outdatedProblemStatement, this.markdownExtensions);
            const updatedMarkdown = htmlForMarkdown(examExerciseUpdateHighlighterComponent.updatedProblemStatement, this.markdownExtensions);
            const diffedMarkdown = diff(outdatedMarkdown, updatedMarkdown);
            const markdownWithoutTasks = this.prepareTasks(diffedMarkdown);
            const markdownWithTableStyles = this.addStylesForTables(markdownWithoutTasks);
            this.renderedMarkdown = this.sanitizer.bypassSecurityTrustHtml(markdownWithTableStyles ?? markdownWithoutTasks);
            this.cdr.markForCheck();
            // Differences between UMLs are ignored, and we only inject the current one (last callback)
            this.scheduleContentInjection(true);
        } else if (this.exercise()?.problemStatement?.trim()) {
            this.injectableContentForMarkdownCallbacks = [];
            const renderedProblemStatement = htmlForMarkdown(this.exercise()!.problemStatement!, this.markdownExtensions);
            const markdownWithoutTasks = this.prepareTasks(renderedProblemStatement);
            const markdownWithTableStyles = this.addStylesForTables(markdownWithoutTasks);
            this.renderedMarkdown = this.sanitizer.bypassSecurityTrustHtml(markdownWithTableStyles ?? markdownWithoutTasks);
            this.cdr.markForCheck();
            this.scheduleContentInjection(false);
        } else {
            // Clear the rendered markdown when problem statement is empty or whitespace-only
            this.renderedMarkdown = undefined;
            this.injectableContentForMarkdownCallbacks = [];
            this.cdr.markForCheck();
        }
    }

    /**
     * Schedules the injection of dynamic content (UML diagrams, task components) into the DOM.
     * Uses setTimeout to ensure the DOM has been updated before injection.
     * @param onlyLastCallback If true, only invokes the last callback (for diff mode where only current UML matters)
     */
    private scheduleContentInjection(onlyLastCallback: boolean): void {
        setTimeout(() => {
            if (onlyLastCallback) {
                const lastCallback = this.injectableContentForMarkdownCallbacks[this.injectableContentForMarkdownCallbacks.length - 1];
                if (lastCallback) {
                    lastCallback();
                }
            } else {
                this.injectableContentForMarkdownCallbacks.forEach((callback) => callback());
            }
            this.injectTasksIntoDocument();
        }, 0);
    }

    addStylesForTables(markdownWithoutTasks: string): string | undefined {
        if (!markdownWithoutTasks?.includes('<table')) {
            return;
        } else {
            const parser = new DOMParser();
            const doc = parser.parseFromString(markdownWithoutTasks as string, 'text/html');
            const tables = doc.querySelectorAll('table');

            tables.forEach((table) => {
                table.style.maxWidth = '100%';
                table.style.overflowX = 'scroll';
                table.style.display = 'block';
            });
            return doc.body.innerHTML;
        }
    }

    prepareTasks(problemStatementHtml: string) {
        const tasks = Array.from(problemStatementHtml.matchAll(taskRegex));
        if (!tasks) {
            return problemStatementHtml;
        }

        this.tasks = tasks
            // check that all groups (full match, name, tests) are present
            .filter((testMatch) => testMatch?.length === 3)
            .map((testMatch: RegExpMatchArray | null) => {
                const nextIndex = this.taskIndex;
                this.taskIndex++;
                return {
                    id: nextIndex,
                    completeString: testMatch![0],
                    taskName: testMatch![1],
                    testIds: testMatch![2] ? this.programmingExerciseInstructionService.convertTestListToIds(testMatch![2], this.testCases) : [],
                };
            });

        return this.tasks.reduce(
            (acc: string, { completeString: task, id }): string =>
                // Insert anchor divs into the text so that injectable elements can be inserted into them.
                // Without class="d-flex" the injected components height would be 0.
                // Added zero-width space as content so the div actually consumes a line to prevent a <ol> display bug in Safari
                acc.replace(new RegExp(escapeStringForUseInRegex(task), 'g'), `<div class="pe-${this.exercise()?.id}-task-${id.toString()} d-flex">&#8203;</div>`),
            problemStatementHtml,
        );
    }

    private injectTasksIntoDocument = () => {
        this.tasks.forEach(({ id, taskName, testIds }) => {
            const taskHtmlContainers = document.getElementsByClassName(`pe-${this.exercise()?.id}-task-${id}`);

            for (let i = 0; i < taskHtmlContainers.length; i++) {
                const taskHtmlContainer = taskHtmlContainers[i];
                this.createTaskComponent(taskHtmlContainer, taskName, testIds);
            }
        });
        // Batch change detection: trigger once after all task components are created
        // instead of calling detectChanges() for each component individually
        this.taskComponentRefs.forEach((ref) => ref.changeDetectorRef.detectChanges());
    };

    private createTaskComponent(taskHtmlContainer: Element, taskName: string, testIds: number[]) {
        const componentRef = createComponent(ProgrammingExerciseInstructionTaskStatusComponent, {
            hostElement: taskHtmlContainer,
            environmentInjector: this.injector,
        });
        componentRef.setInput('exercise', this.exercise());
        componentRef.setInput('participation', this.participation());
        componentRef.setInput('taskName', taskName);
        componentRef.setInput('latestResult', this.latestResult);
        componentRef.setInput('testIds', testIds);
        // Track component ref for cleanup
        this.taskComponentRefs.push(componentRef);
        this.appRef.attachView(componentRef.hostView);
        // Note: detectChanges() is called in batch after all components are created
    }

    // Effects and problemStatementUpdateSubject clean themselves up via DestroyRef / takeUntilDestroyed;
    // only manual rxjs subscriptions are unsubscribed below.
    ngOnDestroy() {
        // Destroy dynamically created task components
        this.destroyTaskComponents();
        if (this.participationSubscription) {
            this.participationSubscription.unsubscribe();
        }
        if (this.generateHtmlSubscription) {
            this.generateHtmlSubscription.unsubscribe();
        }
        if (this.injectableContentFoundSubscription) {
            this.injectableContentFoundSubscription.unsubscribe();
        }
        if (this.testCasesSubscription) {
            this.testCasesSubscription.unsubscribe();
        }
    }
}
