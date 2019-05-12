import { hasParticipationChanged, Participation } from '../../entities/participation';
import { JhiAlertService } from 'ng-jhipster';
import { AfterViewInit, EventEmitter, Component, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { WindowRef } from '../../core/websocket/window.service';
import { RepositoryService } from '../../entities/repository/repository.service';
import { Result, ResultService, ResultWebsocketService } from '../../entities/result';
import * as $ from 'jquery';
import { BuildLogEntryArray } from '../../entities/build-log';
import { Feedback } from 'app/entities/feedback';
import { Observable, Subscription } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { CodeEditorComponent } from '../code-editor.component';
import { CodeEditorSessionService } from '../code-editor-session.service';
import { AnnotationArray } from 'app/entities/ace-editor';
import { CodeEditorBuildLogService } from '../code-editor-repository.service';

export type BuildLogErrors = { errors: { [fileName: string]: AnnotationArray }; timestamp: number };

@Component({
    selector: 'jhi-code-editor-build-output',
    templateUrl: './code-editor-build-output.component.html',
    providers: [JhiAlertService, WindowRef, RepositoryService, ResultService, CodeEditorSessionService],
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
    @Output()
    buildLogErrorsChange = new EventEmitter<BuildLogErrors>();
    @Output()
    isBuildingChange = new EventEmitter<boolean>();

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

    private resultSubscription: Subscription;

    constructor(
        private parent: CodeEditorComponent,
        private $window: WindowRef,
        private buildLogService: CodeEditorBuildLogService,
        private resultService: ResultService,
        private resultWebsocketService: ResultWebsocketService,
        private sessionService: CodeEditorSessionService,
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
     *       - Participation changed
     * @param {SimpleChanges} changes
     *
     */
    ngOnChanges(changes: SimpleChanges): void {
        const participationChange = hasParticipationChanged(changes);
        // If the participation changes, set component to initial as everything needs to be reloaded now
        if (participationChange) {
            this.setupResultWebsocket();
        }
        // If the participation changes and it has results, fetch the result details to decide if the build log should be shown
        if (participationChange && this.participation.results) {
            const latestResult = this.participation.results.length ? this.participation.results.reduce((acc, x) => (x.id > acc.id ? x : acc)) : null;
            Observable.of(latestResult)
                .pipe(
                    switchMap(result => (result ? this.loadAndAttachResultDetails(result) : Observable.of(result))),
                    switchMap(result => this.fetchBuildResults(result)),
                    tap(buildLogsFromServer => {
                        const sessionBuildLogs = this.loadSession();
                        this.buildLogErrors = !sessionBuildLogs || buildLogsFromServer.timestamp > sessionBuildLogs.timestamp ? buildLogsFromServer : sessionBuildLogs;
                    }),
                )
                .subscribe();
        }
    }

    /**
     * Set up the websocket for retrieving build results.
     * Online updates the build logs if the result is new, otherwise doesn't react.
     */
    private setupResultWebsocket() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        this.resultWebsocketService.subscribeResultForParticipation(this.participation.id).then(observable => {
            this.resultSubscription = observable
                .pipe(
                    tap(() => (this.isBuilding = false)),
                    switchMap(result => this.fetchBuildResults(result)),
                    tap(buildLogErrors => (this.buildLogErrors = buildLogErrors)),
                )
                .subscribe();
        });
    }

    /**
     * @function loadResultDetails
     * @desc Fetches details for the result (if we received one) and attach them to the result.
     * Mutates the input parameter result.
     */
    loadAndAttachResultDetails(result: Result): Observable<Result> {
        return this.resultService.getFeedbackDetailsForResult(result.id).pipe(
            catchError(() => Observable.of(null)),
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
        return this.buildLogService.getBuildLogs().pipe(
            map((buildLogs: BuildLogEntryArray) => {
                this.rawBuildLogs = new BuildLogEntryArray(...buildLogs);
                return this.rawBuildLogs.extractErrors();
            }),
        );
    }

    /**
     * Decides if the build log should be shown.
     * If All tests were successful or there is test feedback -> don't show build logs.
     * Else -> show build logs.
     * @param result
     */
    fetchBuildResults(result: Result) {
        if (
            !result ||
            ((result && result.successful && (!result.feedbacks || !result.feedbacks.length)) || (result && !result.successful && result.feedbacks && result.feedbacks.length))
        ) {
            this.rawBuildLogs = new BuildLogEntryArray();
            const buildLogErrors = this.rawBuildLogs.extractErrors();
            return Observable.of(buildLogErrors);
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
        this.parent.toggleCollapse($event, horizontal, this.interactResizable, undefined, this.resizableMinHeight);
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
    }
}
