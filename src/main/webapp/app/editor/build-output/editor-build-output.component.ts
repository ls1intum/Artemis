import {Participation} from '../../entities/participation';
import {JhiAlertService} from 'ng-jhipster';
import {
    AfterViewInit,
    Component,
    Input,
    OnChanges,
    SimpleChanges
} from '@angular/core';
import {WindowRef} from '../../shared/websocket/window.service';
import {RepositoryService} from '../../entities/repository/repository.service';
import {EditorComponent} from '../editor.component';
import {JhiWebsocketService} from '../../shared';
import {Result, ResultService, ParticipationResultService} from '../../entities/result';
import * as $ from 'jquery';
import * as interact from 'interactjs';

@Component({
    selector: 'jhi-editor-build-output',
    templateUrl: './editor-build-output.component.html',
    providers: [
        JhiAlertService,
        WindowRef,
        RepositoryService,
        ResultService,
        ParticipationResultService
    ]
})

export class EditorBuildOutputComponent implements AfterViewInit, OnChanges {

    buildLogs = [];

    /** Resizable sizing constants **/
    resizableMinHeight = 100;
    resizableMaxHeight = 500;

    @Input() participation: Participation;
    @Input() isBuilding: boolean;

    constructor(private parent: EditorComponent,
                private jhiWebsocketService: JhiWebsocketService,
                private repositoryService: RepositoryService,
                private resultService: ResultService,
                private participationResultService: ParticipationResultService) {}

    /**
     * @function ngAfterViewInit
     * @desc After the view was initialized, we create an interact.js resizable object,
     *       designate the edges which can be used to resize the target element and set min and max values.
     *       The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        interact('.resizable-buildoutput')
            .resizable({
                // Enable resize from top edge; triggered by class rg-top
                edges: { left: false, right: false, bottom: false, top: '.rg-top' },
                // Set min and max height
                restrictSize: {
                    min: { height: this.resizableMinHeight },
                    max: { height: this.resizableMaxHeight }
                },
                inertia: true,
            }).on('resizemove', function(event) {
                const target = event.target;
                // Update element size
                target.style.width  = event.rect.width + 'px';
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
     */
    ngOnChanges(changes: SimpleChanges): void {
        if ((changes.participation && this.participation) ||
            (changes.isBuilding && changes.isBuilding.currentValue === false && this.participation)) {
            if (!this.participation.results) {
                this.participationResultService.query(
                    this.participation.exercise.course.id,
                    this.participation.exercise.id,
                    this.participation.id,
                    {showAllResults: false, ratedOnly: this.participation.exercise.type === 'quiz'})
                    .subscribe( results => {
                       this.toggleBuildLogs(results.body);
                    });
            } else {
                this.toggleBuildLogs(this.participation.results);
            }
        }
    }

    /**
     * @function getBuildLogs
     * @desc Gets the buildlogs for the current participation
     */
    getBuildLogs() {
        this.repositoryService.buildlogs(this.participation.id).subscribe( buildLogs => {
            this.buildLogs = buildLogs;
            $('.buildoutput').scrollTop($('.buildoutput')[0].scrollHeight);
        });
    }

    toggleBuildLogs(results: Result[]) {
        if (results && results[0]) {
            this.resultService.details(results[0].id).subscribe( details => {
                if (details.body.length === 0) {
                    this.getBuildLogs();
                } else {
                    this.buildLogs = [];
                }
            });
        }
    }

    /**
     * @function toggleEditorCollapse
     * @desc Calls the parent (editorComponent) toggleCollapse method
     * @param $event
     * @param {boolean} horizontal
     */
    toggleEditorCollapse($event: any, horizontal: boolean) {
        this.parent.toggleCollapse($event, horizontal);
    }
}
