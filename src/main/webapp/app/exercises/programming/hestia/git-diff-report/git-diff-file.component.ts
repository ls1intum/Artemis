import { Component, EventEmitter, Input, OnInit, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/monaco-diff-editor.component';

@Component({
    selector: 'jhi-git-diff-file',
    templateUrl: './git-diff-file.component.html',
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
    originalFileContent?: string;

    @Input()
    modifiedFileContent?: string;

    @Input()
    numberOfContextLines = 3;

    @Output()
    onDiffReady = new EventEmitter<boolean>();

    originalFilePath?: string;
    modifiedFilePath?: string;

    ngOnInit(): void {
        this.determineFilePaths();
        this.monacoDiffEditor.setUnchangedRegionHidingEnabled(!this.diffForTemplateAndSolution);
        this.monacoDiffEditor.setFileContents(this.originalFileContent, this.originalFilePath, this.modifiedFileContent, this.modifiedFilePath);
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
