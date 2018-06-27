import { CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Participation, ParticipationService } from '../entities/participation';
import { RepositoryService, RepositoryFileService } from '../entities/repository/repository.service';
import { HttpResponse } from '@angular/common/http';
import { NG1WEBSOCKET_SERVICE } from '../shared/websocket/ng1-websocket.service';

@Component({
    selector: 'jhi-editor',
    templateUrl: './editor.component.html',
    providers:  [
        JhiAlertService,
        CourseService,
        RepositoryFileService
    ]
})

/**
 * @class EditorComponent
 * @desc This component acts as a wrapper for the upgraded editor component (directive).
 * The dependencies are passed along to the directive, from there to the legacy component.
 */
export class EditorComponent implements OnInit, OnDestroy {

    /** Dependencies as defined by the upgraded Editor component */
    participation: Participation;
    repository: RepositoryService;
    file: any;
    paramSub: Subscription;
    repositoryFiles: string[];

    /**
     * @constructor EditorComponent
     * @param {ActivatedRoute} route
     * @param {ParticipationService} participationService
     * @param {RepositoryService} repositoryService
     * @param {RepositoryFileService} repositoryFileService
     * @param ng1JhiWebsocketService
     */
    constructor(private route: ActivatedRoute,
                private participationService: ParticipationService,
                private repositoryService: RepositoryService,
                private repositoryFileService: RepositoryFileService,
                @Inject(NG1WEBSOCKET_SERVICE) private ng1JhiWebsocketService: any) {}

    /**
     * @function ngOnInit
     * @desc Framework function which is executed when the component is instantiated.
     * Used to assign parameters which are used by the component
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            /** Query the participationService for the participationId given by the params */
            this.participationService.find(params['participationId']).subscribe((response: HttpResponse<Participation>) => {
                this.participation = response.body;
            });
            /** Assign file from params given by the URL */
            this.file = params['file'];
            /** Query the repositoryFileService for files in the repository */
            this.repositoryFileService.query(params['participationId']).subscribe(files => {
                this.repositoryFiles = files;
            }, err => {
                console.log('There was an error while getting files: ' + err.body.msg);
            });
        });

        /** Assign repository */
        this.repository = this.repositoryService;
    }

    /**
     * @function ngOnDestroy
     * @desc Framework function which is executed when the component is destroyed.
     * Used for component cleanup, close open sockets, connections, subscriptions...
     */
    ngOnDestroy() {
        /** Unsubscribe onDestroy to avoid performance issues due to a high number of open subscriptions */
        this.paramSub.unsubscribe();
    }
}
