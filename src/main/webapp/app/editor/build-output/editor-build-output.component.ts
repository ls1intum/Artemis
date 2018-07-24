import {Participation} from '../../entities/participation';
import {JhiAlertService} from 'ng-jhipster';
import {
    AfterViewInit,
    Component,
    Input,
    OnChanges, OnDestroy,
    OnInit,
    SimpleChanges
} from '@angular/core';
import {WindowRef} from '../../shared/websocket/window.service';
import {RepositoryService} from '../../entities/repository/repository.service';
import {EditorComponent} from '../editor.component';
import {JhiWebsocketService} from '../../shared';
import {Result, ResultService, ParticipationResultService} from '../../entities/result';
import * as $ from 'jquery';

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

export class EditorBuildOutputComponent implements OnInit, AfterViewInit, OnDestroy, OnChanges {

    buildLogs = [];

    @Input() participation: Participation;
    @Input() bIsBuilding: boolean;

    //TODO: component => require: { editor: '^editor' }

    constructor(private parent: EditorComponent,
                private jhiWebsocketService: JhiWebsocketService,
                private repositoryService: RepositoryService,
                private resultService: ResultService,
                private participationResultService: ParticipationResultService) {
    }

    /**
     * @function ngOnInit
     * @desc Framework function which is executed when the component is instantiated.
     * Used to assign parameters which are used by the component
     */
    ngOnInit(): void {}

    /**
     * @function ngAfterViewInit
     * @desc Framework lifecycle hook that is called after Angular has fully initialized a component's view;
     * used to handle any additional initialization tasks
     */
    ngAfterViewInit(): void {}

    /**
     * @function ngOnChanges
     * @desc Framework lifecycle hook that is called when any data-bound property of a directive changes
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if ((changes.participation && this.participation) ||
            (changes.bIsBuilding && changes.bIsBuilding.currentValue === false && this.participation)) {
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

    getBuildLogs() {
        this.repositoryService.buildlogs(this.participation.id).subscribe( buildLogs => {
            // TODO: check if buildLogs.log exists
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
     * @descCalls the parent (editorComponent) toggleCollapse method
     * @param event
     * @param {boolean} horizontal
     */
    toggleEditorCollapse(event: any, horizontal: boolean) {
        this.parent.toggleCollapse(event, horizontal);
    }

    /**
     * @function ngOnDestroy
     * @desc Framework function which is executed when the component is destroyed.
     * Used for component cleanup, close open sockets, connections, subscriptions...
     */
    ngOnDestroy(): void {}

}
