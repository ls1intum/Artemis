import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, effect, input, output, viewChild } from '@angular/core';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';
import { DiffInformation, LineChange } from 'app/shared/monaco-editor/diff-editor/util/monaco-diff-editor.util';
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
    readonly diffInformation = input.required<DiffInformation>();
    readonly allowSplitView = input<boolean>(true);
    readonly onDiffReady = output<{ready: boolean; lineChange: LineChange}>();
    readonly fileUnchanged = computed(() => this.diffInformation().templateFileContent === this.diffInformation().solutionFileContent);

    constructor() {
        effect(() => {
            this.monacoDiffEditor().setFileContents(this.diffInformation().templateFileContent, this.diffInformation().solutionFileContent, this.diffInformation().oldPath, this.diffInformation().path);
        });
    }
}
