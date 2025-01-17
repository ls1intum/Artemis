import { Component, Input } from '@angular/core';
import { faCheckCircle, faCircleNotch, faExclamationTriangle, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { CommitState, EditorState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-code-editor-status',
    templateUrl: './code-editor-status.component.html',
    providers: [],
    imports: [NgbTooltip, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
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
