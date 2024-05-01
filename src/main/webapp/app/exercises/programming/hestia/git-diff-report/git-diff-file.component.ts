import { Component, EventEmitter, Input, OnInit, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/monaco-diff-editor.component';

@Component({
    selector: 'jhi-git-diff-file',
    templateUrl: './git-diff-file.component.html',
    styleUrls: ['./git-diff-file.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class GitDiffFileComponent implements OnInit {
    @ViewChild(MonacoDiffEditorComponent, { static: true })
    monacoDiffEditor: MonacoDiffEditorComponent;

    @Input()
    diffForTemplateAndSolution: boolean = false;

    @Input()
    diffEntries: ProgrammingExerciseGitDiffEntry[];

    @Input()
    templateFileContent: string | undefined;

    @Input()
    solutionFileContent: string | undefined;

    @Input()
    numberOfContextLines = 3;

    @Output()
    onDiffReady = new EventEmitter<boolean>();

    previousFilePath: string | undefined;
    filePath: string | undefined;

    ngOnInit(): void {
        this.determineFilePaths();
        this.monacoDiffEditor.setUnchangedRegionHidingEnabled(!this.diffForTemplateAndSolution);
        this.monacoDiffEditor.setFileContents(this.templateFileContent, this.previousFilePath, this.solutionFileContent, this.filePath);
    }

    /**
     * Determines the previous and current file path of the current file
     */
    private determineFilePaths() {
        this.filePath = this.diffEntries
            .map((entry) => entry.filePath)
            .filter((filePath) => filePath)
            .first();

        this.previousFilePath = this.diffEntries
            .map((entry) => entry.previousFilePath)
            .filter((filePath) => filePath)
            .first();
    }
}
