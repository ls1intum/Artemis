import { Component, EventEmitter, HostListener, Input, Output, ViewChild } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { isEmpty as _isEmpty, fromPairs, toPairs, uniq } from 'lodash-es';
import { ActivatedRoute } from '@angular/router';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import {
    CommitState,
    CreateFileChange,
    DeleteFileChange,
    EditorState,
    FileChange,
    FileType,
    RenameFileChange,
    ResizeType,
} from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { AlertService } from 'app/core/util/alert.service';
import { CodeEditorFileBrowserComponent, InteractableEvent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { Participation } from 'app/entities/participation/participation.model';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { Feedback } from 'app/entities/feedback.model';
import { Course } from 'app/entities/course.model';
import { CodeEditorMonacoService } from 'app/exercises/programming/shared/code-editor/service/code-editor-monaco.service';
import { LspConfigModel } from 'app/exercises/programming/shared/code-editor/model/lsp-config.model';
import { MonacoComponent } from 'app/exercises/programming/shared/code-editor/monaco/monaco.component';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { faMobileScreenButton } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { HttpErrorResponse } from '@angular/common/http';

export enum CollapsableCodeEditorElement {
    FileBrowser,
    BuildOutput,
    Instructions,
}

@Component({
    selector: 'jhi-new-code-editor-container',
    templateUrl: './new-code-editor-container.component.html',
    styleUrls: ['./new-code-editor-container.component.scss'],
})
export class NewCodeEditorContainerComponent implements ComponentCanDeactivate {
    readonly CommitState = CommitState;
    readonly CollapsableCodeEditorElement = CollapsableCodeEditorElement;
    @ViewChild(CodeEditorGridComponent, { static: false }) grid: CodeEditorGridComponent;

    @ViewChild(CodeEditorFileBrowserComponent, { static: false }) fileBrowser: CodeEditorFileBrowserComponent;
    @ViewChild(CodeEditorActionsComponent, { static: false }) actions: CodeEditorActionsComponent;
    @ViewChild(CodeEditorBuildOutputComponent, { static: false }) buildOutput: CodeEditorBuildOutputComponent;
    @ViewChild(MonacoComponent, { static: false }) monacoEditor: MonacoComponent;
    @ViewChild(CodeEditorInstructionsComponent, { static: false }) instructions: CodeEditorInstructionsComponent;

    @Input()
    editable = true;
    @Input()
    buildable = true;
    @Input()
    showEditorInstructions = true;
    @Input()
    isTutorAssessment = false;
    @Input()
    highlightFileChanges = false;
    @Input()
    readOnlyManualFeedback = false;
    @Input()
    highlightDifferences: boolean;

    @Output()
    onResizeEditorInstructions = new EventEmitter<void>();
    @Output()
    onCommitStateChange = new EventEmitter<CommitState>();
    @Output()
    onFileChanged = new EventEmitter<void>();
    @Output()
    onUpdateFeedback = new EventEmitter<Feedback[]>();
    @Output()
    onFileLoad = new EventEmitter<string>();
    @Input()
    course?: Course;

    lspNotAvailable = false;

    /** Work in Progress: temporary properties needed to get first prototype working */

    _participation: Participation;
    get participation(): Participation {
        return this._participation;
    }
    @Input() set participation(value: Participation) {
        this._participation = value;
        if (value?.id) {
            this.lspService.initLsp(value.id).subscribe({
                next: (lspConfig) => (this.currentLspConfig = lspConfig),
                error: (e: HttpErrorResponse) => {
                    if (e.status === 503) {
                        this.lspNotAvailable = true;
                    }
                },
            });
        }
    }

    /** END WIP */

    // WARNING: Don't initialize variables in the declaration block. The method initializeProperties is responsible for this task.
    _selectedFile?: string;
    unsavedFilesValue: { [fileName: string]: string } = {}; // {[fileName]: fileContent}

    /** Code Editor State Variables **/
    editorState: EditorState;
    commitState: CommitState;

    errorFiles: string[] = [];
    annotations: Array<Annotation> = [];

    currentLspConfig: LspConfigModel;
    isPresentationMode = false;
    isTerminalInitialized = false;
    isRunEnabled = false;

    // Icons
    faMobileScreenButton = faMobileScreenButton;

    get selectedFile(): string | undefined {
        return this._selectedFile;
    }

    @Input() set selectedFile(value: string | undefined) {
        const previousFile = this.selectedFile;
        this._selectedFile = value;
        if (this.isOnMobile && previousFile !== value) {
            this.loadFile();
        }
    }

    isOnMobile = false;
    selectedFileContent?: string;

    constructor(
        private translateService: TranslateService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private fileService: CodeEditorFileService,
        private lspService: CodeEditorMonacoService,
        private repositoryFileService: CodeEditorRepositoryFileService,
        private featureToggleService: FeatureToggleService,
    ) {
        this.initializeProperties();
    }

    get unsavedFiles() {
        return this.unsavedFilesValue;
    }

    /**
     * Setting unsaved files also updates the editorState / commitState.
     * - unsaved files empty -> EditorState.CLEAN
     * - unsaved files NOT empty -> EditorState.UNSAVED_CHANGES & CommitState.UNCOMMITTED_CHANGES
     * - unsaved files empty AND editorState.SAVING -> CommitState.UNCOMMITTED_CHANGES
     * @param unsavedFiles
     */
    set unsavedFiles(unsavedFiles: { [fileName: string]: string }) {
        this.unsavedFilesValue = unsavedFiles;
        if (_isEmpty(this.unsavedFiles) && this.editorState === EditorState.SAVING) {
            this.editorState = EditorState.CLEAN;
            if (this.commitState !== CommitState.COMMITTING) {
                this.commitState = CommitState.UNCOMMITTED_CHANGES;
            }
        } else if (_isEmpty(this.unsavedFiles)) {
            this.editorState = EditorState.CLEAN;
        } else {
            this.editorState = EditorState.UNSAVED_CHANGES;
            this.commitState = CommitState.UNCOMMITTED_CHANGES;
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
        this.editorState = EditorState.CLEAN;
        this.commitState = CommitState.UNDEFINED;
        this.featureToggleService.getFeatureToggleActive(FeatureToggle.EditorTerminal).subscribe((isActive) => (this.isRunEnabled = isActive));
    };

    /**
     * @function onFileChange
     * @desc A file has changed (create, rename, delete), so we have uncommitted changes.
     * Also, all references to a file need to be updated in case of rename,
     * in case of delete make sure to also remove all sub entities (files in folder).
     */
    onFileChange<F extends FileChange>([, fileChange]: [string[], F]) {
        this.commitState = CommitState.UNCOMMITTED_CHANGES;
        if (fileChange instanceof CreateFileChange) {
            // Select newly created file
            if (fileChange.fileType === FileType.FILE) {
                this.selectedFile = fileChange.fileName;
            }
        } else if (fileChange instanceof RenameFileChange || fileChange instanceof DeleteFileChange) {
            this.unsavedFiles = this.fileService.updateFileReferences(this.unsavedFiles, fileChange);
            this.selectedFile = this.fileService.updateFileReference(this.selectedFile!, fileChange);
        }
        // If unsavedFiles are deleted, this can mean that the editorState becomes clean
        if (_isEmpty(this.unsavedFiles) && this.editorState === EditorState.UNSAVED_CHANGES) {
            this.editorState = EditorState.CLEAN;
        }
        // this.monacoEditor.onFileChange(fileChange);
        this.onFileChanged.emit();
    }

    /**
     * When files were saved, check which could be saved and set unsavedFiles to update the ui.
     * Files that could not be saved will show an error in the header.
     * @param files
     */
    onSavedFiles(files: { [fileName: string]: string | undefined }) {
        const savedFiles = Object.entries(files)
            .filter(([, error]: [string, string | undefined]) => !error)
            .map(([fileName]) => fileName);
        const errorFiles = Object.entries(files)
            .filter(([, error]: [string, string | undefined]) => error)
            .map(([fileName]) => fileName);

        this.unsavedFiles = fromPairs(toPairs(this.unsavedFiles).filter(([fileName]) => !savedFiles.includes(fileName)));

        if (errorFiles.length) {
            this.onError('saveFailed');
        }
        // this.monacoEditor.storeAnnotations(savedFiles);
    }

    /**
     * On successful pull during a refresh operation, we add all to the unsaved files
     * in order to sync them with the external Monaco server.
     */
    onRefreshFiles() {
        this.unsavedFiles = {};
        this.repositoryFileService.getFilesWithContent().subscribe((files) => {
            this.lspService.refresh(files);
        });
    }

    /**
     * When the content of a file changes, set it as unsaved.
     */
    onFileContentChange({ file, fileContent }: { file: string; fileContent: string }) {
        this.unsavedFiles = { ...this.unsavedFiles, [file]: fileContent };
        this.onFileChanged.emit();
    }

    updateFeedback(feedbacks: Feedback[]) {
        this.onUpdateFeedback.emit(feedbacks);
    }

    fileLoad(selectedFile: string) {
        this.onFileLoad.emit(selectedFile);
    }

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: any) {
        this.alertService.error(`artemisApp.editor.errors.${error as string}`);
    }

    /**
     * The user will be warned if there are unsaved changes when trying to leave the code-editor.
     */
    canDeactivate() {
        return _isEmpty(this.unsavedFiles);
    }

    // displays the alert for confirming refreshing or closing the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: any) {
        if (!this.canDeactivate()) {
            event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    onToggleCollapse(event: InteractableEvent, collapsableElement: CollapsableCodeEditorElement) {
        this.grid.toggleCollapse(event, collapsableElement);
    }

    togglePresentationMode(value: boolean) {
        this.isPresentationMode = value;
    }

    onGridResize(type: ResizeType) {
        if (type === ResizeType.SIDEBAR_RIGHT || type === ResizeType.MAIN_BOTTOM) {
            this.onResizeEditorInstructions.emit();
        }
        if (type === ResizeType.SIDEBAR_LEFT || type === ResizeType.SIDEBAR_RIGHT || type === ResizeType.MAIN_BOTTOM) {
            // this.monacoEditor.editor.getEditor().resize();
        }
    }

    /**
     * Set the annotations and extract error files for the file browser.
     * @param annotations The new annotations array
     */
    onAnnotations(annotations: Array<Annotation>) {
        this.annotations = annotations;
        this.errorFiles = uniq(annotations.filter((a) => a.type === 'error').map((a) => a.fileName));
    }

    /**
     * Function called from the terminal component as soon as it has correctly initialized
     */
    onIsTerminalInitialized() {
        this.isTerminalInitialized = true;
    }

    /**
     * Toggle to switch between mobile and desktop editor
     */
    toggleMobile() {
        this.isOnMobile = !this.isOnMobile;
    }

    /**
     * Loads the currently selected file in order to display it in the alternative 'textarea-editor'
     * for mobile devices not supporting Monaco
     */
    loadFile() {
        if (this.selectedFile) {
            this.repositoryFileService.getFile(this.selectedFile).subscribe((file) => {
                this.selectedFileContent = file.fileContent;
            });
        }
    }
}
