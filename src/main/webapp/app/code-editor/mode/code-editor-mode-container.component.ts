import { HostListener } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { difference as _difference, isEmpty as _isEmpty } from 'lodash';
import { ActivatedRoute } from '@angular/router';

import { ComponentCanDeactivate } from 'app/shared';
import { ParticipationService } from 'app/entities/participation';
import { FileChange, RenameFileChange, CreateFileChange, DeleteFileChange, FileType } from 'app/entities/ace-editor/file-change.model';
import { AnnotationArray, Session, EditorState, CommitState } from 'app/entities/ace-editor';
import { JhiAlertService } from 'ng-jhipster';
import { CodeEditorRepositoryFileService } from '../code-editor-repository.service';
import { CodeEditorSessionService } from '../code-editor-session.service';

export abstract class CodeEditorContainer implements ComponentCanDeactivate {
    selectedFile: string;
    unsavedFiles: { [fileName: string]: string } = {};
    fileChange: FileChange;

    session: Session;
    buildLogErrors: { errors: { [fileName: string]: AnnotationArray }; timestamp: number };
    isBuilding = false;

    /** Code Editor State Booleans **/
    editorState = EditorState.CLEAN;
    commitState = CommitState.UNDEFINED;

    constructor(
        protected participationService: ParticipationService,
        private translateService: TranslateService,
        protected route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        protected repositoryFileService: CodeEditorRepositoryFileService,
        protected sessionService: CodeEditorSessionService,
    ) {}

    initializeProperties = () => {
        this.selectedFile = undefined;
        this.unsavedFiles = {};
        this.session = undefined;
        this.buildLogErrors = undefined;
        this.isBuilding = false;
        this.editorState = EditorState.CLEAN;
        this.commitState = CommitState.UNDEFINED;
    };

    onRepositoryChecked = (commitState: CommitState) => {
        this.commitState = commitState;
    };

    /**
     * Set the editor state.
     * @param editorState
     */
    setEditorState(editorState: EditorState) {
        this.editorState = editorState;
    }

    /**
     * @function onFileChange
     * @desc A file has changed (create, rename, delete), so we have uncommitted changes.
     * Also all references to a file need to be updated in case of rename,
     * in case of delete make sure to also remove all sub entities (files in folder).
     */
    // TODO: Refactor, maybe move into service
    onFileChange<F extends FileChange>([files, fileChange]: [string[], F]) {
        this.commitState = CommitState.UNCOMMITTED_CHANGES;
        if (fileChange instanceof CreateFileChange) {
            // Select newly created file
            if (fileChange.fileType === FileType.FILE) {
                this.selectedFile = fileChange.fileName;
            }
        } else if (fileChange instanceof RenameFileChange) {
            const oldFileNameRegex = new RegExp(`^${fileChange.oldFileName}`);
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
            // Also updated the name of the selectedFile
            if (this.selectedFile && fileChange.oldFileName === this.selectedFile) {
                this.selectedFile = fileChange.newFileName;
            } else if (this.selectedFile && this.selectedFile.startsWith(fileChange.oldFileName)) {
                this.selectedFile = this.selectedFile.replace(oldFileNameRegex, fileChange.newFileName);
            }
        } else if (fileChange instanceof DeleteFileChange) {
            this.unsavedFiles = compose(
                fromPairs,
                filter(([fileName]) => !fileName.startsWitch(fileChange.fileName)),
                toPairs,
            )(this.unsavedFiles);
            // If the selected file or its containing folder was deleted, unselect it
            if (this.selectedFile && (this.selectedFile === fileChange.fileName || this.selectedFile.startsWith(fileChange.fileName))) {
                this.selectedFile = undefined;
            }
        }
        if (fileChange instanceof RenameFileChange) {
            const oldFileNameRegex = new RegExp(`^${fileChange.oldFileName}`);
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
        } else if (fileChange instanceof DeleteFileChange) {
            const errors = compose(
                fromPairs,
                filter(([fileName]) => !fileName.startsWith(fileChange.fileName)),
                toPairs,
            )(this.buildLogErrors.errors);
            this.buildLogErrors = { errors, timestamp: this.buildLogErrors.timestamp };
        }
        this.fileChange = fileChange;
        if (!_isEmpty(this.unsavedFiles) && this.editorState === EditorState.CLEAN) {
            this.editorState = EditorState.UNSAVED_CHANGES;
        } else if (_isEmpty(this.unsavedFiles) && this.editorState === EditorState.UNSAVED_CHANGES) {
            this.editorState = EditorState.CLEAN;
        }
    }

    /**
     * Set unsaved files and check if this changes the commit state.
     * @param unsavedFiles
     */
    setUnsavedFiles(unsavedFiles: { [fileName: string]: string }) {
        this.unsavedFiles = unsavedFiles;
        if (_isEmpty(this.unsavedFiles) && this.editorState === EditorState.SAVING && this.commitState !== CommitState.WANTS_TO_COMMIT) {
            this.editorState = EditorState.CLEAN;
            this.commitState = CommitState.UNCOMMITTED_CHANGES;
        } else if (_isEmpty(this.unsavedFiles)) {
            this.editorState = EditorState.CLEAN;
        } else {
            this.editorState = EditorState.UNSAVED_CHANGES;
        }
    }

    /**
     * When files were saved, check which could be saved and set unsavedFiles to update the ui.
     * Files that could not be saved will show an error in the header.
     * @param files
     */
    // TODO: Signatur wrong?
    onSavedFiles(files: { [fileName: string]: string | null }) {
        const savedFiles = Object.entries(files)
            .filter(([, error]: [string, string | null]) => !error)
            .map(([fileName]) => fileName);
        const errorFiles = Object.entries(files)
            .filter(([, error]: [string, string | null]) => error)
            .map(([fileName]) => fileName);

        // TODO: Refactor
        const unsavedFiles = fromPairs(toPairs(this.unsavedFiles).filter(([fileName]) => !savedFiles.includes(fileName)));

        this.setUnsavedFiles(unsavedFiles);

        if (errorFiles.length) {
            this.onError('saveFailed');
        }
        this.storeSession();
    }

    /**
     * When the content of a file changes, set it as unsaved.
     * @param file
     */
    onFileContentChange({ file, fileContent }: { file: string; fileContent: string }) {
        const unsavedFiles = { ...this.unsavedFiles, [file]: fileContent };
        this.setUnsavedFiles(unsavedFiles);
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
