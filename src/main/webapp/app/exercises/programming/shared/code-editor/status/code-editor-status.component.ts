import { Component, Input } from '@angular/core';
import { faCheckCircle, faCircleNotch, faExclamationTriangle, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { CommitState, EditorState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

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

    // Icons
    faCircleNotch = faCircleNotch;
    faExclamationTriangle = faExclamationTriangle;
    faCheckCircle = faCheckCircle;
    faTimesCircle = faTimesCircle;
}
