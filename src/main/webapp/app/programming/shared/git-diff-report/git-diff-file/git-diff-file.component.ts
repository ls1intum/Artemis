import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, effect, input, output, viewChild } from '@angular/core';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';
import { DiffInformation } from 'app/programming/shared/utils/diff.utils';
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
    readonly loadContent = input<boolean>(true); // Controls whether Monaco editor content should be loaded
    readonly onDiffReady = output<boolean>();
    readonly fileUnchanged = computed(() => this.diffInformation().originalFileContent === this.diffInformation().modifiedFileContent);

    constructor() {
        effect(() => {
            if (!this.loadContent()) {
                return;
            }
            this.monacoDiffEditor().setFileContents(
                this.diffInformation().originalFileContent,
                this.diffInformation().modifiedFileContent,
                this.diffInformation().originalPath,
                this.diffInformation().modifiedPath,
            );
        });
    }
}
