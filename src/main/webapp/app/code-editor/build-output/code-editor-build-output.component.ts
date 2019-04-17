import { hasParticipationChanged, Participation } from '../../entities/participation';
import { JhiAlertService } from 'ng-jhipster';
import { AfterViewInit, EventEmitter, Component, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { maxBy as _maxBy } from 'lodash';
import { WindowRef } from '../../core/websocket/window.service';
import { RepositoryService } from '../../entities/repository/repository.service';
import { CodeEditorComponent } from '../code-editor.component';
import { Result, ResultService, ResultWebsocketService } from '../../entities/result';
import * as $ from 'jquery';
import * as interact from 'interactjs';
import { Interactable } from 'interactjs';
import { BuildLogEntryArray } from '../../entities/build-log';
import { Feedback } from 'app/entities/feedback';
import { Observable, Subscription } from 'rxjs';
import { distinctUntilChanged, map } from 'rxjs/operators';

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
            const latestResultPrev = changes.participation.previousValue ? _maxBy((changes.participation.previousValue as Participation).results, 'id') : null;
            const latestResultNew = _maxBy(this.participation.results, 'id');
            // Check if the result is new, if not the component does not need to be updated
            if ((!latestResultPrev && latestResultNew) || (latestResultPrev && latestResultNew && latestResultNew.id !== latestResultPrev.id)) {
                this.loadAndAttachResultDetails(latestResultNew).subscribe(result => this.toggleBuildLogs(result));
            }
        }
    }

    async setupResultWebsocket() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        return this.resultWebsocketService.subscribeResultForParticipation(this.participation.id).then(observable => {
            this.resultSubscription = observable
                .pipe(distinctUntilChanged(({ id: id1 }: Result, { id: id2 }: Result) => id1 === id2))
                .subscribe(result => this.toggleBuildLogs(result));
        });
    }

    /**
     * @function loadResultDetails
     * @desc Fetches details for the result (if we received one) => Input latestResult
     */
    loadAndAttachResultDetails(result: Result): Observable<Result> {
        return this.resultService.getFeedbackDetailsForResult(result.id).pipe(
            map(({ body }: { body: Feedback[] }) => body),
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

    toggleBuildLogs(result: Result) {
        if ((result.successful && (!result.feedbacks || !result.feedbacks.length)) || (!result.successful && result.feedbacks && result.feedbacks.length)) {
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
