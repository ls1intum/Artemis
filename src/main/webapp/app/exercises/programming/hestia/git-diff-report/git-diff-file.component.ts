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
    monacoDiffEditor = viewChild.required(MonacoDiffEditorComponent);

    diffForTemplateAndSolution = input<boolean>(false);
    diffEntries = input.required<ProgrammingExerciseGitDiffEntry[]>();
    originalFileContent = input<string>();
    originalFilePath = input<string>();
    modifiedFileContent = input<string>();
    modifiedFilePath = input<string>();
    allowSplitView = input<boolean>(true);
    onDiffReady = output<boolean>();

    fileUnchanged = computed(() => this.originalFileContent() === this.modifiedFileContent());

    constructor() {
        effect(() => {
            this.monacoDiffEditor().setFileContents(this.originalFileContent(), this.originalFilePath(), this.modifiedFileContent(), this.modifiedFilePath());
        });
    }
}
