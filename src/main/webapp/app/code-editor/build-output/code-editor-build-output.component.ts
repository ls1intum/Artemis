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
import { catchError, map } from 'rxjs/operators';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { CodeEditorComponent } from '../code-editor.component';

@Component({
    selector: 'jhi-code-editor-build-output',
    templateUrl: './code-editor-build-output.component.html',
    providers: [JhiAlertService, WindowRef, RepositoryService, ResultService],
})
export class CodeEditorBuildOutputComponent implements AfterViewInit, OnChanges, OnDestroy {
    buildLogs = new BuildLogEntryArray();

    /** Resizable constants **/
    resizableMinHeight = 100;
    resizableMaxHeight = 500;
    interactResizable: Interactable;

    @Input()
    participation: Participation;
    @Input()
    isBuilding: boolean;
    @Output()
    buildLogChange = new EventEmitter<BuildLogEntryArray>();

    private resultSubscription: Subscription;

    constructor(
        private parent: CodeEditorComponent,
        private $window: WindowRef,
        private repositoryService: RepositoryService,
        private resultService: ResultService,
        private resultWebsocketService: ResultWebsocketService,
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
            this.toggleBuildLogs(latestResult);
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
            this.resultSubscription = observable.subscribe(result => this.toggleBuildLogs(result));
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
        this.repositoryService.buildlogs(this.participation.id).subscribe(buildLogs => {
            this.buildLogs = new BuildLogEntryArray(...buildLogs);
            $('.buildoutput').scrollTop($('.buildoutput')[0].scrollHeight);
            this.buildLogChange.emit(this.buildLogs);
        });
    }

    /**
     * Decides if the build log should be shown.
     * If All tests were successful or there is test feedback -> don't show build logs.
     * Else -> show build logs.
     * @param result
     */
    toggleBuildLogs(result: Result) {
        if (
            !result ||
            ((result && result.successful && (!result.feedbacks || !result.feedbacks.length)) || (result && !result.successful && result.feedbacks && result.feedbacks.length))
        ) {
            this.buildLogs = new BuildLogEntryArray();
            // If there are no compile errors, send recent timestamp
            this.buildLogChange.emit(new BuildLogEntryArray({ time: new Date(Date.now()), log: '' }));
        } else {
            // If the build failed, find out why
            this.getBuildLogs();
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

    ngOnDestroy() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
    }
}
