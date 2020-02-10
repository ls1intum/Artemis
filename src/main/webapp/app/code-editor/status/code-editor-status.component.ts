import { Component, Input } from '@angular/core';
import { EditorState } from 'app/code-editor/model/editor-state.model';
import { CommitState } from 'app/code-editor/model/commit-state.model';

@Component({
    selector: 'jhi-code-editor-status',
    templateUrl: './code-editor-status.component.html',
    providers: [],
})
export class CodeEditorStatusComponent {
    CommitState = CommitState;
    EditorState = EditorState;

    @Input()
    editorState: EditorState;
    @Input()
    commitState: CommitState;
}
