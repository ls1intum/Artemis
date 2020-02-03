import { hasParticipationChanged, Participation } from '../../entities/participation';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { JhiAlertService } from 'ng-jhipster';
import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { Result, ResultService } from '../../entities/result';
import { BuildLogEntry, BuildLogEntryArray } from 'app/entities/build-log';
import { Feedback } from 'app/entities/feedback';
import { Observable, of, Subscription } from 'rxjs';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { CodeEditorSessionService } from 'app/code-editor/service/code-editor-session.service';
import { AnnotationArray } from 'app/entities/ace-editor';
import { CodeEditorBuildLogService, CodeEditorSubmissionService } from 'app/code-editor/service';

export type BuildLogErrors = { errors: { [fileName: string]: AnnotationArray }; timestamp: number };

@Component({
    selector: 'jhi-code-editor-build-output',
    templateUrl: './code-editor-build-output.component.html',
    styleUrls: ['./code-editor-build-output.scss'],
    providers: [JhiAlertService, WindowRef],
})
export class CodeEditorBuildOutputComponent implements AfterViewInit, OnInit, OnChanges, OnDestroy {
    @Input()
    participation: Participation;
    @Input()
    get buildLogErrors() {
        return this.buildLogErrorsValue;
    }
    @Output()
    onToggleCollapse = new EventEmitter<{ event: any; horizontal: boolean; interactable: Interactable; resizableMinWidth?: number; resizableMinHeight: number }>();
    @Output()
    buildLogErrorsChange = new EventEmitter<BuildLogErrors>();
    @Output()
    onError = new EventEmitter<string>();

    isBuilding: boolean;
    rawBuildLogs = new BuildLogEntryArray();
    buildLogErrorsValue: BuildLogErrors;

    /** Resizable constants **/
    resizableMinHeight = 150;
    interactResizable: Interactable;

    set buildLogErrors(buildLogErrors: BuildLogErrors) {
        this.buildLogErrorsValue = buildLogErrors;
        this.buildLogErrorsChange.emit(buildLogErrors);
    }

    private resultSubscription: Subscription;
    private submissionSubscription: Subscription;

    constructor(
        private $window: WindowRef,
        private buildLogService: CodeEditorBuildLogService,
        private resultService: ResultService,
        private sessionService: CodeEditorSessionService,
        private participationWebsocketService: ParticipationWebsocketService,
        private submissionService: CodeEditorSubmissionService,
    ) {}

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
        this.resizableMinHeight = this.$window.nativeWindow.screen.height / 6;
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
        if (participationChange && this.participation.results && this.participation.results.length) {
            const latestResult = this.participation.results.reduce((acc, x) => (x.id > acc.id ? x : acc));
            of(latestResult)
                .pipe(
                    switchMap(result => (result && !result.feedbacks ? this.loadAndAttachResultDetails(result) : of(result))),
                    switchMap(result => this.fetchBuildResults(result)),
                    map(buildLogsFromServer => BuildLogEntryArray.fromBuildLogs(buildLogsFromServer!)),
                    tap((buildLogsFromServer: BuildLogEntryArray) => {
                        this.rawBuildLogs = buildLogsFromServer;
                        const buildLogErrors = this.rawBuildLogs.extractErrors();
                        // Only load errors from session if the last result has build errors
                        if (this.rawBuildLogs.length) {
                            const sessionBuildLogs = this.loadSession();
                            this.buildLogErrors = !sessionBuildLogs || buildLogErrors.timestamp > sessionBuildLogs.timestamp ? buildLogErrors : sessionBuildLogs;
                        } else {
                            this.buildLogErrors = buildLogErrors;
                        }
                    }),
                    catchError(() => {
                        this.rawBuildLogs = new BuildLogEntryArray();
                        this.buildLogErrors = this.rawBuildLogs.extractErrors();
                        return Observable.of();
                    }),
                )
                .subscribe();
        } else {
            if (!this.resultSubscription && this.participation) {
                this.setupResultWebsocket();
            }
        }
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
            .subscribeForLatestResultOfParticipation(this.participation.id)
            .pipe(
                // Ignore initial null result from service
                filter(result => !!result),
                switchMap(result => this.fetchBuildResults(result)),
                tap((buildLogsFromServer: BuildLogEntry[]) => {
                    this.rawBuildLogs = BuildLogEntryArray.fromBuildLogs(buildLogsFromServer);
                    this.buildLogErrors = this.rawBuildLogs.extractErrors();
                }),
                catchError(() => {
                    this.onError.emit('failedToLoadBuildLogs');
                    this.rawBuildLogs = new BuildLogEntryArray();
                    this.buildLogErrors = this.rawBuildLogs.extractErrors();
                    return Observable.of(null);
                }),
            )
            .subscribe(() => {}, console.log);
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
     * If All tests were successful or there is test feedback -> don't show build logs.
     * Else -> show build logs.
     * @param result
     */
    fetchBuildResults(result: Result | null): Observable<BuildLogEntry[] | null> {
        if ((result && result.successful) || (result && !result.successful && result.feedbacks && result.feedbacks.length)) {
            return of([]);
        } else {
            // If the build failed, find out why
            return this.getBuildLogs();
        }
    }

    /**
     * @function toggleEditorCollapse
     * @desc Calls the parent (editorComponent) toggleCollapse method
     * @param $event
     * @param {boolean} horizontal
     */
    toggleEditorCollapse($event: any) {
        this.onToggleCollapse.emit({
            event: $event,
            horizontal: false,
            interactable: this.interactResizable,
            resizableMinWidth: undefined,
            resizableMinHeight: this.resizableMinHeight,
        });
    }

    /**
     * @function loadSession
     * @desc Gets the user's session data from localStorage to load editor settings
     */
    loadSession() {
        return this.sessionService.loadSession();
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
