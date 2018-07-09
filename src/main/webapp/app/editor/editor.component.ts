import { CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import {Component, OnDestroy, OnInit} from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Participation, ParticipationService } from '../entities/participation';
import { RepositoryService, RepositoryFileService } from '../entities/repository/repository.service';
import { HttpResponse } from '@angular/common/http';
import * as $ from 'jquery';

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
    saveStatusLabel: string;

    /** File Status Booleans **/
    bIsSaved: boolean = true;
    bIsBuilding: boolean = false;
    bIsCommitted: boolean;

    /**
     * @constructor EditorComponent
     * @param {ActivatedRoute} route
     * @param {ParticipationService} participationService
     * @param {RepositoryService} repositoryService
     * @param {RepositoryFileService} repositoryFileService
     */
    constructor(private route: ActivatedRoute,
                private participationService: ParticipationService,
                private repositoryService: RepositoryService,
                private repositoryFileService: RepositoryFileService) {}

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
                console.log(this.participation);
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

    updateSaveStatusLabel(event) {
        this.bIsSaved = event.bIsSaved;
        if (!this.bIsSaved) {
            this.bIsCommitted = false;
        }
        this.saveStatusLabel = event.saveStatusLabel;
    }

    /** Collapse parts of the editor (file browser, build output...) */
    toggleCollapse = function(event: any, horizontal) {

        const target = event.toElement || event.relatedTarget || event.target;

        target.blur();

        const $card = $(target).closest('.card');

        if ($card.hasClass('collapsed')) {
            $card.removeClass('collapsed');
        } else {
            $card.addClass('collapsed');
            if (horizontal) {
                $card.height('35px');
            } else {
                $card.width('55px');
            }
        }
    };

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
