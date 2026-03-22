import { ChangeDetectionStrategy, Component, HostListener, effect, input, viewChild } from '@angular/core';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';
import { CUSTOM_MARKDOWN_LANGUAGE_ID } from 'app/shared/monaco-editor/model/languages/monaco-custom-markdown.language';
import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';

@Component({
    selector: 'jhi-exercise-version-markdown-diff',
    template: `
        <div class="version-markdown-diff" [style.min-height.px]="initialEditorHeight()">
            <jhi-monaco-diff-editor [allowSplitView]="renderSideBySide" [languageId]="customMarkdownLanguageId" />
        </div>
    `,
    styles: [
        `
            :host {
                display: block;
            }

            .version-markdown-diff {
                overflow: hidden;
                border: 1px solid var(--surface-border);
                border-radius: 8px;
                background: var(--surface-ground);
            }
        `,
    ],
    imports: [MonacoDiffEditorComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseVersionMarkdownDiffComponent {
    readonly original = input<string | undefined>();
    readonly modified = input<string | undefined>();
    readonly domainActions = input<TextEditorDomainAction[]>([]);
    readonly initialEditorHeight = input<MarkdownEditorHeight>(MarkdownEditorHeight.MEDIUM);

    readonly editor = viewChild(MonacoDiffEditorComponent);
    readonly customMarkdownLanguageId = CUSTOM_MARKDOWN_LANGUAGE_ID;

    renderSideBySide = typeof window === 'undefined' ? true : window.innerWidth >= 1200;

    constructor() {
        effect(() => {
            const original = this.original() ?? '';
            const modified = this.modified() ?? '';
            const editor = this.editor();
            if (!editor) {
                return;
            }

            editor.setFileContents(original, modified, 'snapshot-before.md', 'snapshot-after.md');
        });
    }

    @HostListener('window:resize')
    onResize(): void {
        this.renderSideBySide = window.innerWidth >= 1200;
    }
}
