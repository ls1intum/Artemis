import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output, ViewChild, ViewEncapsulation } from '@angular/core';
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
export class GitDiffFileComponent implements OnInit {
    @ViewChild(MonacoDiffEditorComponent, { static: true })
    monacoDiffEditor: MonacoDiffEditorComponent;

    @Input()
    diffForTemplateAndSolution: boolean = false;

    @Input()
    diffEntries: ProgrammingExerciseGitDiffEntry[];

    @Input()
    originalFileContent?: string;

    @Input()
    modifiedFileContent?: string;

    @Input()
    allowSplitView = true;

    @Output()
    onDiffReady = new EventEmitter<boolean>();

    originalFilePath?: string;
    modifiedFilePath?: string;
    fileUnchanged = false;

    ngOnInit(): void {
        this.determineFilePaths();
        this.monacoDiffEditor.setFileContents(this.originalFileContent, this.originalFilePath, this.modifiedFileContent, this.modifiedFilePath);
        this.fileUnchanged = this.originalFileContent === this.modifiedFileContent;
    }

    /**
     * Determines the previous and current file path of the current file
     */
    private determineFilePaths() {
        this.modifiedFilePath = this.diffEntries
            .map((entry) => entry.filePath)
            .filter((filePath) => filePath)
            .first();

        this.originalFilePath = this.diffEntries
            .map((entry) => entry.previousFilePath)
            .filter((filePath) => filePath)
            .first();
    }
}
