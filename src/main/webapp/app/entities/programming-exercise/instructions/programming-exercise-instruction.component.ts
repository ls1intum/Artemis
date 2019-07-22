import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { ShowdownExtension } from 'showdown';
import { catchError, filter, flatMap, map, switchMap, tap } from 'rxjs/operators';
import { Feedback } from 'app/entities/feedback';
import { Result, ResultService } from 'app/entities/result';
import { ProgrammingExercise } from '../programming-exercise.model';
import { RepositoryFileService } from 'app/entities/repository';
import { hasParticipationChanged, Participation, ParticipationWebsocketService } from 'app/entities/participation';
import { Observable, Subscription } from 'rxjs';
import { problemStatementHasChanged } from 'app/entities/exercise';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ProgrammingExerciseTaskExtensionFactory, TestsForTasks } from './extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionFactory } from 'app/entities/programming-exercise/instructions/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction.service';

type Step = {
    title: string;
    done: TestCaseState;
    tests: string[];
};

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
    @Output()
    public resultChange = new EventEmitter<Result>();

    public problemStatement: string;
    public participationSubscription: Subscription;

    public isInitial = true;
    public isLoading: boolean;
    public latestResult: Result | null;
    public steps: Array<Step> = [];
    public renderedMarkdown: SafeHtml;

    private markdownExtensions: ShowdownExtension[];
    generateHtmlSubscription: Subscription;

    constructor(
        private translateService: TranslateService,
        private resultService: ResultService,
        private repositoryFileService: RepositoryFileService,
        private participationWebsocketService: ParticipationWebsocketService,
        private markdownService: ArtemisMarkdown,
        private programmingExerciseInstructionService: ProgrammingExerciseInstructionService,
        private programmingExerciseTaskFactory: ProgrammingExerciseTaskExtensionFactory,
        private programmingExercisePlantUmlFactory: ProgrammingExercisePlantUmlExtensionFactory,
    ) {
        this.markdownExtensions = [this.programmingExerciseTaskFactory.getExtension(), this.programmingExercisePlantUmlFactory.getExtension()];
    }

    /**
     * If the participation changes, the participation's instructions need to be loaded and the
     * subscription for the participation's result needs to be set up.
     * @param changes
     */
    public ngOnChanges(changes: SimpleChanges) {
        const participationHasChanged = hasParticipationChanged(changes);
        // It is possible that the exercise does not have an id in case it is being created now.
        if (participationHasChanged) {
            this.isInitial = true;
            if (this.generateHtmlSubscription) {
                this.generateHtmlSubscription.unsubscribe();
            }
            if (this.generateHtmlEvents) {
                this.generateHtmlEvents.subscribe(() => {
                    this.renderedMarkdown = this.markdownService.htmlForMarkdown(this.problemStatement, this.markdownExtensions);
                });
            }
            this.setupResultWebsocket();
            this.programmingExerciseTaskFactory.subscribeForTestForTasks().subscribe((testsForTasks: TestsForTasks) => {
                this.steps = testsForTasks.map(([, taskName, tests]) => ({
                    done: this.programmingExerciseInstructionService.testStatusForTask(tests, this.latestResult).testCaseState,
                    title: taskName,
                    tests,
                }));
            });
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
                        this.programmingExerciseTaskFactory.setLatestResult(this.latestResult);
                        this.programmingExercisePlantUmlFactory.setLatestResult(this.latestResult);
                    }),
                    tap(() => {
                        this.renderedMarkdown = this.markdownService.htmlForMarkdown(this.problemStatement, this.markdownExtensions);
                        this.isInitial = false;
                        this.isLoading = false;
                    }),
                )
                .subscribe();
        } else if (this.exercise && problemStatementHasChanged(changes)) {
            this.problemStatement = this.exercise.problemStatement!;
        }
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
                this.programmingExerciseTaskFactory.setLatestResult(this.latestResult);
                this.programmingExercisePlantUmlFactory.setLatestResult(this.latestResult);
                this.renderedMarkdown = this.markdownService.htmlForMarkdown(this.problemStatement, this.markdownExtensions);
            });
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
        return this.resultService.findResultsForParticipation(this.exercise.course!.id, this.exercise.id, this.participation.id).pipe(
            catchError(() => Observable.of(null)),
            map((latestResult: HttpResponse<Result[]>) => {
                if (latestResult && latestResult.body && latestResult.body.length) {
                    return latestResult.body.reduce((acc: Result, v: Result) => (v.id > acc.id ? v : acc));
                } else {
                    return null;
                }
            }),
            flatMap((latestResult: Result) => (latestResult ? this.loadAndAttachResultDetails(latestResult) : Observable.of(null))),
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
        this.steps = [];
        if (this.participationSubscription) {
            this.participationSubscription.unsubscribe();
        }
    }
}
