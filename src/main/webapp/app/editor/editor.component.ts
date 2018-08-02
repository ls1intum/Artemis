import { CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import {Component, OnChanges, OnDestroy, OnInit, SimpleChanges} from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Participation, ParticipationService } from '../entities/participation';
import { RepositoryService, RepositoryFileService } from '../entities/repository/repository.service';
import {Result} from '../entities/result';
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
export class EditorComponent implements OnInit, OnChanges, OnDestroy {

    /** Dependencies as defined by the Editor component */
    participation: Participation;
    repository: RepositoryService;
    file: any;
    commonFilePathPrefix: string;
    paramSub: Subscription;
    repositoryFiles: string[];
    latestResult: Result;
    saveStatusLabel: string;

    /** File Status Booleans **/
    isSaved = true;
    isBuilding = false;
    isCommitted: boolean;

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
    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe(params => {
            /** Query the participationService for the participationId given by the params */
            this.participationService.find(params['participationId']).subscribe((response: HttpResponse<Participation>) => {
                this.participation = response.body;
                this.checkIfRepositoryIsClean();
            });
            /** Query the repositoryFileService for files in the repository */
            this.repositoryFileService.query(params['participationId']).subscribe(files => {
                this.repositoryFiles = files;
                this.commonFilePathPrefix = this.identifyCommonFilePathPrefix();
            }, err => {
                console.log('There was an error while getting files: ' + err.body.msg);
            });
        });

        /** Assign repository */
        this.repository = this.repositoryService;
    }

    ngOnChanges(changes: SimpleChanges) {
        this.checkIfRepositoryIsClean();
    }

    checkIfRepositoryIsClean(): void {
        this.repository.isClean(this.participation.id).subscribe(res => {
            this.isCommitted = res.isClean;
        });
    }

    updateSaveStatusLabel(event) {
        console.log('updateSaveStatusLabel called');
        this.isSaved = event.isSaved;
        if (!this.isSaved) {
            this.isCommitted = false;
        }
        this.saveStatusLabel = event.saveStatusLabel;
    }

    updateLatestResult($event) {
        console.log('updateLatestResult called; received new result');
        this.isBuilding = false;
        this.latestResult = $event.newResult;
    }

    updateSelectedFile(fileObject) {
        console.log('RECEIVED EVENT WITH NEW FILENAME: ' + fileObject.fileName);
        console.log(this.repositoryFiles);
        this.file = fileObject.fileName;
    }

    updateRepositoryCommitStatus(event) {
        console.log('updateRepositoryCommitStatus called');
        console.log(event);
        this.isSaved = false;
        /** Query the repositoryFileService for updated files in the repository */
        this.repositoryFileService.query(this.participation.id).subscribe(files => {
            this.repositoryFiles = files;
        }, err => {
            console.log('There was an error while getting files: ' + err.body.msg);
        });
    }

    /** Collapse parts of the editor (file browser, build output...) */
    toggleCollapse(event: any, horizontal: boolean) {

        const target = event.toElement || event.relatedTarget || event.target;

        target.blur();

        const $card = $(target).closest('.card');

        if ($card.hasClass('collapsed')) {
            $card.removeClass('collapsed');
        } else {
            $card.addClass('collapsed');
            horizontal ? $card.height('35px') : $card.width('55px');
        }
    }

    /**
     * Looks for the users identifier within the repository path to identify the common file path
     * which we need to append to the filename when doing a GET-request to the server
     * @returns {string}: the prefix path until the actual repository appended by a backslash
     */
    identifyCommonFilePathPrefix(): string {
        const studentLoginIdx = this.repositoryFiles[0]
            .split('\\')
            .indexOf(this.participation.student.login);
        return this.repositoryFiles[0].split('\\').slice(0, studentLoginIdx).join('\\') + '\\';
    }

    commit(event) {

        const target = event.toElement || event.relatedTarget || event.target;

        target.blur();
        this.isBuilding = true;
        this.repository.commit(this.participation.id).subscribe(
            res => {
                this.isCommitted = true;
                console.log('Successfully committed');
            },
            err => {
                console.log('Error occured');
            });
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
