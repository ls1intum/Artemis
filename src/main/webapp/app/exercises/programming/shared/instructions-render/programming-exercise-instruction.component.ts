import {
    ApplicationRef,
    Component,
    EnvironmentInjector,
    EventEmitter,
    Input,
    OnChanges,
    OnDestroy,
    Output,
    SimpleChanges,
    ViewChild,
    ViewContainerRef,
    createComponent,
} from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ThemeService } from 'app/core/theme/theme.service';
import { ProgrammingExerciseTestCase } from 'app/entities/programming/programming-exercise-test-case.model';
import { ProgrammingExerciseGradingService } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { ShowdownExtension } from 'showdown';
import { catchError, filter, map, mergeMap, switchMap, tap } from 'rxjs/operators';
import { Observable, Subscription, merge, of } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ProgrammingExerciseTaskExtensionWrapper, taskRegex } from './extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/exercises/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { TaskArray } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';
import { Participation } from 'app/entities/participation/participation.model';
import { Feedback } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { problemStatementHasChanged } from 'app/exercises/shared/exercise/exercise.utils';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { Result } from 'app/entities/result.model';
import { findLatestResult } from 'app/shared/util/utils';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { hasParticipationChanged } from 'app/exercises/shared/participation/participation.utils';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import diff from 'html-diff-ts';
import { ProgrammingExerciseInstructionService } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-instruction-task-status.component';

@Component({
    selector: 'jhi-programming-exercise-instructions',
    templateUrl: './programming-exercise-instruction.component.html',
    styleUrls: ['./programming-exercise-instruction.scss'],
})
export class ProgrammingExerciseInstructionComponent implements OnChanges, OnDestroy {
    @Input() public exercise: ProgrammingExercise;
    @Input() public participation: Participation;
    @Input() generateHtmlEvents: Observable<void>;
    @Input() personalParticipation: boolean;
    // Emits an event if the instructions are not available via the problemStatement
    @Output()
    public onNoInstructionsAvailable = new EventEmitter();

    @ViewChild(ExamExerciseUpdateHighlighterComponent) examExerciseUpdateHighlighterComponent: ExamExerciseUpdateHighlighterComponent;

    public problemStatement: string;
    public participationSubscription?: Subscription;
    private testCasesSubscription?: Subscription;

    public isInitial = true;
    public isLoading: boolean;
    public latestResultValue?: Result;
    // unique index, even if multiple tasks are shown from different problem statements on the same page (in different tabs)
    private taskIndex = 0;
    public tasks: TaskArray;

    get latestResult() {
        return this.latestResultValue;
    }

    set latestResult(result: Result | undefined) {
        this.latestResultValue = result;
        this.programmingExercisePlantUmlWrapper.setLatestResult(this.latestResult);
        this.programmingExercisePlantUmlWrapper.setTestCases(this.testCases);
    }

    public renderedMarkdown: SafeHtml;
    private injectableContentForMarkdownCallbacks: Array<() => void> = [];

    markdownExtensions: ShowdownExtension[];
    private injectableContentFoundSubscription: Subscription;
    private tasksSubscription: Subscription;
    private generateHtmlSubscription: Subscription;
    private themeChangeSubscription: Subscription;
    private testCases?: ProgrammingExerciseTestCase[];

    // Icons
    faSpinner = faSpinner;

    constructor(
        public viewContainerRef: ViewContainerRef,
        private resultService: ResultService,
        private participationWebsocketService: ParticipationWebsocketService,
        private programmingExerciseTaskWrapper: ProgrammingExerciseTaskExtensionWrapper,
        private programmingExercisePlantUmlWrapper: ProgrammingExercisePlantUmlExtensionWrapper,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private programmingExerciseGradingService: ProgrammingExerciseGradingService,
        themeService: ThemeService,
        private sanitizer: DomSanitizer,
        private programmingExerciseInstructionService: ProgrammingExerciseInstructionService,
        private appRef: ApplicationRef,
        private injector: EnvironmentInjector,
    ) {
        this.programmingExerciseTaskWrapper.viewContainerRef = this.viewContainerRef;
        this.themeChangeSubscription = themeService.getCurrentThemeObservable().subscribe(() => {
            this.updateMarkdown();
        });
    }

    /**
     * If the participation changes, the participation's instructions need to be loaded and the
     * subscription for the participation's result needs to be set up.
     * @param changes
     */
    public ngOnChanges(changes: SimpleChanges) {
        if (this.exercise?.isAtLeastTutor) {
            if (this.testCasesSubscription) {
                this.testCasesSubscription.unsubscribe();
            }
            this.testCasesSubscription = this.programmingExerciseGradingService
                .getTestCases(this.exercise.id!)
                .pipe(
                    tap((testCases) => {
                        this.testCases = testCases;
                    }),
                )
                .subscribe();
        }
        of(!!this.markdownExtensions)
            .pipe(
                // Set up the markdown extensions if they are not set up yet so that tasks, UMLs, etc. can be parsed.
                tap((markdownExtensionsInitialized: boolean) => !markdownExtensionsInitialized && this.setupMarkdownSubscriptions()),
                // If the participation has changed, set up the websocket subscriptions.
                map(() => hasParticipationChanged(changes)),
                tap((participationHasChanged: boolean) => {
                    if (participationHasChanged) {
                        this.isInitial = true;
                        if (this.generateHtmlSubscription) {
                            this.generateHtmlSubscription.unsubscribe();
                        }
                        if (this.generateHtmlEvents) {
                            this.generateHtmlEvents.subscribe(() => {
                                this.updateMarkdown();
                            });
                        }
                        this.setupResultWebsocket();
                    }
                }),
                switchMap((participationHasChanged: boolean) => {
                    // If the exercise is not loaded, the instructions can't be loaded and so there is no point in loading the results, etc, yet.
                    if (!this.isLoading && this.exercise && this.participation && (this.isInitial || participationHasChanged)) {
                        this.isLoading = true;
                        return of(this.exercise.problemStatement).pipe(
                            // If no instructions can be loaded, abort pipe and hide the instruction panel
                            tap((problemStatement) => {
                                if (!problemStatement) {
                                    this.onNoInstructionsAvailable.emit();
                                    this.isLoading = false;
                                    this.isInitial = false;
                                    return of(undefined);
                                }
                            }),
                            filter((problemStatement) => !!problemStatement),
                            tap((problemStatement) => (this.problemStatement = problemStatement!)),
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
                    } else if (problemStatementHasChanged(changes) && this.problemStatement === undefined) {
                        // Refreshes the state in the singleton task and uml extension service
                        this.latestResult = this.latestResultValue;
                        this.problemStatement = this.exercise.problemStatement!;
                        this.updateMarkdown();
                        return of(undefined);
                    } else if (this.exercise && problemStatementHasChanged(changes)) {
                        // Refreshes the state in the singleton task and uml extension service
                        this.latestResult = this.latestResultValue;
                        this.problemStatement = this.exercise.problemStatement!;
                        return of(undefined);
                    } else {
                        return of(undefined);
                    }
                }),
            )
            .subscribe();
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
            .subscribeForLatestResultOfParticipation(this.participation.id!, this.personalParticipation, this.exercise.id!)
            .pipe(filter((result) => !!result))
            .subscribe((result: Result) => {
                this.latestResult = result;
                this.programmingExercisePlantUmlWrapper.setLatestResult(this.latestResult);
                this.updateMarkdown();
            });
    }

    /**
     * Render the markdown into html.
     */
    updateMarkdown(): void {
        // make sure that always the correct result is set, before updating markdown
        // looks weird, but in setter of latestResult are setters of sub components invoked
        this.latestResult = this.latestResult;

        this.injectableContentForMarkdownCallbacks = [];
        this.renderMarkdown();
    }

    renderUpdatedProblemStatement() {
        this.updateMarkdown();
    }

    /**
     * This method is used for initially loading the results so that the instructions can be rendered.
     */
    loadInitialResult(): Observable<Result | undefined> {
        if (this.participation?.id && this.participation?.results?.length) {
            // Get the result with the highest id (most recent result)
            const latestResult = findLatestResult(this.participation.results);
            if (!latestResult) {
                return of(undefined);
            }
            latestResult.participation = this.participation;
            return latestResult.feedbacks ? of(latestResult) : this.loadAndAttachResultDetails(latestResult);
        } else if (this.participation && this.participation.id) {
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
        return this.programmingExerciseParticipationService.getLatestResultWithFeedback(this.participation.id!, true).pipe(
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
        const currentParticipation = result.participation ? result.participation : this.participation;
        return this.resultService.getFeedbackDetailsForResult(currentParticipation.id!, result).pipe(
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
        if (
            this.examExerciseUpdateHighlighterComponent?.showHighlightedDifferences &&
            this.examExerciseUpdateHighlighterComponent.outdatedProblemStatement &&
            this.examExerciseUpdateHighlighterComponent.updatedProblemStatement
        ) {
            const outdatedMarkdown = htmlForMarkdown(this.examExerciseUpdateHighlighterComponent.outdatedProblemStatement, this.markdownExtensions);
            const updatedMarkdown = htmlForMarkdown(this.examExerciseUpdateHighlighterComponent.updatedProblemStatement, this.markdownExtensions);
            const diffedMarkdown = diff(outdatedMarkdown, updatedMarkdown);
            const markdownWithoutTasks = this.prepareTasks(diffedMarkdown);
            this.renderedMarkdown = this.sanitizer.bypassSecurityTrustHtml(markdownWithoutTasks);
            // Differences between UMLs are ignored, and we only inject the current one
            setTimeout(() => {
                const injectUML = this.injectableContentForMarkdownCallbacks[this.injectableContentForMarkdownCallbacks.length - 1];
                if (injectUML) {
                    injectUML();
                }
                this.injectTasksIntoDocument();
            }, 0);
        } else if (this.exercise?.problemStatement) {
            this.injectableContentForMarkdownCallbacks = [];
            const renderedProblemStatement = htmlForMarkdown(this.exercise.problemStatement, this.markdownExtensions);
            const markdownWithoutTasks = this.prepareTasks(renderedProblemStatement);
            this.renderedMarkdown = this.sanitizer.bypassSecurityTrustHtml(markdownWithoutTasks);
            setTimeout(() => {
                this.injectableContentForMarkdownCallbacks.forEach((callback) => {
                    callback();
                });
                this.injectTasksIntoDocument();
            }, 0);
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
                acc.replace(new RegExp(escapeStringForUseInRegex(task), 'g'), `<div class="pe-${this.exercise.id}-task-${id.toString()} d-flex">&#8203;</div>`),
            problemStatementHtml,
        );
    }

    private injectTasksIntoDocument = () => {
        this.tasks.forEach(({ id, taskName, testIds }) => {
            const taskHtmlContainers = document.getElementsByClassName(`pe-${this.exercise.id}-task-${id}`);

            for (let i = 0; i < taskHtmlContainers.length; i++) {
                const taskHtmlContainer = taskHtmlContainers[i];
                this.createTaskComponent(taskHtmlContainer, taskName, testIds);
            }
        });
    };

    private createTaskComponent(taskHtmlContainer: Element, taskName: string, testIds: number[]) {
        const componentRef = createComponent(ProgrammingExerciseInstructionTaskStatusComponent, {
            hostElement: taskHtmlContainer,
            environmentInjector: this.injector,
        });
        componentRef.instance.exercise = this.exercise;
        componentRef.instance.taskName = taskName;
        componentRef.instance.latestResult = this.latestResult;
        componentRef.instance.testIds = testIds;
        this.appRef.attachView(componentRef.hostView);
        componentRef.changeDetectorRef.detectChanges();
    }

    /**
     * Unsubscribes from all subscriptions.
     */
    ngOnDestroy() {
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
        if (this.themeChangeSubscription) {
            this.themeChangeSubscription.unsubscribe();
        }
    }
}
