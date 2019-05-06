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
import { RepositoryFileService, RepositoryService } from '../entities/repository/repository.service';
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

@Component({
    selector: 'jhi-code-editor',
    templateUrl: './code-editor.component.html',
    providers: [JhiAlertService, WindowRef, CourseService, RepositoryFileService],
})
export abstract class CodeEditorComponent implements OnChanges {
    @ViewChild(CodeEditorAceComponent) editor: CodeEditorAceComponent;

    @Input()
    readonly editableInstructions = false;
    selectedFile: string;
    paramSub: Subscription;
    repositoryFiles: string[];
    unsavedFiles: string[] = [];
    fileChange: FileChange;

    /** Code Editor State Booleans **/
    isInitial = true;
    editorState = EditorState.CLEAN;
    commitState = CommitState.UNDEFINED;
    isLoadingFiles = true;

    abstract isReady: () => boolean;
    abstract checkIfRepositoryIsClean: () => Observable<CommitState>;
    abstract commit: () => Observable<void>;
    abstract afterInit: () => void = () => {};

    constructor(private jhiAlertService: JhiAlertService) {}

    init = (): Observable<void> => {
        // Reset all variables
        this.selectedFile = undefined;
        this.repositoryFiles = undefined;
        this.unsavedFiles = [];
        this.isInitial = false;
        return Observable.of() as Observable<void>;
    };

    /**
     * @function ngOnChanges
     * @desc Fetches the participation and the repository files for the provided participationId in params
     * If we are able to find the participation with the id specified in the route params in our data storage,
     * we use it in order to spare any additional REST calls
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (this.isInitial && this.isReady()) {
            this.init()
                .pipe(
                    switchMap(() => this.checkIfRepositoryIsClean()),
                    tap(commitState => (this.commitState = commitState)),
                    tap(() => this.afterInit()),
                )
                .subscribe(
                    () => {},
                    err => {
                        this.commitState = CommitState.COULD_NOT_BE_RETRIEVED;
                        this.onError(err);
                    },
                );
        }
    }

    /**
     * Set the editor state.
     * @param editorState
     */
    setEditorState(editorState: EditorState) {
        this.editorState = editorState;
    }

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: string) {
        this.jhiAlertService.error(`arTeMiSApp.editor.errors.${error}`);
    }

    /**
     * Set unsaved files and check if this changes the commit state.
     * @param unsavedFiles
     */
    setUnsavedFiles(unsavedFiles: string[]) {
        this.unsavedFiles = unsavedFiles;
        if (!this.unsavedFiles.length && this.editorState === EditorState.SAVING && this.commitState !== CommitState.WANTS_TO_COMMIT) {
            this.editorState = EditorState.CLEAN;
            this.commitState = CommitState.UNCOMMITTED_CHANGES;
        } else if (!this.unsavedFiles.length && this.editorState === EditorState.SAVING && this.commitState === CommitState.WANTS_TO_COMMIT) {
            this.editorState = EditorState.CLEAN;
            this.commit();
        } else if (!this.unsavedFiles.length) {
            this.editorState = EditorState.CLEAN;
        } else {
            this.editorState = EditorState.UNSAVED_CHANGES;
        }
    }

    /**
     * @function onFileChange
     * @desc A file has changed (create, rename, delete), so we have uncommitted changes.
     * Also all references to a file need to be updated in case of rename,
     * in case of delete make sure to also remove all sub entities (files in folder).
     */
    onFileChange<T extends FileChange>([files, fileChange]: [string[], T]) {
        this.commitState = CommitState.UNCOMMITTED_CHANGES;
        this.repositoryFiles = files;
        if (fileChange instanceof CreateFileChange) {
            // Select newly created file
            if (fileChange.fileType === FileType.FILE) {
                this.selectedFile = fileChange.fileName;
            }
        } else if (fileChange instanceof RenameFileChange) {
            const oldFileNameRegex = new RegExp(`^${fileChange.oldFileName}`);
            const renamedUnsavedFiles = this.unsavedFiles
                .filter(file => file.startsWith(fileChange.oldFileName))
                .map(file => file.replace(oldFileNameRegex, fileChange.newFileName));
            this.unsavedFiles = [...this.unsavedFiles.filter(file => !file.startsWith(fileChange.oldFileName)), ...renamedUnsavedFiles];
            // Also updated the name of the selectedFile
            if (this.selectedFile && fileChange.oldFileName === this.selectedFile) {
                this.selectedFile = fileChange.newFileName;
            } else if (this.selectedFile && this.selectedFile.startsWith(fileChange.oldFileName)) {
                this.selectedFile = this.selectedFile.replace(oldFileNameRegex, fileChange.newFileName);
            }
        } else if (fileChange instanceof DeleteFileChange) {
            this.unsavedFiles = this.unsavedFiles.filter(fileName => !fileName.startsWith(fileChange.fileName));
            // If the selected file or its containing folder was deleted, unselect it
            if (this.selectedFile && (this.selectedFile === fileChange.fileName || this.selectedFile.startsWith(fileChange.fileName))) {
                this.selectedFile = undefined;
            }
        }
        this.fileChange = fileChange;
        if (this.unsavedFiles.length && this.editorState === EditorState.CLEAN) {
            this.editorState = EditorState.UNSAVED_CHANGES;
        } else if (!this.unsavedFiles.length && this.editorState === EditorState.UNSAVED_CHANGES) {
            this.editorState = EditorState.CLEAN;
        }
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
     * When files were saved, check which could be saved and set unsavedFiles to update the ui.
     * Files that could not be saved will show an error in the header.
     * @param files
     */
    onSavedFiles(files: any) {
        const { savedFiles } = Object.entries(files).reduce(
            (acc, [fileName, error]: [string, string | null]) => (error ? acc : { ...acc, savedFiles: [fileName, ...acc.savedFiles] }),
            { savedFiles: [] },
        );

        const unsavedFiles = _difference(this.unsavedFiles, savedFiles);
        this.setUnsavedFiles(unsavedFiles);
    }

    /**
     * When the files are loaded set the repositoryFiles.
     * @param files
     */
    onFilesLoaded(files: string[]) {
        this.repositoryFiles = files;
    }

    /**
     * When the content of a file changes, set it as unsaved.
     * @param file
     */
    onFileContentChange({ file }: { file: string; unsavedChanges: boolean }) {
        const unsavedFiles = this.unsavedFiles.includes(file) ? this.unsavedFiles : [file, ...this.unsavedFiles];
        this.setUnsavedFiles(unsavedFiles);
    }

    /**
     * @function commit
     * @desc Commits the current repository files.
     * If there are unsaved changes, save them first before trying to commit again.
     * @param $event
     */
    prepareCommit() {
        // Avoid multiple commits at the same time.
        if (this.commitState === CommitState.COMMITTING) {
            return;
        }
        // If there are unsaved changes, save them before trying to commit again.
        if (!this.unsavedFiles.length) {
            this.commitState = CommitState.COMMITTING;
            this.commit().subscribe(
                () => {
                    this.commitState = CommitState.CLEAN;
                },
                err => {
                    console.log('Error during commit ocurred!', err);
                },
            );
        } else {
            this.commitState = CommitState.WANTS_TO_COMMIT;
            this.editor.saveChangedFiles();
        }
    }
    /**
     * The user will be warned if there are unsaved changes when trying to leave the code-editor.
     */
    hasUnsavedChanges() {
        return !this.unsavedFiles || !this.unsavedFiles.length;
    }
}
