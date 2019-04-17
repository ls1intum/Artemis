import { Component, Input } from '@angular/core';
import { CommitState, EditorState } from 'app/code-editor';

@Component({
    selector: 'jhi-code-editor-actions',
    templateUrl: './code-editor-actions.component.html',
    providers: [],
})
export class CodeEditorActionsComponent {
    @Input()
    editorState: EditorState;
    @Input()
    commitState: CommitState;
    @Input()
    isBuilding: boolean;
    @Input()
    onSave: () => void;
    @Input()
    onCommit: (event: any) => void;
}
