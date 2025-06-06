import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, inject } from '@angular/core';
import { Observable, Subscription, of } from 'rxjs';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import { BuildLogEntry, BuildLogEntryArray } from 'app/buildagent/shared/entities/build-log.model';
import { Participation, getExercise } from 'app/exercise/shared/entities/participation/participation.model';
import { CodeEditorSubmissionService } from 'app/programming/shared/code-editor/services/code-editor-submission.service';
import { CodeEditorBuildLogService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Interactable } from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { findLatestResult } from 'app/shared/util/utils';
import { StaticCodeAnalysisIssue } from 'app/programming/shared/entities/static-code-analysis-issue.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { faChevronDown, faCircleNotch, faTerminal } from '@fortawesome/free-solid-svg-icons';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ResultService } from 'app/exercise/result/result.service';
import { hasParticipationChanged } from 'app/exercise/participation/participation.utils';
import { Annotation } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

@Component({
    selector: 'jhi-code-editor-build-output',
    templateUrl: './code-editor-build-output.component.html',
    styleUrls: ['./code-editor-build-output.scss'],
    imports: [FaIconComponent, TranslateDirective, ArtemisDatePipe],
})
export class CodeEditorBuildOutputComponent implements AfterViewInit, OnInit, OnChanges, OnDestroy {
    private buildLogService = inject(CodeEditorBuildLogService);
    private resultService = inject(ResultService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private submissionService = inject(CodeEditorSubmissionService);

    @Input()
    participation: Participation;

    @Output()
    onAnnotations = new EventEmitter<Array<Annotation>>();
    @Output()
    onToggleCollapse = new EventEmitter<{ event: any; horizontal: boolean; interactable: Interactable; resizableMinWidth?: number; resizableMinHeight: number }>();
    @Output()
    onError = new EventEmitter<string>();

    isBuilding: boolean;
    rawBuildLogs = new BuildLogEntryArray();
    result?: Result;

    /** Resizable constants **/
    resizableMinHeight = 150;
    interactResizable: Interactable;

    private resultSubscription: Subscription;
    private submissionSubscription: Subscription;

    // Icons
    faChevronDown = faChevronDown;
    faCircleNotch = faCircleNotch;
    faTerminal = faTerminal;

    ngOnInit(): void {
        this.setupSubmissionWebsocket();
    }

    /**
     * @function ngAfterViewInit
     * @desc After the view was initialized, we create an interact.js resizable object,
     *       designate the edges which can be used to resize the target element and set min and max values.
     *       The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        this.resizableMinHeight = window.screen.height / 6;
        this.interactResizable = interact('.resizable-buildoutput');
    }

    /**
     * @function ngOnChanges
     * @desc We need to update the participation results under certain conditions:
     *       - Participation changed => reset websocket connection and load initial result
     * @param {SimpleChanges} changes
     *
     */
    ngOnChanges(changes: SimpleChanges): void {
        const participationChange = hasParticipationChanged(changes);
        if (participationChange) {
            this.setupResultWebsocket();
        }
        // If the participation changes and it has results, fetch the result details to decide if the build log should be shown
        if (participationChange && getAllResultsOfAllSubmissions(this.participation?.submissions).length) {
            const latestResult = findLatestResult(getAllResultsOfAllSubmissions(this.participation.submissions));
            of(latestResult)
                .pipe(
                    switchMap((result) => (result && !result.feedbacks ? this.loadAndAttachResultDetails(this.participation, result) : of(result))),
                    tap((result) => (this.result = result)),
                    switchMap((result) => this.fetchBuildResults(result)),
                    map((buildLogsFromServer) => BuildLogEntryArray.fromBuildLogs(buildLogsFromServer!)),
                    tap((buildLogsFromServer: BuildLogEntryArray) => {
                        this.rawBuildLogs = buildLogsFromServer;
                    }),
                    catchError(() => {
                        this.rawBuildLogs = new BuildLogEntryArray();
                        return of();
                    }),
                )
                .subscribe(() => this.extractAnnotations());
        } else {
            if (!this.resultSubscription && this.participation) {
                this.setupResultWebsocket();
            }
        }
    }

    /**
     * Extracts annotations from
     * - the build logs as compilation errors
     * - the result feedbacks as static code analysis issues
     * and emits them to the parent component
     */
    private extractAnnotations() {
        const exercise: ProgrammingExercise | undefined = getExercise(this.participation!);
        const buildLogErrors = this.rawBuildLogs.extractErrors(exercise?.programmingLanguage, exercise?.projectType);
        const codeAnalysisIssues = (this.result!.feedbacks || [])
            .filter(Feedback.isStaticCodeAnalysisFeedback)
            .map<StaticCodeAnalysisIssue>((feedback) => JSON.parse(feedback.detailText!));
        const codeAnalysisAnnotations = codeAnalysisIssues.map<Annotation>((issue) => ({
            text: issue.message || '',
            fileName: issue.filePath || '',
            // TODO: Support endLine and endColumn
            row: (issue.startLine || 1) - 1,
            column: (issue.startColumn || 1) - 1,
            type: 'warning', // TODO encode type in feedback
            timestamp: this.result?.completionDate ? new Date(this.result.completionDate.toString()).valueOf() : 0,
        }));
        this.onAnnotations.emit([...buildLogErrors, ...codeAnalysisAnnotations]);
    }

    /**
     * Subscribe to incoming submissions, translating to the state isBuilding = true (a pending submission without result exists) vs = false (no pending submission).
     */
    private setupSubmissionWebsocket() {
        this.submissionSubscription = this.submissionService
            .getBuildingState()
            .pipe(tap((isBuilding: boolean) => (this.isBuilding = isBuilding)))
            .subscribe();
    }

    /**
     * Set up the websocket for retrieving build results.
     * Online updates the build logs if the result is new, otherwise doesn't react.
     */
    private setupResultWebsocket() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        this.resultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation.id!, true)
            .pipe(
                // Ignore initial null/undefined result from service
                filter((result) => !!result),
                tap((result) => (this.result = result!)),
                switchMap((result) => this.fetchBuildResults(result)),
                tap((buildLogsFromServer: BuildLogEntry[]) => {
                    this.rawBuildLogs = BuildLogEntryArray.fromBuildLogs(buildLogsFromServer);
                }),
                catchError(() => {
                    this.onError.emit('failedToLoadBuildLogs');
                    this.rawBuildLogs = new BuildLogEntryArray();
                    return of(undefined);
                }),
            )
            .subscribe(() => this.extractAnnotations());
    }

    /**
     * @function loadAndAttachResultDetails
     * @desc Fetches details for the result (if we received one) and attach them to the result.
     * Mutates the input parameter result.
     */
    loadAndAttachResultDetails(participation: Participation, result: Result): Observable<Result> {
        return this.resultService.getFeedbackDetailsForResult(participation.id!, result).pipe(
            map((res) => res?.body),
            map((feedbacks: Feedback[]) => {
                result.feedbacks = feedbacks;
                return result;
            }),
            catchError(() => of(result)),
        );
    }

    /**
     * @function getBuildLogs
     * @desc Gets the buildlogs for the current participation
     */
    getBuildLogs() {
        return this.buildLogService.getBuildLogs();
    }

    /**
     * Decides if the build log should be shown.
     * Fetch the build logs if a result is available and no submission is available or the submission could not be build
     * @param result
     */
    fetchBuildResults(result?: Result): Observable<BuildLogEntry[] | undefined> {
        if (result && result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA && (!result.submission || (result.submission as ProgrammingSubmission).buildFailed)) {
            return this.getBuildLogs();
        } else {
            return of([]);
        }
    }

    /**
     * @function toggleEditorCollapse
     * @desc Calls the parent (editorComponent) toggleCollapse method
     * @param event
     */
    toggleEditorCollapse(event: any) {
        this.onToggleCollapse.emit({
            event,
            horizontal: false,
            interactable: this.interactResizable,
            resizableMinWidth: undefined,
            resizableMinHeight: this.resizableMinHeight,
        });
    }

    ngOnDestroy() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
    }
}
