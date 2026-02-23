import { Component, input } from '@angular/core';
import { faListAlt } from '@fortawesome/free-solid-svg-icons';
import { CodeEditorFileBrowserNodeComponent } from 'app/programming/manage/code-editor/file-browser/node/code-editor-file-browser-node.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FileBadge } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileBrowserBadgeComponent } from 'app/programming/manage/code-editor/file-browser/badge/code-editor-file-browser-badge.component';

@Component({
    selector: 'jhi-code-editor-file-browser-problem-statement',
    standalone: true,
    templateUrl: './code-editor-file-browser-problem-statement.component.html',
    imports: [FaIconComponent, NgClass, TranslateDirective, CodeEditorFileBrowserBadgeComponent],
})
export class CodeEditorFileBrowserProblemStatementComponent extends CodeEditorFileBrowserNodeComponent {
    badges = input<FileBadge[]>([]);
    // Icons
    readonly faListAlt = faListAlt;
}
