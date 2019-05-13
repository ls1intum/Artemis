import { HostListener } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { isEmpty as _isEmpty } from 'lodash';
import { ActivatedRoute } from '@angular/router';

import { ComponentCanDeactivate } from 'app/shared';
import { ParticipationService } from 'app/entities/participation';
import { FileChange, RenameFileChange, CreateFileChange, DeleteFileChange, FileType } from 'app/entities/ace-editor/file-change.model';
import { AnnotationArray } from 'app/entities/ace-editor';
import { JhiAlertService } from 'ng-jhipster';
import { CodeEditorSessionService } from 'app/code-editor/service';
import { EditorState, CommitState } from 'app/code-editor/model';

export abstract class CodeEditorContainer implements ComponentCanDeactivate {
    // WARNING: Don't initialize variables in the declaration block. The method initializeProperties is responsible for this task.
    selectedFile: string;
    unsavedFilesValue: { [fileName: string]: string }; // {[fileName]: fileContent}
    // This variable is used to inform components that a file has changed its filename, e.g. because of renaming
    fileChange: FileChange;
    buildLogErrors: { errors: { [fileName: string]: AnnotationArray }; timestamp: number };

    /** Code Editor State Variables **/
    editorState: EditorState;
    commitState: CommitState;
    isBuilding: boolean;

    constructor(
        protected participationService: ParticipationService,
        private translateService: TranslateService,
        protected route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        protected sessionService: CodeEditorSessionService,
    ) {
        this.initializeProperties();
    }

    get unsavedFiles() {
        return this.unsavedFilesValue;
    }

    /**
     * Setting unsaved files also updates the editorState / commitState.
     * - unsaved files empty -> EditorState.CLEAN
     * - unsaved files NOT empty -> EditorState.UNSAVED_CHANGES
     * - unsaved files empty AND editorState.SAVING -> CommitState.UNCOMMITTED_CHANGES
     * @param unsavedFiles
     */
    set unsavedFiles(unsavedFiles: { [fileName: string]: string }) {
        this.unsavedFilesValue = unsavedFiles;
        if (_isEmpty(this.unsavedFiles) && this.editorState === EditorState.SAVING) {
            this.editorState = EditorState.CLEAN;
            this.commitState = CommitState.UNCOMMITTED_CHANGES;
        } else if (_isEmpty(this.unsavedFiles)) {
            this.editorState = EditorState.CLEAN;
        } else {
            this.editorState = EditorState.UNSAVED_CHANGES;
        }
    }

    /**
     * Resets all variables of this class.
     * When a new variable is added, it needs to be added to this method!
     * Initializing in variable declaration is not allowed.
     */
    initializeProperties = () => {
        this.selectedFile = undefined;
        this.unsavedFiles = {};
        this.buildLogErrors = { errors: {}, timestamp: 0 };
        this.isBuilding = false;
        this.editorState = EditorState.CLEAN;
        this.commitState = CommitState.UNDEFINED;
    };

    /**
     * @function onFileChange
     * @desc A file has changed (create, rename, delete), so we have uncommitted changes.
     * Also all references to a file need to be updated in case of rename,
     * in case of delete make sure to also remove all sub entities (files in folder).
     */
    onFileChange<F extends FileChange>([, fileChange]: [string[], F]) {
        this.commitState = CommitState.UNCOMMITTED_CHANGES;
        if (fileChange instanceof CreateFileChange) {
            // Select newly created file
            if (fileChange.fileType === FileType.FILE) {
                this.selectedFile = fileChange.fileName;
            }
        } else if (fileChange instanceof RenameFileChange) {
            const oldFileNameRegex = new RegExp(`^${fileChange.oldFileName}`);
            // Update unsavedFiles
            const renamedUnsavedFiles = compose(
                fromPairs,
                map(([fileName, fileContent]) => [fileName.replace(oldFileNameRegex, fileChange.newFileName), fileContent]),
                filter(([fileName]) => fileName.startsWitch(fileChange.oldFileName)),
                toPairs,
            )(this.unsavedFiles);
            const unaffectedUnsavedFiles = compose(
                fromPairs,
                filter(([fileName]) => !fileName.startsWitch(fileChange.oldFileName)),
                toPairs,
            );
            this.unsavedFiles = { ...renamedUnsavedFiles, ...unaffectedUnsavedFiles };
            // Update buildLogErrors
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
            // Also updated the name of the selectedFile
            if (this.selectedFile && fileChange.oldFileName === this.selectedFile) {
                this.selectedFile = fileChange.newFileName;
            } else if (this.selectedFile && this.selectedFile.startsWith(fileChange.oldFileName)) {
                this.selectedFile = this.selectedFile.replace(oldFileNameRegex, fileChange.newFileName);
            }
        } else if (fileChange instanceof DeleteFileChange) {
            // Update unsavedFiles
            this.unsavedFiles = compose(
                fromPairs,
                filter(([fileName]) => !fileName.startsWitch(fileChange.fileName)),
                toPairs,
            )(this.unsavedFiles);
            // Update buildLogErrors
            const errors = compose(
                fromPairs,
                filter(([fileName]) => !fileName.startsWith(fileChange.fileName)),
                toPairs,
            )(this.buildLogErrors.errors);
            this.buildLogErrors = { errors, timestamp: this.buildLogErrors.timestamp };
            // If unsavedFiles are deleted, this can mean that the editorState becomes clean
            if (_isEmpty(this.unsavedFiles) && this.editorState === EditorState.UNSAVED_CHANGES) {
                this.editorState = EditorState.CLEAN;
            }
            // If the selected file or its containing folder was deleted, unselect it
            if (this.selectedFile && (this.selectedFile === fileChange.fileName || this.selectedFile.startsWith(fileChange.fileName))) {
                this.selectedFile = undefined;
            }
        }
        // Set the fileChange to inform other Components so they can update their references to the files
        this.fileChange = fileChange;
    }

    /**
     * When files were saved, check which could be saved and set unsavedFiles to update the ui.
     * Files that could not be saved will show an error in the header.
     * @param files
     */
    onSavedFiles(files: { [fileName: string]: string | null }) {
        const savedFiles = Object.entries(files)
            .filter(([, error]: [string, string | null]) => !error)
            .map(([fileName]) => fileName);
        const errorFiles = Object.entries(files)
            .filter(([, error]: [string, string | null]) => error)
            .map(([fileName]) => fileName);

        this.unsavedFiles = fromPairs(toPairs(this.unsavedFiles).filter(([fileName]) => !savedFiles.includes(fileName)));

        if (errorFiles.length) {
            this.onError('saveFailed');
        }
        this.storeSession();
    }

    /**
     * When the content of a file changes, set it as unsaved.
     */
    onFileContentChange({ file, fileContent }: { file: string; fileContent: string }) {
        this.unsavedFiles = { ...this.unsavedFiles, [file]: fileContent };
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
     * Store the build log error data in the localStorage of the browser (synchronous action).
     */
    storeSession() {
        this.sessionService.storeSession(this.buildLogErrors);
    }

    /**
     * The user will be warned if there are unsaved changes when trying to leave the code-editor.
     */
    canDeactivate() {
        return _isEmpty(this.unsavedFiles);
    }

    // displays the alert for confirming refreshing or closing the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any) {
        if (!this.canDeactivate()) {
            $event.returnValue = this.translateService.instant('pendingChanges');
        }
    }
}
