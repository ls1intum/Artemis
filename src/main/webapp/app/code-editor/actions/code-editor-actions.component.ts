import { Component, EventEmitter, Input, Output, SimpleChanges } from '@angular/core';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { isEmpty as _isEmpty } from 'lodash';

import { CommitState, EditorState } from 'app/code-editor';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/code-editor/service/code-editor-repository.service';

@Component({
    selector: 'jhi-code-editor-actions',
    templateUrl: './code-editor-actions.component.html',
    providers: [],
})
export class CodeEditorActionsComponent {
    CommitState = CommitState;
    EditorState = EditorState;

    @Input()
    readonly unsavedFiles: { [fileName: string]: string };
    @Input()
    get editorState() {
        return this.editorStateValue;
    }
    @Input()
    get commitState() {
        return this.commitStateValue;
    }
    @Input()
    get isBuilding() {
        return this.isBuildingValue;
    }

    @Output()
    commitStateChange = new EventEmitter<CommitState>();
    @Output()
    editorStateChange = new EventEmitter<EditorState>();
    @Output()
    isBuildingChange = new EventEmitter<boolean>();
    @Output()
    onSavedFiles = new EventEmitter<Array<[string, string | null]>>();
    @Output()
    onError = new EventEmitter<string>();

    editorStateValue: EditorState;
    commitStateValue: CommitState;
    isBuildingValue: boolean;

    set commitState(commitState: CommitState) {
        this.commitStateValue = commitState;
        this.commitStateChange.emit(commitState);
    }

    set editorState(editorState: EditorState) {
        this.editorStateValue = editorState;
        this.editorStateChange.emit(editorState);
    }

    set isBuilding(isBuilding: boolean) {
        this.isBuildingValue = isBuilding;
        this.isBuildingChange.emit(isBuilding);
    }

    constructor(private repositoryService: CodeEditorRepositoryService, private repositoryFileService: CodeEditorRepositoryFileService) {}

    /**
     * @function saveFiles
     * @desc Saves all files that have unsaved changes in the editor.
     */
    saveChangedFiles() {
        if (!_isEmpty(this.unsavedFiles)) {
            this.editorState = EditorState.SAVING;
            const unsavedFiles = Object.entries(this.unsavedFiles).map(([fileName, fileContent]) => ({ fileName, fileContent }));
            return this.repositoryFileService.updateFiles(unsavedFiles).pipe(
                catchError(err => {
                    this.onError.emit(err.error);
                    this.editorState = EditorState.UNSAVED_CHANGES;
                    return Observable.of(null);
                }),
                tap((res: Array<[string, string]> | null) => (res ? this.onSavedFiles.emit(res) : null)),
            );
        } else {
            return Observable.of(null);
        }
    }

    /**
     * @function commit
     * @desc Commits the current repository files.
     * If there are unsaved changes, save them first before trying to commit again.
     * @param $event
     */
    commit() {
        // Avoid multiple commits at the same time.
        if (this.commitState === CommitState.COMMITTING) {
            return;
        }
        // If there are unsaved changes, save them before trying to commit again.
        Observable.of(null)
            .pipe(
                switchMap(() => (this.editorState === EditorState.UNSAVED_CHANGES ? this.saveChangedFiles() : Observable.of(null))),
                tap(() => (this.commitState = CommitState.COMMITTING)),
                switchMap(() => this.repositoryService.commit()),
                tap(() => {
                    this.commitState = CommitState.CLEAN;
                    this.isBuilding = true;
                }),
            )
            .subscribe(
                () => {},
                () => {
                    this.commitState = CommitState.UNCOMMITTED_CHANGES;
                    this.onError.emit('commitFailed');
                },
            );
    }
}
