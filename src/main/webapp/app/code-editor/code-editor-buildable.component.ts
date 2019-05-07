import * as $ from 'jquery';
import { Component, EventEmitter, Input, Output, OnChanges, ViewChild, SimpleChanges } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { LocalStorageService } from 'ngx-webstorage';
import { Subscription } from 'rxjs/Subscription';
import { difference as _difference } from 'lodash';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { catchError, map as rxMap, switchMap, tap } from 'rxjs/operators';

import { BuildLogEntryArray } from 'app/entities/build-log';

import { CourseService } from '../entities/course';
import { Participation, hasParticipationChanged } from '../entities/participation';
import { AnnotationArray, Session } from '../entities/ace-editor';
import { WindowRef } from '../core/websocket/window.service';

import Interactable from '@interactjs/core/Interactable';
import { CodeEditorAceComponent } from 'app/code-editor/ace/code-editor-ace.component';
import { EditorState } from 'app/entities/ace-editor/editor-state.model';
import { CommitState } from 'app/entities/ace-editor/commit-state.model';
import { Observable } from 'rxjs';
import { ResultService, Result } from 'app/entities/result';
import { Feedback } from 'app/entities/feedback';
import { TranslateService } from '@ngx-translate/core';
import { FileChange, RenameFileChange, CreateFileChange, DeleteFileChange, FileType } from 'app/entities/ace-editor/file-change.model';
import { CodeEditorComponent } from './code-editor.component';

@Component({
    selector: 'jhi-code-editor-buildable',
    templateUrl: './code-editor.component.html',
    providers: [JhiAlertService, WindowRef, CourseService],
})
export class CodeEditorBuildableComponent extends CodeEditorComponent {
    participationValue: Participation;
    @Output()
    participationChange = new EventEmitter<Participation>();
    @Input()
    get participation() {
        return this.participationValue;
    }
    set participation(participation: Participation) {
        this.participationValue = participation;
        this.participationChange.emit(participation);
    }

    errorFiles: string[] = [];
    session: Session;
    buildLogErrors: { errors: { [fileName: string]: AnnotationArray }; timestamp: number };
    isBuilding = false;

    constructor(jhiAlertService: JhiAlertService, private localStorageService: LocalStorageService) {
        super(jhiAlertService);
    }

    resetVariables = (): Observable<void> => {
        // Initialize variables;
        return super.resetVariables().pipe(
            tap(() => {
                this.errorFiles = [];
                this.buildLogErrors = undefined;
                this.session = undefined;
                this.isBuilding = false;
            }),
        );
    };

    afterInit = () => {
        this.loadSession();
    };

    isReady = () => {
        return !!this.participation;
    };

    onFileChange<T extends FileChange>([files, fileChange]: [string[], T]) {
        super.onFileChange([files, fileChange]);
        this.commitState = CommitState.UNCOMMITTED_CHANGES;
        this.repositoryFiles = files;
        if (fileChange instanceof RenameFileChange) {
            const oldFileNameRegex = new RegExp(`^${fileChange.oldFileName}`);
            const renamedErrorFiles = this.errorFiles.filter(file => file.startsWith(fileChange.oldFileName)).map(file => file.replace(oldFileNameRegex, fileChange.newFileName));
            this.errorFiles = [...this.errorFiles.filter(file => !file.startsWith(fileChange.oldFileName)), ...renamedErrorFiles];
            const renamedErrors = compose(
                fromPairs,
                map(([fileName, session]) => [fileName.replace(oldFileNameRegex, fileChange.newFileName), session]),
                toPairs,
            )(this.buildLogErrors.errors);
            const filteredErrors = compose(
                fromPairs,
                filter(([fileName]) => !fileName.startsWith(fileChange.oldFileName)),
                toPairs,
            )(this.buildLogErrors.errors);
            this.buildLogErrors = { errors: { ...filteredErrors, ...renamedErrors }, timestamp: this.buildLogErrors.timestamp };
            // If the renamed file has errors, we also need to update the session in localStorage
            if (this.errorFiles.includes(fileChange.newFileName)) {
                this.storeSession();
            }
        } else if (fileChange instanceof DeleteFileChange) {
            this.errorFiles = this.errorFiles.filter(fileName => !fileName.startsWith(fileChange.fileName));
            const errors = compose(
                fromPairs,
                filter(([fileName]) => !fileName.startsWith(fileChange.fileName)),
                toPairs,
            )(this.buildLogErrors.errors);
            this.buildLogErrors = { errors, timestamp: this.buildLogErrors.timestamp };
        }
    }

    onSavedFiles(files: any) {
        super.onSavedFiles(files);
        const { errorFiles } = Object.entries(files).reduce(
            (acc, [fileName, error]: [string, string | null]) => (error ? { ...acc, errorFiles: [fileName, ...acc.errorFiles] } : acc),
            { errorFiles: [] },
        );
        if (errorFiles.length) {
            this.onError('saveFailed');
        }
        this.storeSession();
    }

    /**
     * @function updateLatestResult
     * @desc Callback function for when a new result is received from the result component
     */
    updateLatestResult() {
        this.isBuilding = false;
    }

    /**
     * Check if the received build logs are recent and format them for use in the ace-editor
     * @param buildLogs
     */
    updateLatestBuildLogs(buildLogs: BuildLogEntryArray) {
        // The build logs come asynchronously while the view of other components are rendered.
        // To avoid ExpressionChangedAfterItHasBeenCheckedError, we wait a tick so the view can update.
        setTimeout(() => {
            const timestamp = buildLogs.length ? Date.parse(buildLogs[0].time) : 0;
            if (!this.buildLogErrors || timestamp > this.buildLogErrors.timestamp) {
                this.buildLogErrors = { errors: buildLogs.extractErrors(), timestamp };
                this.errorFiles = Object.keys(this.buildLogErrors.errors);
                // Only store the buildLogErrors if the session was already loaded - might be that they are outdated
                if (this.session) {
                    this.storeSession();
                }
            }
        }, 0);
    }

    /**
     * @function loadSession
     * @desc Gets the user's session data from localStorage to load editor settings
     */
    loadSession() {
        const sessions = JSON.parse(this.localStorageService.retrieve('sessions') || '{}');
        this.session = sessions[this.participation.id];
        if (this.session && (!this.buildLogErrors || this.session.timestamp > this.buildLogErrors.timestamp)) {
            this.buildLogErrors = {
                errors: compose(
                    fromPairs,
                    map(([fileName, errors]) => [fileName, new AnnotationArray(...errors)]),
                    filter(([, errors]) => errors.length),
                    toPairs,
                )(this.session.errors),
                timestamp: this.session.timestamp,
            };

            this.errorFiles = Object.keys(this.buildLogErrors.errors);
        } else if (this.buildLogErrors) {
            this.storeSession();
        }
    }

    /**
     * Store the build log error data in the localStorage of the browser (synchronous action).
     */
    storeSession() {
        this.localStorageService.store('sessions', JSON.stringify({ [this.participation.id]: this.buildLogErrors }));
    }

    commit = () => {
        return super.commit().pipe(tap(() => (this.isBuilding = true)));
    };
}
