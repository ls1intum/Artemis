import { Participation } from '../../entities/participation';
import { JhiAlertService } from 'ng-jhipster';
import { AfterViewInit, EventEmitter, Component, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { maxBy as _maxBy } from 'lodash';
import { WindowRef } from '../../core/websocket/window.service';
import { RepositoryService } from '../../entities/repository/repository.service';
import { CodeEditorComponent } from '../code-editor.component';
import { JhiWebsocketService, AccountService } from '../../core';
import { Result, ResultService, ResultWebsocketService } from '../../entities/result';
import * as moment from 'moment';
import * as $ from 'jquery';
import * as interact from 'interactjs';
import { Interactable } from 'interactjs';
import { BuildLogEntryArray } from '../../entities/build-log';

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
    @Input()
    isInitial = true;

    private unsubscribeResults: () => void;

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
     *          OR
     *       - Repository status was 'building' and is now done
     * @param {SimpleChanges} changes
     *
     * TODO: avoid this call and rather use the websocket mechanism to retrieve the latest result
     * TODO: in any case make sure to ask the server for the latest rated result if the call cannot be avoided
     *
     */
    ngOnChanges(changes: SimpleChanges): void {
        // If the participation changes, set component to initial as everything needs to be reloaded now
        if (this.participation && changes.participation && changes.participation.currentValue && this.participation.id !== changes.participation.currentValue.id) {
            this.isInitial = true;
        }
        if ((changes.participation && this.participation) || (changes.isBuilding && changes.isBuilding.currentValue === false && this.participation)) {
            if (this.isInitial) {
                this.setupResultWebsocket();
                this.isInitial = false;
            }
            if (!this.participation.results) {
                this.resultService
                    .findResultsForParticipation(this.participation.exercise.course.id, this.participation.exercise.id, this.participation.id, {
                        showAllResults: false,
                        ratedOnly: this.participation.exercise.type === 'quiz',
                    })
                    .subscribe(results => {
                        this.toggleBuildLogs(_maxBy(results.body, 'id'));
                    });
            } else if (this.participation.results && this.participation.results.length) {
                const latestResultPrev =
                    changes.participation && changes.participation.previousValue ? _maxBy((changes.participation.previousValue as Participation).results, 'id') : undefined;
                const latestResultNew = _maxBy(this.participation.results, 'id');
                if ((!latestResultPrev && latestResultNew) || (latestResultPrev && latestResultNew && latestResultNew.id !== latestResultPrev.id)) {
                    this.toggleBuildLogs(latestResultNew);
                }
            }
        }
    }

    async setupResultWebsocket() {
        if (this.unsubscribeResults) {
            this.unsubscribeResults();
        }
        return this.resultWebsocketService
            .subscribeResultForParticipation(this.participation.id, this.toggleBuildLogs.bind(this))
            .then(unsubscribe => (this.unsubscribeResults = unsubscribe));
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
        // TODO: can we use the result in code-editor-instructions.component.ts?
        this.resultService.getFeedbackDetailsForResult(result.id).subscribe(details => {
            if (details.body.length === 0) {
                this.getBuildLogs();
            } else {
                this.buildLogs = new BuildLogEntryArray();
                // If there are no compile errors, send recent timestamp
                this.buildLogChange.emit(new BuildLogEntryArray({ time: new Date(Date.now()), log: '' }));
            }
        });
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
        if (this.unsubscribeResults) {
            this.unsubscribeResults();
        }
    }
}
