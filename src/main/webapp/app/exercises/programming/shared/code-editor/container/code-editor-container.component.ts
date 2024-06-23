import { Component, EventEmitter, HostListener, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { isEmpty as _isEmpty, fromPairs, toPairs, uniq } from 'lodash-es';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import {
    CommitState,
    CreateFileChange,
    DeleteFileChange,
    EditorState,
    FileBadge,
    FileBadgeType,
    FileChange,
    FileType,
    RenameFileChange,
    ResizeType,
} from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { AlertService } from 'app/core/util/alert.service';
import { CodeEditorFileBrowserComponent, InteractableEvent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { Annotation, CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { Participation } from 'app/entities/participation/participation.model';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { Feedback } from 'app/entities/feedback.model';
import { Course } from 'app/entities/course.model';
import { ConnectionError } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorMonacoComponent } from 'app/exercises/programming/shared/code-editor/monaco/code-editor-monaco.component';

export enum CollapsableCodeEditorElement {
    FileBrowser,
    BuildOutput,
    Instructions,
}

@Component({
    selector: 'jhi-code-editor-container',
    templateUrl: './code-editor-container.component.html',
    styleUrls: ['./code-editor-container.component.scss'],
})
export class CodeEditorContainerComponent implements OnChanges, ComponentCanDeactivate {
    readonly CommitState = CommitState;
    readonly EditorState = EditorState;
    readonly CollapsableCodeEditorElement = CollapsableCodeEditorElement;
    @ViewChild(CodeEditorGridComponent, { static: false }) grid: CodeEditorGridComponent;

    @ViewChild(CodeEditorFileBrowserComponent, { static: false }) fileBrowser: CodeEditorFileBrowserComponent;
    @ViewChild(CodeEditorActionsComponent, { static: false }) actions: CodeEditorActionsComponent;
    @ViewChild(CodeEditorBuildOutputComponent, { static: false }) buildOutput: CodeEditorBuildOutputComponent;
    @ViewChild(CodeEditorAceComponent, { static: false }) aceEditor?: CodeEditorAceComponent;
    @ViewChild(CodeEditorMonacoComponent, { static: false }) monacoEditor?: CodeEditorMonacoComponent;
    @ViewChild(CodeEditorInstructionsComponent, { static: false }) instructions: CodeEditorInstructionsComponent;

    @Input()
    editable = true;
    @Input()
    forRepositoryView = false;
    @Input()
    buildable = true;
    @Input()
    showEditorInstructions = true;
    @Input()
    isTutorAssessment = false;
    @Input()
    highlightFileChanges = false;
    @Input()
    allowHiddenFiles = false;
    @Input()
    feedbackSuggestions: Feedback[] = [];
    @Input()
    readOnlyManualFeedback = false;
    @Input()
    highlightDifferences: boolean;
    @Input()
    disableAutoSave = false;
    @Input()
    useMonacoEditor = false;

    @Output()
    onCommitStateChange = new EventEmitter<CommitState>();
    @Output()
    onFileChanged = new EventEmitter<void>();
    @Output()
    onUpdateFeedback = new EventEmitter<Feedback[]>();
    @Output()
    onFileLoad = new EventEmitter<string>();
    @Output()
    onAcceptSuggestion = new EventEmitter<Feedback>();
    @Output()
    onDiscardSuggestion = new EventEmitter<Feedback>();
    @Input()
    course?: Course;

    /** Work in Progress: temporary properties needed to get first prototype working */

    @Input()
    participation: Participation;

    /** END WIP */

    // WARNING: Don't initialize variables in the declaration block. The method initializeProperties is responsible for this task.
    selectedFile?: string;
    unsavedFilesValue: { [fileName: string]: string }; // {[fileName]: fileContent}
    fileBadges: { [fileName: string]: FileBadge[] };

    /** Code Editor State Variables **/
    editorState: EditorState;
    commitState: CommitState;

    errorFiles: string[] = [];
    annotations: Array<Annotation> = [];

    constructor(
        private translateService: TranslateService,
        private alertService: AlertService,
        private fileService: CodeEditorFileService,
    ) {
        this.initializeProperties();
    }

    ngOnChanges(changes: SimpleChanges) {
        // Update file badges when feedback suggestions change
        if (changes.feedbackSuggestions) {
            this.updateFileBadges();
        }
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
     * Update the file badges for the code editor (currently only feedback suggestions)
     */
    updateFileBadges() {
        this.fileBadges = {};
        // Create badges for feedback suggestions
        // Get file paths from feedback suggestions:
        const filePathsWithSuggestions = this.feedbackSuggestions
            .map((feedback) => Feedback.getReferenceFilePath(feedback))
            .filter((filePath) => filePath !== undefined) as string[];
        for (const filePath of filePathsWithSuggestions) {
            // Count the number of suggestions for this file
            const suggestionsCount = this.feedbackSuggestions.filter((feedback) => Feedback.getReferenceFilePath(feedback) === filePath).length;
            this.fileBadges[filePath] = [new FileBadge(FileBadgeType.FEEDBACK_SUGGESTION, suggestionsCount)];
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
        this.fileBadges = {};
        this.editorState = EditorState.CLEAN;
        this.commitState = CommitState.UNDEFINED;
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
        this.monacoEditor?.onFileChange(fileChange);
        this.aceEditor?.onFileChange(fileChange);

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
        this.aceEditor?.storeAnnotations(savedFiles);
        this.monacoEditor?.storeAnnotations(savedFiles);
    }

    /**
     * On successful pull during a refresh operation, we remove all unsaved files.
     */
    onRefreshFiles() {
        this.unsavedFiles = {};
    }

    /**
     * When the content of a file changes, set it as unsaved.
     */
    onFileContentChange({ file, fileContent }: { file: string; fileContent: string }) {
        this.unsavedFiles = { ...this.unsavedFiles, [file]: fileContent };
        this.onFileChanged.emit();
    }

    fileLoad(selectedFile: string) {
        this.onFileLoad.emit(selectedFile);
    }

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * @param error the translation key of the error that should be displayed
     */
    onError(error: any) {
        let errorTranslationKey: string;
        const translationParams = { connectionIssue: '' };
        if (!error.includes(ConnectionError.message)) {
            errorTranslationKey = error;
        } else {
            translationParams.connectionIssue = this.translateService.instant(`artemisApp.editor.errors.${ConnectionError.message}`);
            errorTranslationKey = error.replaceAll(ConnectionError.message, '');
        }
        this.alertService.error(`artemisApp.editor.errors.${errorTranslationKey}`, translationParams);
    }

    /**
     * The user will be warned if there are unsaved changes when trying to leave the code-editor.
     */
    canDeactivate() {
        return _isEmpty(this.unsavedFiles);
    }

    getText(): string {
        return this.monacoEditor?.getText() ?? '';
    }

    getNumberOfLines(): number {
        if (this.aceEditor) {
            return this.aceEditor.editorSession.getLength();
        }
        return this.monacoEditor?.getNumberOfLines() ?? 0;
    }

    highlightLines(startLine: number, endLine: number): void {
        // Workaround: increase line number by 1 for monaco
        // Will be removed once ace is gone from every instance of this component
        this.monacoEditor?.highlightLines(startLine + 1, endLine + 1);
        this.aceEditor?.highlightLines(startLine, endLine, 'diff-newLine', 'gutter-diff-newLine');
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

    onGridResize(type: ResizeType) {
        if (this.aceEditor && (type === ResizeType.SIDEBAR_LEFT || type === ResizeType.SIDEBAR_RIGHT || type === ResizeType.MAIN_BOTTOM)) {
            this.aceEditor.editor.getEditor().resize();
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
}
