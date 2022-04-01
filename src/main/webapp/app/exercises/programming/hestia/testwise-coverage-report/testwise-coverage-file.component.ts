import { Component, Input, ViewChild, OnInit } from '@angular/core';
import { ProgrammingExerciseTestwiseCoverageReport } from 'app/entities/hestia/programming-exercise-testwise-coverage-report.model';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import ace from 'brace';

@Component({
    selector: 'jhi-testwise-coverage-file',
    templateUrl: './testwise-coverage-file.component.html',
    styleUrls: ['./testwise-coverage-file.component.scss'],
})
export class TestwiseCoverageFileComponent implements OnInit {
    @Input()
    fileContent: string;

    @Input()
    fileName: string;

    @Input()
    reports: ProgrammingExerciseTestwiseCoverageReport[];

    @ViewChild('editor', { static: true })
    editor: AceEditorComponent;

    proportionCoveredLines: number;

    constructor() {}

    ngOnInit(): void {
        this.setupEditor(this.editor, this.fileContent, this.reports);
    }

    private aggregateCoveredLinesBlocks(reports: ProgrammingExerciseTestwiseCoverageReport[]): Map<number, number> {
        const coveredLines = new Set<number>();
        const entries = reports.flatMap((entryByReport) => entryByReport.entries);
        // retrieve all covered line numbers
        entries.forEach((entry) => {
            this.getRangeArray(entry!.startLine!, entry!.lineCount ?? 0).forEach((line) => coveredLines.add(line));
        });

        const numberOfLines = this.fileContent.split('\n').length - 1;
        this.proportionCoveredLines = coveredLines.size / numberOfLines;

        // build the blocks
        const orderedLines = Array.from(coveredLines).sort();
        const startLineByLength = new Map<number, number>();

        let index = 0;
        while (index < orderedLines.length) {
            const currentBlockStartLine = orderedLines[index];
            let currentBlockLength = 1;
            let continueBlock = true;

            // count the length of the consecutive blocks
            while (continueBlock && index < orderedLines.length) {
                if (index + 1 === orderedLines.length) {
                    continueBlock = false;
                } else if (orderedLines[index + 1] === orderedLines[index] + 1) {
                    currentBlockLength++;
                } else {
                    continueBlock = false;
                }
                index++;
            }
            startLineByLength.set(currentBlockStartLine, currentBlockLength);
        }

        return startLineByLength;
    }

    private getRangeArray(startLine: number, lineCount: number): number[] {
        return [...Array(lineCount).keys()].map((i) => i + startLine - 1);
    }

    private setupEditor(editor: AceEditorComponent, fileContent: string, reports: ProgrammingExerciseTestwiseCoverageReport[]): void {
        editor.setTheme('dreamweaver');
        editor.getEditor().setOptions({
            animatedScroll: true,
            maxLines: Infinity,
            highlightActiveLine: false,
            showPrintMargin: false,
        });
        editor.getEditor().getSession().setValue(fileContent);

        const aggregatedLineBlocks = this.aggregateCoveredLinesBlocks(reports);

        ace.Range = ace.acequire('ace/range').Range;
        aggregatedLineBlocks.forEach((blockLength, lineNumber) => {
            const range = new ace.Range(lineNumber, 0, lineNumber + blockLength - 1, 1);
            editor.getEditor().getSession().addMarker(range, 'ace_highlight-marker', 'fullLine');
        });
    }
}
