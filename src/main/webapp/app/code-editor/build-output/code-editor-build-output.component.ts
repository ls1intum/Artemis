import { hasParticipationChanged, Participation, ParticipationWebsocketService } from '../../entities/participation';
import { JhiAlertService } from 'ng-jhipster';
import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { RepositoryService } from 'app/entities/repository/repository.service';
import { Result, ResultService } from '../../entities/result';
import { BuildLogEntry, BuildLogEntryArray } from 'app/entities/build-log';
import { Feedback } from 'app/entities/feedback';
import { of, Observable, Subscription } from 'rxjs';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { CodeEditorSessionService } from 'app/code-editor/service/code-editor-session.service';
import { AnnotationArray } from 'app/entities/ace-editor';
import { CodeEditorBuildLogService } from 'app/code-editor/service/code-editor-repository.service';

export type BuildLogErrors = { errors: { [fileName: string]: AnnotationArray }; timestamp: number };

@Component({
    selector: 'jhi-code-editor-build-output',
    templateUrl: './code-editor-build-output.component.html',
    providers: [JhiAlertService, WindowRef],
})
export class CodeEditorBuildOutputComponent implements AfterViewInit, OnChanges, OnDestroy {
    @Input()
    participation: Participation;
    @Input()
    get isBuilding() {
        return this.isBuildingValue;
    }
    @Input()
    get buildLogErrors() {
        return this.buildLogErrorsValue;
    }
    @Input()
    toggleCollapse: (event: any, horizontal: boolean, interactable: Interactable, resizableMinWidth: number, resizableMinHeight: number) => void;
    @Output()
    buildLogErrorsChange = new EventEmitter<BuildLogErrors>();
    @Output()
    isBuildingChange = new EventEmitter<boolean>();
    @Output()
    onError = new EventEmitter<string>();

    rawBuildLogs = new BuildLogEntryArray();
    buildLogErrorsValue: BuildLogErrors;
    isBuildingValue: boolean;

    /** Resizable constants **/
    resizableMinHeight = 100;
    resizableMaxHeight = 500;
    interactResizable: Interactable;

    set buildLogErrors(buildLogErrors: BuildLogErrors) {
        this.buildLogErrorsValue = buildLogErrors;
        this.buildLogErrorsChange.emit(buildLogErrors);
    }

    set isBuilding(isBuilding: boolean) {
        this.isBuildingValue = isBuilding;
        this.isBuildingChange.emit(isBuilding);
    }

    private resultSubscriptions: { [id: number]: Subscription } = {};

    constructor(
        private $window: WindowRef,
        private buildLogService: CodeEditorBuildLogService,
        private resultService: ResultService,
        private sessionService: CodeEditorSessionService,
        private participationWebsocketService: ParticipationWebsocketService,
    ) {}

    /**
     * @function ngAfterViewInit
     * @desc After the view was initialized, we create an interact.js resizable object,
     *       designate the edges which can be used to resize the target element and set min and max values.
     *       The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        this.resizableMinHeight = this.$window.nativeWindow.screen.height / 7;
        this.interactResizable = interact('.resizable-buildoutput')
            .resizable({
                // Enable resize from top edge; triggered by class rg-top
                edges: { left: false, right: false, bottom: false, top: '.rg-top' },
                // Set min and max height
                restrictSize: {
                    min: { height: this.resizableMinHeight },
                    max: { height: this.resizableMaxHeight },
                },
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function(event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function(event: any) {
                const target = event.target;
                // Update element height
                target.style.height = event.rect.height + 'px';
            });
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
        // If the participation changes and it has results, fetch the result details to decide if the build log should be shown
        if (participationChange && this.participation.results && this.participation.results.length) {
            if (!this.resultSubscriptions[this.participation.id]) {
                this.setupResultWebsocket();
            }
            const latestResult = this.participation.results.reduce((acc, x) => (x.id > acc.id ? x : acc));
            of(latestResult)
                .pipe(
                    switchMap(result => (result ? this.loadAndAttachResultDetails(result) : of(result))),
                    switchMap(result => this.fetchBuildResults(result)),
                    map(buildLogsFromServer => new BuildLogEntryArray(...buildLogsFromServer)),
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
            if (!this.resultSubscriptions[this.participation.id]) {
                this.setupResultWebsocket();
            }
        }
    }

    /**
     * Set up the websocket for retrieving build results.
     * Online updates the build logs if the result is new, otherwise doesn't react.
     */
    private setupResultWebsocket() {
        if (!this.resultSubscriptions[this.participation.id]) {
            this.participationWebsocketService.addParticipation(this.participation);
            this.resultSubscriptions[this.participation.id] = this.participationWebsocketService
                .subscribeForLatestResultOfParticipation(this.participation.id)
                .pipe(
                    // Ignore initial null result from service
                    filter(result => !!result),
                    switchMap(result => this.fetchBuildResults(result)),
                    tap((buildLogsFromServer: BuildLogEntry[]) => {
                        this.isBuilding = false;
                        this.rawBuildLogs = new BuildLogEntryArray(...buildLogsFromServer);
                        this.buildLogErrors = this.rawBuildLogs.extractErrors();
                    }),
                    catchError(() => {
                        this.onError.emit('failedToLoadBuildLogs');
                        this.isBuilding = false;
                        this.rawBuildLogs = new BuildLogEntryArray();
                        this.buildLogErrors = this.rawBuildLogs.extractErrors();
                        return Observable.of(null);
                    }),
                )
                .subscribe(() => {}, console.log);
        }
    }

    /**
     * @function loadResultDetails
     * @desc Fetches details for the result (if we received one) and attach them to the result.
     * Mutates the input parameter result.
     */
    loadAndAttachResultDetails(result: Result): Observable<Result> {
        return this.resultService.getFeedbackDetailsForResult(result.id).pipe(
            catchError(() => of(null)),
            map(res => res && res.body),
            map((feedbacks: Feedback[]) => {
                result.feedbacks = feedbacks;
                return result;
            }),
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
    fetchBuildResults(result: Result): Observable<BuildLogEntry[] | null> {
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
    toggleEditorCollapse($event: any, horizontal: boolean) {
        this.toggleCollapse($event, horizontal, this.interactResizable, undefined, this.resizableMinHeight);
    }

    /**
     * @function loadSession
     * @desc Gets the user's session data from localStorage to load editor settings
     */
    loadSession() {
        return this.sessionService.loadSession();
    }

    ngOnDestroy() {
        Object.values(this.resultSubscriptions).forEach(subscription => subscription.unsubscribe());
    }
}
