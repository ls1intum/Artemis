import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, effect, input, output, viewChild } from '@angular/core';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/monaco-diff-editor.component';

@Component({
    selector: 'jhi-git-diff-file',
    templateUrl: './git-diff-file.component.html',
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MonacoDiffEditorComponent],
})
export class GitDiffFileComponent {
    readonly monacoDiffEditor = viewChild.required(MonacoDiffEditorComponent);
    readonly diffForTemplateAndSolution = input<boolean>(false);
    readonly diffEntries = input.required<ProgrammingExerciseGitDiffEntry[]>();
    readonly originalFileContent = input<string>();
    readonly originalFilePath = input<string>();
    readonly modifiedFileContent = input<string>();
    readonly modifiedFilePath = input<string>();
    readonly allowSplitView = input<boolean>(true);
    readonly onDiffReady = output<boolean>();
    readonly fileUnchanged = computed(() => this.originalFileContent() === this.modifiedFileContent());

    constructor() {
        effect(() => {
            this.monacoDiffEditor().setFileContents(this.originalFileContent(), this.originalFilePath(), this.modifiedFileContent(), this.modifiedFilePath());
        });
    }
}
