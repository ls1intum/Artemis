import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, effect, input, output, viewChild } from '@angular/core';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/monaco-diff-editor.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LineStat } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';

@Component({
    selector: 'jhi-git-diff-file',
    templateUrl: './git-diff-file.component.html',
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MonacoDiffEditorComponent, TranslateDirective],
})
export class GitDiffFileComponent {
    readonly monacoDiffEditor = viewChild.required(MonacoDiffEditorComponent);
    readonly diffForTemplateAndSolution = input<boolean>(false);
    readonly originalFileContent = input<string>();
    readonly originalFilePath = input<string>();
    readonly modifiedFileContent = input<string>();
    readonly modifiedFilePath = input<string>();
    readonly allowSplitView = input<boolean>(true);
    readonly onDiffReady = output<boolean>();
    readonly lineStatChanged = output<LineStat>();
    readonly fileUnchanged = computed(() => this.originalFileContent() === this.modifiedFileContent());

    constructor() {
        effect(() => {
            this.monacoDiffEditor().setFileContents(this.originalFileContent(), this.originalFilePath(), this.modifiedFileContent(), this.modifiedFilePath());
        });
    }
}
