import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { ShowdownExtension } from 'showdown';
import { catchError, filter, flatMap, map, switchMap, tap } from 'rxjs/operators';
import { Feedback } from 'app/entities/feedback';
import { Result, ResultService } from 'app/entities/result';
import { ProgrammingExercise } from '../programming-exercise.model';
import { RepositoryFileService } from 'app/entities/repository';
import { hasParticipationChanged, Participation, ParticipationWebsocketService } from 'app/entities/participation';
import { merge, Observable, Subscription } from 'rxjs';
import { problemStatementHasChanged } from 'app/entities/exercise';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ProgrammingExerciseTaskExtensionWrapper } from './extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/entities/programming-exercise/instructions/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction.service';
import { TaskArray } from 'app/entities/programming-exercise/instructions/programming-exercise-task.model';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services';

@Component({
    selector: 'jhi-programming-exercise-instructions',
    templateUrl: './programming-exercise-instruction.component.html',
    styleUrls: ['./programming-exercise-instruction.scss'],
})
export class ProgrammingExerciseInstructionComponent implements OnChanges, OnDestroy {
    @Input()
    public exercise: ProgrammingExercise;
    @Input()
    public participation: Participation;
    @Input() generateHtmlEvents: Observable<void>;
    // If there are no instructions available (neither in the exercise problemStatement or the legacy README.md) emits an event
    @Output()
    public onNoInstructionsAvailable = new EventEmitter();

    public problemStatement: string;
    public participationSubscription: Subscription;

    public isInitial = true;
    public isLoading: boolean;
    public latestResultValue: Result | null;

    get latestResult() {
        return this.latestResultValue;
    }

    set latestResult(result: Result | null) {
        this.latestResultValue = result;
        this.programmingExerciseTaskWrapper.setLatestResult(this.latestResult);
        this.programmingExercisePlantUmlWrapper.setLatestResult(this.latestResult);
    }

    public tasks: TaskArray;
    public renderedMarkdown: SafeHtml;
    private injectableContentForMarkdownCallbacks: Array<() => void> = [];

    markdownExtensions: ShowdownExtension[];
    private injectableContentFoundSubscription: Subscription;
    private tasksSubscription: Subscription;

    generateHtmlSubscription: Subscription;

    constructor(
        private translateService: TranslateService,
        private resultService: ResultService,
        private repositoryFileService: RepositoryFileService,
        private participationWebsocketService: ParticipationWebsocketService,
        private markdownService: ArtemisMarkdown,
        private programmingExerciseInstructionService: ProgrammingExerciseInstructionService,
        private programmingExerciseTaskWrapper: ProgrammingExerciseTaskExtensionWrapper,
        private programmingExercisePlantUmlWrapper: ProgrammingExercisePlantUmlExtensionWrapper,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
    ) {}

    /**
     * If the participation changes, the participation's instructions need to be loaded and the
     * subscription for the participation's result needs to be set up.
     * @param changes
     */
    public ngOnChanges(changes: SimpleChanges) {
        // Set up the markdown extensions if they are not set up yet so that tasks, UMLs, etc. can be parsed.
        if (!this.markdownExtensions) {
            this.setupMarkdownSubscriptions();
        }
        const participationHasChanged = hasParticipationChanged(changes);
        // It is possible that the exercise does not have an id in case it is being created now.
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
        // If the exercise is not loaded, the instructions can't be loaded and so there is no point in loading the results, etc, yet.
        if (!this.isLoading && this.exercise && this.participation && (this.isInitial || participationHasChanged)) {
            this.isLoading = true;
            this.loadInstructions()
                .pipe(
                    // If no instructions can be loaded, abort pipe and hide the instruction panel
                    tap(problemStatement => {
                        if (!problemStatement) {
                            this.onNoInstructionsAvailable.emit();
                            this.isLoading = false;
                            this.isInitial = false;
                            return Observable.of(null);
                        }
                    }),
                    filter(problemStatement => !!problemStatement),
                    tap(problemStatement => (this.problemStatement = problemStatement!)),
                    switchMap(() => this.loadInitialResult()),
                    tap(latestResult => {
                        this.latestResult = latestResult;
                    }),
                    tap(() => {
                        this.updateMarkdown();
                        this.isInitial = false;
                        this.isLoading = false;
                    }),
                )
                .subscribe();
        } else if (problemStatementHasChanged(changes) && this.problemStatement === undefined) {
            this.problemStatement = this.exercise.problemStatement!;
            this.updateMarkdown();
        } else if (this.exercise && problemStatementHasChanged(changes)) {
            this.problemStatement = this.exercise.problemStatement!;
        }
    }

    /**
     * Setup the markdown extensions for parsing the tasks and tests and subscriptions necessary to receive injectable content.
     */
    private setupMarkdownSubscriptions() {
        this.markdownExtensions = [this.programmingExerciseTaskWrapper.getExtension(), this.programmingExercisePlantUmlWrapper.getExtension()];
        if (this.injectableContentFoundSubscription) {
            this.injectableContentFoundSubscription.unsubscribe();
        }
        this.injectableContentFoundSubscription = merge(
            this.programmingExerciseTaskWrapper.subscribeForInjectableElementsFound(),
            this.programmingExercisePlantUmlWrapper.subscribeForInjectableElementsFound(),
        ).subscribe(injectableCallback => (this.injectableContentForMarkdownCallbacks = [...this.injectableContentForMarkdownCallbacks, injectableCallback]));
        if (this.tasksSubscription) {
            this.tasksSubscription.unsubscribe();
        }
        this.tasksSubscription = this.programmingExerciseTaskWrapper.subscribeForFoundTestsInTasks().subscribe((tasks: TaskArray) => {
            this.tasks = tasks;
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
            .subscribeForLatestResultOfParticipation(this.participation.id)
            .pipe(filter(participation => !!participation))
            .subscribe((result: Result) => {
                this.latestResult = result;
                this.programmingExerciseTaskWrapper.setLatestResult(this.latestResult);
                this.programmingExercisePlantUmlWrapper.setLatestResult(this.latestResult);
                this.updateMarkdown();
            });
    }

    /**
     * Render the markdown into html.
     */
    updateMarkdown(): void {
        this.injectableContentForMarkdownCallbacks = [];
        this.renderedMarkdown = this.markdownService.htmlForMarkdown(this.problemStatement, this.markdownExtensions);
        // Wait a tick for the template to render before injecting the content.
        setTimeout(() => this.injectableContentForMarkdownCallbacks.forEach(callback => callback()), 0);
    }

    /**
     * This method is used for initially loading the results so that the instructions can be rendered.
     */
    loadInitialResult(): Observable<Result | null> {
        if (this.participation && this.participation.id && this.participation.results && this.participation.results.length) {
            // Get the result with the highest id (most recent result)
            const latestResult = this.participation.results.reduce((acc, v) => (v.id > acc.id ? v : acc));
            if (!latestResult) {
                return Observable.of(null);
            }
            return latestResult.feedbacks ? Observable.of(latestResult) : this.loadAndAttachResultDetails(latestResult);
        } else if (this.participation && this.participation.id) {
            // Only load results if the exercise already is in our database, otherwise there can be no build result anyway
            return this.loadLatestResult();
        } else {
            return Observable.of(null);
        }
    }

    /**
     * Retrieve latest result for the participation/exercise/course combination.
     * If there is no result, return null.
     */
    loadLatestResult(): Observable<Result | null> {
        return this.programmingExerciseParticipationService.getLatestResultWithFeedback(this.participation.id).pipe(
            catchError(() => Observable.of(null)),
            flatMap((latestResult: Result) => (latestResult && !latestResult.feedbacks ? this.loadAndAttachResultDetails(latestResult) : Observable.of(latestResult))),
        );
    }

    /**
     * @function loadResultDetails
     * @desc Fetches details for the result (if we received one) and attach them to the result.
     * Mutates the input parameter result.
     */
    loadAndAttachResultDetails(result: Result): Observable<Result> {
        return this.resultService.getFeedbackDetailsForResult(result.id).pipe(
            map(res => res && res.body),
            map((feedbacks: Feedback[]) => {
                result.feedbacks = feedbacks;
                return result;
            }),
            catchError(() => Observable.of(result)),
        );
    }

    /**
     * @function loadInstructions
     * @desc Loads the instructions for the programming exercise.
     * We added the problemStatement later, historically the instructions where a file in the student's repository
     * This is why we now prefer the problemStatement and if it doesn't exist try to load the readme.
     */
    loadInstructions(): Observable<string | null> {
        if (this.exercise.problemStatement !== null && this.exercise.problemStatement !== undefined) {
            return Observable.of(this.exercise.problemStatement);
        } else {
            if (!this.participation.id) {
                return Observable.of(null);
            }
            return this.repositoryFileService.get(this.participation.id, 'README.md').pipe(
                catchError(() => Observable.of(null)),
                // Old readme files contain chars instead of our domain command tags - replace them when loading the file
                map(fileObj => fileObj && fileObj.fileContent.replace(new RegExp(/✅/, 'g'), '[task]')),
            );
        }
    }

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
        if (this.tasksSubscription) {
            this.tasksSubscription.unsubscribe();
        }
    }
}
