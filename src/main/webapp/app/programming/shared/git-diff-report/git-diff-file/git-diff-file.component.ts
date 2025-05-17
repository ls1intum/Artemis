import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, effect, input, output, viewChild } from '@angular/core';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';
import { LineChange } from 'app/programming/shared/git-diff-report/model/git-diff.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-git-diff-file',
    templateUrl: './git-diff-file.component.html',
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MonacoDiffEditorComponent, TranslateDirective],
})
export class GitDiffFileComponent {
    readonly monacoDiffEditor = viewChild.required(MonacoDiffEditorComponent);
    readonly originalFileContent = input<string>();
    readonly modifiedFileContent = input<string>();
    readonly allowSplitView = input<boolean>(true);
    readonly path = input<string>();
    readonly onDiffReady = output<{ready: boolean; lineChange: LineChange}>();
    readonly fileUnchanged = computed(() => this.originalFileContent() === this.modifiedFileContent());

    constructor() {
        effect(() => {
            this.monacoDiffEditor().setFileContents(this.originalFileContent(), this.modifiedFileContent(), this.path());
        });
    }
}
