import { Component } from '@angular/core';
import { faListAlt } from '@fortawesome/free-solid-svg-icons';
import { CodeEditorFileBrowserNodeComponent } from 'app/programming/manage/code-editor/file-browser/node/code-editor-file-browser-node.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-code-editor-file-browser-problem-statement',
    standalone: true,
    templateUrl: './code-editor-file-browser-problem-statement.component.html',
    imports: [FaIconComponent, NgClass, TranslateDirective],
})
export class CodeEditorFileBrowserProblemStatementComponent extends CodeEditorFileBrowserNodeComponent {
    // Icons
    readonly faListAlt = faListAlt;
}
