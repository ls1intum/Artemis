import { Component, input } from '@angular/core';
import { faCheckCircle } from '@fortawesome/free-solid-svg-icons/faCheckCircle';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons/faCircleNotch';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons/faExclamationTriangle';
import { faTimesCircle } from '@fortawesome/free-solid-svg-icons/faTimesCircle';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CommitState, EditorState } from '../model/code-editor.model';

@Component({
    selector: 'jhi-code-editor-status',
    templateUrl: './code-editor-status.component.html',
    providers: [],
    imports: [TumAetUiTooltipDirective, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class CodeEditorStatusComponent {
    CommitState = CommitState;
    EditorState = EditorState;

    readonly editorState = input<EditorState>();
    readonly commitState = input<CommitState>();

    // Icons
    faCircleNotch = faCircleNotch;
    faExclamationTriangle = faExclamationTriangle;
    faCheckCircle = faCheckCircle;
    faTimesCircle = faTimesCircle;
}
