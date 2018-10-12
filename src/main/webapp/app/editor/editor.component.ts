import { CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { Component, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { WindowRef } from '../core/websocket/window.service';
import { Participation, ParticipationService } from '../entities/participation';
import { ParticipationDataProvider } from '../courses/exercises/participation-data-provider';
import { RepositoryFileService, RepositoryService } from '../entities/repository/repository.service';
import { Result } from '../entities/result';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import * as $ from 'jquery';
import { Interactable } from 'interactjs';

@Component({
    selector: 'jhi-editor',
    templateUrl: './editor.component.html',
    providers: [JhiAlertService, WindowRef, CourseService, RepositoryFileService]
})
export class EditorComponent implements OnInit, OnChanges, OnDestroy {
    /** Dependencies as defined by the Editor component */
    participation: Participation;
    repository: RepositoryService;
    file: string;
    paramSub: Subscription;
    repositoryFiles: string[];
    latestResult: Result;
    saveStatusLabel: string;

    /** Enable initial refresh call for result component **/
    doInitialRefresh = true;

    /** File Status Booleans **/
    isSaved = true;
    isBuilding = false;
    isCommitted: boolean;

    /**
     * @constructor EditorComponent
     * @param {ActivatedRoute} route
     * @param {WindowRef} $window
     * @param {ParticipationService} participationService
     * @param {ParticipationDataProvider} participationDataProvider
     * @param {RepositoryService} repositoryService
     * @param {RepositoryFileService} repositoryFileService
     */
    constructor(
        private route: ActivatedRoute,
        private $window: WindowRef,
        private participationService: ParticipationService,
        private participationDataProvider: ParticipationDataProvider,
        private repositoryService: RepositoryService,
        private repositoryFileService: RepositoryFileService
    ) {}

    /**
     * @function ngOnInit
     * @desc Fetches the participation and the repository files for the provided participationId in params
     * If we are able to find the participation with the id specified in the route params in our data storage,
     * we use it in order to spare any additional REST calls
     */
    ngOnInit(): void {
        /** Assign repository */
        this.repository = this.repositoryService;

        this.paramSub = this.route.params.subscribe(params => {
            // Cast params id to Number or strict comparison will lead to result false (due to differing types)
            if (
                this.participationDataProvider.participationStorage &&
                this.participationDataProvider.participationStorage.id === Number(params['participationId'])
            ) {
                // We found a matching participation in the data provider, so we can avoid doing a REST call
                this.participation = this.participationDataProvider.participationStorage;
                this.checkIfRepositoryIsClean();
            } else {
                /** Query the participationService for the participationId given by the params */
                this.participationService.find(params['participationId']).subscribe((response: HttpResponse<Participation>) => {
                    this.participation = response.body;
                    this.checkIfRepositoryIsClean();
                });
            }
            /** Query the repositoryFileService for files in the repository */
            this.repositoryFileService.query(params['participationId']).subscribe(
                files => {
                    this.repositoryFiles = files;
                },
                (error: HttpErrorResponse) => {
                    console.log('There was an error while getting files: ' + error.message + ': ' + error.error);
                }
            );
        });

        /** Assign repository */
        this.repository = this.repositoryService;
    }

    /**
     * @function ngOnChanges
     * @desc Checks if the repository has uncommitted changes
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges) {
        this.checkIfRepositoryIsClean();
    }

    /**
     * @function checkIfRepositoryIsClean
     * @desc Calls the repository service to see if the repository has uncommitted changes
     */
    checkIfRepositoryIsClean(): void {
        this.repository.isClean(this.participation.id).subscribe(res => {
            this.isCommitted = res.isClean;
        });
    }

    /**
     * @function updateSaveStatusLabel
     * @desc Callback function for a save status changes of files
     * @param $event Event object which contains information regarding the save status of files
     */
    updateSaveStatusLabel($event: any) {
        this.isSaved = $event.isSaved;
        if (!this.isSaved) {
            this.isCommitted = false;
        }
        this.saveStatusLabel = $event.saveStatusLabel;
    }

    /**
     * @function updateLatestResult
     * @desc Callback function for when a new result is received from the result component
     * @param $event Event object which contains the newly received result
     */
    updateLatestResult($event: any) {
        this.isBuilding = false;
        this.latestResult = $event.newResult;
    }

    /**
     * @function updateSelectedFile
     * @desc Callback function for when a new file is selected within the file-browser component
     * @param $event Event object which contains the new file name
     */
    updateSelectedFile($event: any) {
        this.file = $event.fileName;
    }

    /**
     * @function updateRepositoryCommitStatus
     * @desc Callback function for when a file was created or deleted; updates the current repository files
     */
    updateRepositoryCommitStatus($event: any) {
        this.isSaved = false;
        this.isCommitted = false;
        /** Query the repositoryFileService for updated files in the repository */
        this.repositoryFileService.query(this.participation.id).subscribe(
            files => {
                this.repositoryFiles = files;
                // Select newly created file
                if ($event.mode === 'create') {
                    this.file = $event.file;
                }
            },
            (error: HttpErrorResponse) => {
                console.log('There was an error while getting files: ' + error.message + ': ' + error.error);
            }
        );
    }

    /**
     * @function toggleCollapse
     * @desc Collapse parts of the editor (file browser, build output...)
     * @param $event {object} Click event object; contains target information
     * @param horizontal {boolean} Used to decide which height to use for the collapsed element
     * @param interactResizable {Interactable} The interactjs element, used to en-/disable resizing
     * @param minWidth {number} Width to set the element to after toggling the collapse
     * @param minHeight {number} Height to set the element to after toggling the collapse
     */
    toggleCollapse($event: any, horizontal: boolean, interactResizable: Interactable, minWidth?: number, minHeight?: number) {
        const target = $event.toElement || $event.relatedTarget || $event.target;
        target.blur();
        const $card = $(target).closest('.card');

        if ($card.hasClass('collapsed')) {
            $card.removeClass('collapsed');
            interactResizable.resizable({ enabled: true });

            // Reset min width if argument was provided
            if (minWidth) {
                $card.width(minWidth + 'px');
            }
            // Reset min height if argument was provided
            if (minHeight) {
                $card.height(minHeight + 'px');
            }
        } else {
            $card.addClass('collapsed');
            horizontal ? $card.height('35px') : $card.width('55px');
            interactResizable.resizable({ enabled: false });
        }
    }

    /**
     * @function commit
     * @desc Commits the current repository files
     * @param $event
     */
    commit($event: any) {
        const target = $event.toElement || $event.relatedTarget || $event.target;
        target.blur();
        this.isBuilding = true;
        this.repository.commit(this.participation.id).subscribe(
            res => {
                this.isCommitted = true;
            },
            err => {
                console.log('Error during commit ocurred!', err);
            }
        );
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
