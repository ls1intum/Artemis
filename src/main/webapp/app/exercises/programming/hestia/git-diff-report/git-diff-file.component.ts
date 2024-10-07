import { ChangeDetectionStrategy, Component, ViewEncapsulation, effect, input, output, signal, untracked, viewChild } from '@angular/core';
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

    fileUnchanged = signal<boolean>(false);

    constructor() {
        effect(
            () => {
                untracked(() => {
                    this.monacoDiffEditor().setFileContents(this.originalFileContent(), this.originalFilePath(), this.modifiedFileContent(), this.modifiedFilePath());
                    this.fileUnchanged.set(this.originalFileContent() === this.modifiedFileContent());
                });
            },
            { allowSignalWrites: true },
        );
    }
}
