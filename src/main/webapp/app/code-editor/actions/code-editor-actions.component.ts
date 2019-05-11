import { Component, EventEmitter, Input, Output, SimpleChanges, OnChanges } from '@angular/core';
import { catchError, map as rxMap, switchMap, tap } from 'rxjs/operators';

import { CommitState, EditorState } from 'app/code-editor';
import { CodeEditorRepositoryService } from 'app/code-editor/code-editor-repository.service';

@Component({
    selector: 'jhi-code-editor-actions',
    templateUrl: './code-editor-actions.component.html',
    providers: [],
})
export class CodeEditorActionsComponent implements OnChanges {
    @Input()
    readonly editorState: EditorState;
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
    isBuildingChange = new EventEmitter<boolean>();
    @Output()
    onSave = new EventEmitter<void>();

    commitStateValue: CommitState;
    isBuildingValue: boolean;

    set commitState(commitState: CommitState) {
        this.commitStateValue = commitState;
        this.commitStateChange.emit(commitState);
    }

    set isBuilding(isBuilding: boolean) {
        this.isBuildingValue = isBuilding;
        this.isBuildingChange.emit(isBuilding);
    }

    constructor(private repositoryService: CodeEditorRepositoryService) {}

    ngOnChanges(changes: SimpleChanges) {
        if (changes.editorState && this.commitState === CommitState.WANTS_TO_COMMIT && this.editorState === EditorState.CLEAN) {
            this.commit();
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
        if (this.editorState === EditorState.UNSAVED_CHANGES) {
            this.commitState = CommitState.WANTS_TO_COMMIT;
            this.onSave.emit();
        } else {
            this.commitState = CommitState.COMMITTING;
            this.repositoryService
                .commit()
                // TODO: How to only do this when buildable?
                .pipe(tap(() => (this.isBuilding = true)))
                .subscribe(
                    () => {
                        this.commitState = CommitState.CLEAN;
                    },
                    (err: any) => {
                        console.log('Error during commit ocurred!', err);
                    },
                );
        }
    }
}
