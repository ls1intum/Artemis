import { HostListener, OnDestroy, ViewChild } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { catchError, map as rxMap, switchMap, tap } from 'rxjs/operators';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { difference as _difference } from 'lodash';
import { ActivatedRoute } from '@angular/router';

import { ComponentCanDeactivate } from 'src/main/webapp/app/shared';
import { ParticipationService } from 'src/main/webapp/app/entities/participation';
import { FileChange, RenameFileChange, CreateFileChange, DeleteFileChange, FileType } from 'app/entities/ace-editor/file-change.model';
import { AnnotationArray, Session, EditorState, CommitState } from 'app/entities/ace-editor';
import { BuildLogEntryArray } from 'app/entities/build-log';
import { CodeEditorAceComponent } from 'app/code-editor/ace/code-editor-ace.component';
import { JhiAlertService } from 'ng-jhipster';

export abstract class CodeEditorContainer implements OnDestroy, ComponentCanDeactivate {
    @ViewChild(CodeEditorAceComponent) editor: CodeEditorAceComponent;
    paramSub: Subscription;

    selectedFile: string;
    repositoryFiles: string[];
    unsavedFiles: string[] = [];
    fileChange: FileChange;

    errorFiles: string[] = [];
    session: Session;
    buildLogErrors: { errors: { [fileName: string]: AnnotationArray }; timestamp: number };
    isBuilding = false;

    /** Code Editor State Booleans **/
    editorState = EditorState.CLEAN;
    commitState = CommitState.UNDEFINED;
    isLoadingFiles = true;

    constructor(
        protected participationService: ParticipationService,
        private translateService: TranslateService,
        protected route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
    ) {}

    onDomainChange = () => {
        this.selectedFile = undefined;
        this.repositoryFiles = undefined;
        this.unsavedFiles = [];
        this.errorFiles = [];
        this.session = undefined;
        this.buildLogErrors = undefined;
        this.isBuilding = false;
        this.editorState = EditorState.CLEAN;
        this.commitState = CommitState.UNDEFINED;
        this.isLoadingFiles = true;
    };

    onRepsitoryChecked = (commitState: CommitState) => {
        this.commitState = commitState;
        this.loadSession();
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
    onFileChange<F extends FileChange>([files, fileChange]: [string[], F]) {
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
        this.fileChange = fileChange;
        if (this.unsavedFiles.length && this.editorState === EditorState.CLEAN) {
            this.editorState = EditorState.UNSAVED_CHANGES;
        } else if (!this.unsavedFiles.length && this.editorState === EditorState.UNSAVED_CHANGES) {
            this.editorState = EditorState.CLEAN;
        }
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
            this.prepareCommit();
        } else if (!this.unsavedFiles.length) {
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
    onSavedFiles(files: any) {
        const { savedFiles } = Object.entries(files).reduce(
            (acc, [fileName, error]: [string, string | null]) => (error ? acc : { ...acc, savedFiles: [fileName, ...acc.savedFiles] }),
            { savedFiles: [] },
        );

        const unsavedFiles = _difference(this.unsavedFiles, savedFiles);
        this.setUnsavedFiles(unsavedFiles);

        const { errorFiles } = Object.entries(files).reduce(
            (acc, [fileName, error]: [string, string | null]) => (error ? { ...acc, errorFiles: [fileName, ...acc.errorFiles] } : acc),
            { errorFiles: [] },
        );
        if (errorFiles.length) {
            this.onError('saveFailed');
        }
        // TODO: Migrate to 2 way databinding with build-output component
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
    updateLatestBuildLogs(buildLogs) {
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
            // TODO: Move into actions component
            this.repositoryService
                .commit()
                .pipe(tap(() => (this.isBuilding = true)))
                .subscribe(
                    () => {
                        this.commitState = CommitState.CLEAN;
                    },
                    (err: any) => {
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

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: string) {
        this.jhiAlertService.error(`arTeMiSApp.editor.errors.${error}`);
    }

    /**
     * The user will be warned if there are unsaved changes when trying to leave the code-editor.
     */
    canDeactivate() {
        return this.hasUnsavedChanges();
    }

    // displays the alert for confirming refreshing or closing the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any) {
        if (!this.canDeactivate()) {
            $event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }
}
