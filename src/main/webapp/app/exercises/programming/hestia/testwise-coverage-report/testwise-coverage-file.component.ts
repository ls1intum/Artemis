import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewChild, ViewEncapsulation } from '@angular/core';
import { CoverageFileReport } from 'app/entities/hestia/coverage-file-report.model';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

@Component({
    selector: 'jhi-testwise-coverage-file',
    templateUrl: './testwise-coverage-file.component.html',
    styleUrls: ['./testwise-coverage-file.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TestwiseCoverageFileComponent implements OnInit, OnChanges {
    @Input()
    fileContent: string;

    @Input()
    fileName: string;

    @Input()
    fileReport: CoverageFileReport;

    @ViewChild('editor', { static: true })
    editor: MonacoEditorComponent;

    proportionCoveredLines: number;
    proportionString: string;
    editorHeight: number = 20;

    static readonly COVERED_LINE_HIGHLIGHT_CLASS = 'covered-line-highlight';

    ngOnInit(): void {
        this.renderFile();
        this.editorHeight = this.editor.getContentHeight();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.fileReport || changes.fileContent) {
            this.renderFile();
        }
    }

    private aggregateCoveredLinesBlocks(fileReport: CoverageFileReport): Map<number, number> {
        const coveredLines = new Set<number>();
        const entries = fileReport.testwiseCoverageEntries!;
        // retrieve all covered line numbers
        entries.forEach((entry) => {
            this.getRangeArray(entry.startLine!, entry.lineCount!).forEach((line) => coveredLines.add(line));
        });

        // build the blocks
        const orderedLines = Array.from(coveredLines).sort();
        const startLineByLength = new Map<number, number>();

        // set the covered line ratio accordingly
        this.proportionCoveredLines = orderedLines.length / this.fileReport!.lineCount!;
        this.proportionString = `${(this.proportionCoveredLines * 100).toFixed(1)} %`;

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

    private renderFile() {
        this.editor.changeModel(this.fileName, this.fileContent ?? '');
        this.editor.disposeLineHighlights();
        this.aggregateCoveredLinesBlocks(this.fileReport).forEach((blockLength, lineNumber) => {
            this.editor.highlightLines(lineNumber + 1, lineNumber + blockLength, 'covered-line-highlight', 'covered-line-highlight');
        });
    }
}
