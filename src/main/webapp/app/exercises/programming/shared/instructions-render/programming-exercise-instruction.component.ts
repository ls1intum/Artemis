import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewContainerRef } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { ShowdownExtension } from 'showdown';
import { catchError, filter, mergeMap, map, switchMap, tap } from 'rxjs/operators';
import { merge, Observable, of, Subscription } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ProgrammingExerciseTaskExtensionWrapper } from './extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/exercises/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExerciseInstructionService } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { TaskArray, TaskArrayWithExercise } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';
import { Participation } from 'app/entities/participation/participation.model';
import { Feedback } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { problemStatementHasChanged } from 'app/exercises/shared/exercise/exercise.utils';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { Result } from 'app/entities/result.model';
import { findLatestResult } from 'app/shared/util/utils';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { hasParticipationChanged } from 'app/exercises/shared/participation/participation.utils';

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
    // If there are no instructions available (neither in the exercise problemStatement nor the legacy README.md) emits an event
    @Output()
    public onNoInstructionsAvailable = new EventEmitter();

    public problemStatement: string;
    public participationSubscription: Subscription;

    public isInitial = true;
    public isLoading: boolean;
    public latestResultValue?: Result;

    get latestResult() {
        return this.latestResultValue;
    }

    set latestResult(result: Result | undefined) {
        this.latestResultValue = result;
        this.programmingExerciseTaskWrapper.setExercise(this.exercise);
        this.programmingExerciseTaskWrapper.setLatestResult(this.latestResult);
        this.programmingExercisePlantUmlWrapper.setLatestResult(this.latestResult);
    }

    public tasks: TaskArray;
    public renderedMarkdown: SafeHtml;
    private injectableContentForMarkdownCallbacks: Array<() => void> = [];

    markdownExtensions: ShowdownExtension[];
    private injectableContentFoundSubscription: Subscription;
    private tasksSubscription: Subscription;
    private generateHtmlSubscription: Subscription;

    // Icons
    faSpinner = faSpinner;

    constructor(
        public viewContainerRef: ViewContainerRef,
        private translateService: TranslateService,
        private resultService: ResultService,
        private repositoryFileService: RepositoryFileService,
        private participationWebsocketService: ParticipationWebsocketService,
        private markdownService: ArtemisMarkdownService,
        private programmingExerciseInstructionService: ProgrammingExerciseInstructionService,
        private programmingExerciseTaskWrapper: ProgrammingExerciseTaskExtensionWrapper,
        private programmingExercisePlantUmlWrapper: ProgrammingExercisePlantUmlExtensionWrapper,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
    ) {
        this.programmingExerciseTaskWrapper.viewContainerRef = this.viewContainerRef;
    }

    /**
     * If the participation changes, the participation's instructions need to be loaded and the
     * subscription for the participation's result needs to be set up.
     * @param changes
     */
    public ngOnChanges(changes: SimpleChanges) {
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
                        return this.loadInstructions().pipe(
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
        this.injectableContentFoundSubscription = merge(
            this.programmingExerciseTaskWrapper.subscribeForInjectableElementsFound(),
            this.programmingExercisePlantUmlWrapper.subscribeForInjectableElementsFound(),
        ).subscribe((injectableCallback) => {
            this.injectableContentForMarkdownCallbacks = [...this.injectableContentForMarkdownCallbacks, injectableCallback];
        });
        if (this.tasksSubscription) {
            this.tasksSubscription.unsubscribe();
        }
        this.tasksSubscription = this.programmingExerciseTaskWrapper.subscribeForFoundTestsInTasks().subscribe((tasks: TaskArrayWithExercise) => {
            // Multiple instances of the code editor use the TaskWrapperService. We have to check, that the returned tasks belong to this exercise
            if (tasks.exerciseId === this.exercise.id) {
                this.tasks = tasks.tasks;
            }
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
                this.programmingExerciseTaskWrapper.setLatestResult(this.latestResult);
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
        this.renderedMarkdown = this.markdownService.safeHtmlForMarkdown(this.problemStatement, this.markdownExtensions);
        // Wait a tick for the template to render before injecting the content.
        setTimeout(() => this.injectableContentForMarkdownCallbacks.forEach((callback) => callback()), 0);
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
        return this.resultService.getFeedbackDetailsForResult(currentParticipation.id!, result.id!).pipe(
            map((res) => res && res.body),
            map((feedbacks: Feedback[]) => {
                result.feedbacks = feedbacks;
                return result;
            }),
            catchError(() => of(result)),
        );
    }

    /**
     * Loads the instructions for the programming exercise.
     * We added the problemStatement later, historically the instructions where a file in the student's repository
     * This is why we now prefer the problemStatement and if it doesn't exist try to load the readme.
     */
    loadInstructions(): Observable<string | undefined> {
        if (this.exercise.problemStatement) {
            return of(this.exercise.problemStatement);
        } else {
            if (!this.participation.id) {
                return of(undefined);
            }
            return this.repositoryFileService.get(this.participation.id, 'README.md').pipe(
                catchError(() => of(undefined)),
                // Old readme files contain chars instead of our domain command tags - replace them when loading the file
                map((fileObj) => fileObj && fileObj.fileContent.replace(new RegExp(/✅/, 'g'), '[task]')),
            );
        }
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
        if (this.tasksSubscription) {
            this.tasksSubscription.unsubscribe();
        }
    }
}
