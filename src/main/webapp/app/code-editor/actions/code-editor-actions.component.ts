import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { Observable, Subscription, throwError } from 'rxjs';
import { isEmpty as _isEmpty } from 'lodash';

import { CommitState, EditorState, GitConflictState } from 'app/code-editor';
import { ConflictStateService } from 'app/code-editor/service';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/code-editor/service/code-editor-repository.service';
import { CodeEditorResolveConflictModalComponent } from 'app/code-editor/actions/code-editor-resolve-conflict-modal.component';

@Component({
    selector: 'jhi-code-editor-actions',
    templateUrl: './code-editor-actions.component.html',
})
export class CodeEditorActionsComponent implements OnInit, OnDestroy {
    CommitState = CommitState;
    EditorState = EditorState;

    @Input()
    buildable = true;
    @Input()
    unsavedFiles: { [fileName: string]: string };
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
    onSavedFiles = new EventEmitter<{ [fileName: string]: string | null }>();
    @Output()
    onError = new EventEmitter<string>();

    editorStateValue: EditorState;
    commitStateValue: CommitState;
    isBuildingValue: boolean;
    isResolvingConflict = false;

    conflictStateSubscription: Subscription;

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

    constructor(
        private repositoryService: CodeEditorRepositoryService,
        private repositoryFileService: CodeEditorRepositoryFileService,
        private conflictService: ConflictStateService,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        this.conflictStateSubscription = this.conflictService.subscribeConflictState().subscribe((gitConflictState: GitConflictState) => {
            // When the conflict is encountered when opening the code-editor, setting the commitState here could cause an uncheckedException.
            // This is why a timeout of 0 is set to make sure the template is rendered before setting the commitState.
            if (this.commitState === CommitState.CONFLICT && gitConflictState === GitConflictState.OK) {
                // Case a: Conflict was resolved.
                setTimeout(() => (this.commitState = CommitState.UNDEFINED), 0);
            } else if (this.commitState !== CommitState.CONFLICT && gitConflictState === GitConflictState.CHECKOUT_CONFLICT) {
                // Case b: Conflict has occurred.
                setTimeout(() => (this.commitState = CommitState.CONFLICT), 0);
            }
        });
    }

    ngOnDestroy(): void {
        if (this.conflictStateSubscription) {
            this.conflictStateSubscription.unsubscribe();
        }
    }

    /**
     * @function saveFiles
     * @desc Saves all files that have unsaved changes in the editor.
     */
    saveChangedFiles() {
        if (!_isEmpty(this.unsavedFiles)) {
            this.editorState = EditorState.SAVING;
            const unsavedFiles = Object.entries(this.unsavedFiles).map(([fileName, fileContent]) => ({ fileName, fileContent }));
            return this.repositoryFileService.updateFiles(unsavedFiles).pipe(
                tap(res => this.onSavedFiles.emit(res)),
                catchError(err => {
                    this.onError.emit(err.error);
                    this.editorState = EditorState.UNSAVED_CHANGES;
                    return throwError('saving failed');
                }),
            );
        } else {
            return Observable.of(null);
        }
    }

    /**
     * @function commit
     * @desc Commits the current repository files.
     * If there are unsaved changes, save them first before trying to commit again.
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
                    if (this.buildable) {
                        this.isBuilding = true;
                    }
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

    resetRepository() {
        this.modalService.open(CodeEditorResolveConflictModalComponent, { keyboard: true, size: 'lg' });
    }
}
