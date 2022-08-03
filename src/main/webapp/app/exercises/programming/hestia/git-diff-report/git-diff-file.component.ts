import { Component, Input, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import ace, { acequire } from 'brace';

@Component({
    selector: 'jhi-git-diff-file',
    templateUrl: './git-diff-file.component.html',
    styleUrls: ['./git-diff-file.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class GitDiffFileComponent implements OnInit {
    @ViewChild('editorPrevious', { static: true })
    editorPrevious: AceEditorComponent;

    @ViewChild('editorNow', { static: true })
    editorNow: AceEditorComponent;

    @Input()
    diffEntries: ProgrammingExerciseGitDiffEntry[];

    @Input()
    templateFileContent: string | undefined;

    @Input()
    solutionFileContent: string | undefined;

    @Input()
    numberOfContextLines = 3;

    previousFilePath: string | undefined;
    filePath: string | undefined;

    templateLines: string[] = [];
    solutionLines: string[] = [];

    actualStartLine: number;
    previousEndLine: number;
    endLine: number;
    actualEndLine: number;

    readonly aceModeList = acequire('ace/ext/modelist');

    ngOnInit(): void {
        ace.Range = ace.acequire('ace/range').Range;

        // Create a clone of the diff entries to prevent modifications to the original diff entries
        this.diffEntries = this.diffEntries.map((entry) => ({ ...entry }));

        this.determineFilePaths();
        this.createLineArrays();
        this.determineActualStartLine();
        this.determineEndLines();

        this.processEntries();

        this.actualEndLine = Math.min(Math.max(this.templateLines.length, this.solutionLines.length), Math.max(this.previousEndLine, this.endLine));

        this.setupEditor(this.editorPrevious);
        this.setupEditor(this.editorNow);
        this.renderTemplateFile();
        this.renderSolutionFile();
    }

    /**
     * Determines the previous and current file path of the current file
     * @private
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

    /**
     * Splits the content of the template and solution files into an array of lines
     * @private
     */
    private createLineArrays() {
        this.templateLines = this.templateFileContent?.split('\n') ?? [];
        this.solutionLines = this.solutionFileContent?.split('\n') ?? [];
        // Pop the last lines if they are empty, as these are irrelevant and not included in the diff entries
        if (this.templateLines.last() === '') {
            this.templateLines.pop();
        }
        if (this.solutionLines.last() === '') {
            this.solutionLines.pop();
        }
    }

    /**
     * Determines the first line that should be displayed.
     * Compares the previous and current start line and takes the minimum of both and offsets it by the number of context lines.
     * @private
     */
    private determineActualStartLine() {
        this.actualStartLine = Math.max(
            0,
            Math.min(
                ...[...this.diffEntries.map((entry) => entry.startLine ?? Number.MAX_VALUE), ...this.diffEntries.map((entry) => entry.previousStartLine ?? Number.MAX_VALUE)],
            ) -
                this.numberOfContextLines -
                1,
        );
    }

    /**
     * Determines the last line that should be displayed.
     * Compares the previous and current last line (extracted from the diff entries)
     * and takes the maximum of both and offsets it by the number of context lines.
     * @private
     */
    private determineEndLines() {
        this.previousEndLine = Math.max(...this.diffEntries.map((entry) => (entry.previousStartLine ?? 0) + (entry.previousLineCount ?? 0))) + this.numberOfContextLines - 1;
        this.endLine = Math.max(...this.diffEntries.map((entry) => (entry.startLine ?? 0) + (entry.lineCount ?? 0))) + this.numberOfContextLines - 1;
    }

    /**
     * Processes all git-diff entries by delegating to the appropriate processing method for each entry type.
     * @private
     */
    private processEntries() {
        this.diffEntries.forEach((entry) => {
            if (entry.previousStartLine && entry.previousLineCount && !entry.startLine && !entry.lineCount) {
                this.processEntryWithDeletion(entry);
            } else if (!entry.previousStartLine && !entry.previousLineCount && entry.startLine && entry.lineCount) {
                this.processEntryWithAddition(entry);
            } else if (entry.previousStartLine && entry.previousLineCount && entry.startLine && entry.lineCount) {
                this.processEntryWithChange(entry);
            }
        });
    }

    /**
     * Processes a git-diff entry with a deletion. Counterpart of processEntryWithAddition.
     * Adds empty lines to the solution file to match the number of lines that are deleted in the template file.
     * Also, accordingly offsets the start line of the entries that come after the added empty lines.
     * @private
     */
    private processEntryWithDeletion(entry: ProgrammingExerciseGitDiffEntry) {
        this.solutionLines = [
            ...this.solutionLines.slice(0, entry.previousStartLine! - 1),
            ...Array(entry.previousLineCount).fill(undefined),
            ...this.solutionLines.slice(entry.previousStartLine! - 1),
        ];
        this.endLine += entry.previousLineCount!;
        this.diffEntries
            .filter((entry2) => entry2.startLine && entry2.lineCount && entry2.startLine >= entry.previousStartLine!)
            .forEach((entry2) => {
                entry2.startLine! += entry.previousLineCount!;
            });
    }

    /**
     * Processes a git-diff entries with an addition. Counterpart of processEntryWithDeletion.
     * Adds empty lines to the template file to match the number of lines that are added in the solution file.
     * Also, accordingly offsets the start line of the entries that come after the added empty lines.
     * @private
     */
    private processEntryWithAddition(entry: ProgrammingExerciseGitDiffEntry) {
        this.templateLines = [...this.templateLines.slice(0, entry.startLine! - 1), ...Array(entry.lineCount).fill(undefined), ...this.templateLines.slice(entry.startLine! - 1)];
        this.previousEndLine += entry.lineCount!;
        this.diffEntries
            .filter((entry2) => entry2.previousStartLine && entry2.previousLineCount && entry2.previousStartLine >= entry.startLine!)
            .forEach((entry2) => {
                entry2.previousStartLine! += entry.lineCount!;
            });
    }

    /**
     * Processes a git-diff entry with a change (deletion and addition).
     * Adds empty lines to the template/solution file to match the number of lines that are added/removed in the solution/template file.
     * Also, accordingly offsets the start line of the entries that come after the added empty lines.
     * @private
     */
    private processEntryWithChange(entry: ProgrammingExerciseGitDiffEntry) {
        if (entry.previousLineCount! < entry.lineCount!) {
            // There are more added lines than deleted lines -> add empty lines to the template file
            this.templateLines = [
                ...this.templateLines.slice(0, entry.startLine! + entry.previousLineCount! - 1),
                ...Array(entry.lineCount! - entry.previousLineCount!).fill(undefined),
                ...this.templateLines.slice(entry.startLine! + entry.previousLineCount! - 1),
            ];
            this.diffEntries
                .filter((entry2) => entry2.previousStartLine && entry2.previousLineCount && entry2.previousStartLine > entry.startLine!)
                .forEach((entry2) => {
                    entry2.previousStartLine! += entry.lineCount! - entry.previousLineCount!;
                });
        } else {
            // There are more deleted lines than added lines -> add empty lines to the solution file
            this.solutionLines = [
                ...this.solutionLines.slice(0, entry.previousStartLine! + entry.lineCount! - 1),
                ...Array(entry.previousLineCount! - entry.lineCount!).fill(undefined),
                ...this.solutionLines.slice(entry.previousStartLine! + entry.lineCount! - 1),
            ];
            this.diffEntries
                .filter((entry2) => entry2.startLine && entry2.lineCount && entry2.startLine > entry.previousStartLine!)
                .forEach((entry2) => {
                    entry2.startLine! += entry.previousLineCount! - entry.lineCount!;
                });
        }
    }

    /**
     * Sets up an ace editor for the template or solution file.
     * @param editor The editor to set up.
     * @private
     */
    private setupEditor(editor: AceEditorComponent): void {
        editor.getEditor().setOptions({
            animatedScroll: true,
            maxLines: Infinity,
            showPrintMargin: false,
            readOnly: true,
            highlightActiveLine: false,
            highlightGutterLine: false,
        });
        editor.getEditor().renderer.$cursorLayer.element.style.display = 'none';
        const editorMode = this.aceModeList.getModeForPath(this.filePath ?? this.previousFilePath ?? '').name;
        editor.setMode(editorMode);
    }

    /**
     * Renders the content of the template file in the template editor.
     * Sets the content of the editor and colors the lines that were removed red.
     * All empty lines added in the processEntries methods are colored gray.
     * @private
     */
    private renderTemplateFile() {
        const session = this.editorPrevious.getEditor().getSession();
        session.setValue(
            this.templateLines
                .slice(this.actualStartLine, this.actualEndLine)
                .map((line) => line ?? '')
                .join('\n'),
        );

        Object.entries(session.getMarkers() ?? {}).forEach(([, v]) => session.removeMarker((v as any).id));

        // Adds the red coloring to the code and the gutter
        this.diffEntries
            .filter((entry) => entry.previousStartLine !== undefined && entry.previousLineCount !== undefined)
            .map((entry) => {
                const startRow = entry.previousStartLine! - this.actualStartLine - 1;
                const endRow = entry.previousStartLine! + entry.previousLineCount! - this.actualStartLine - 2;
                const range = new ace.Range(startRow, 0, endRow, 1);
                session.addMarker(range, 'removed-line', 'fullLine');
                for (let i = startRow; i <= endRow; i++) {
                    session.addGutterDecoration(i, 'removed-line-gutter');
                }
            });

        // Adds the gray coloring to the code and the gutter
        this.templateLines.forEach((line, index) => {
            if (line === undefined) {
                const actualLine = index - this.actualStartLine;
                const range = new ace.Range(actualLine, 0, actualLine, 1);
                session.addMarker(range, 'placeholder-line', 'fullLine');
                session.addGutterDecoration(actualLine, 'placeholder-line-gutter');
            }
        });

        // Copy the lines here, as otherwise they may be undefined in the gutter
        const templateLinesCopy = this.templateLines;
        const copyActualStartLine = this.actualStartLine;
        const copyActualEndLine = this.actualEndLine;
        let rowNumber: number;

        // Takes care of the correct numbering of the lines, as empty lines added by the processEntries methods are not counted
        session.gutterRenderer = {
            getWidth(session2: any, lastLineNumber: number, config: any) {
                return Math.max(
                    ...Array.from({ length: copyActualEndLine - copyActualStartLine }, (_, index) => index + 1).map((lineNumber) => {
                        return this.getText(session, lineNumber + copyActualStartLine - 1).toString().length * config.characterWidth;
                    }),
                );
            },
            getText(_: any, row: number): string | number {
                if (row === 0) {
                    rowNumber = copyActualStartLine + 1;
                }
                return templateLinesCopy[row + copyActualStartLine] !== undefined ? rowNumber++ : '';
            },
        } as any;
        this.editorPrevious.getEditor().resize();
    }

    /**
     * Renders the content of the solution file in the solution editor.
     * Sets the content of the editor and colors the lines that were added green.
     * All empty lines added in the processEntries methods are colored gray.
     * @private
     */
    private renderSolutionFile() {
        const session = this.editorNow.getEditor().getSession();
        session.setValue(
            this.solutionLines
                .slice(this.actualStartLine, this.actualEndLine)
                .map((line) => line ?? '')
                .join('\n'),
        );

        Object.entries(session.getMarkers() ?? {}).forEach(([, v]) => session.removeMarker((v as any).id));

        // Adds the red coloring to the code and the gutter
        this.diffEntries
            .filter((entry) => entry.startLine && entry.lineCount)
            .map((entry) => {
                const startRow = entry.startLine! - this.actualStartLine - 1;
                const endRow = entry.startLine! + entry.lineCount! - this.actualStartLine - 2;
                const range = new ace.Range(startRow, 0, endRow, 1);
                session.addMarker(range, 'added-line', 'fullLine');
                for (let i = startRow; i <= endRow; i++) {
                    session.addGutterDecoration(i, 'added-line-gutter');
                }
            });

        // Adds the gray coloring to the code and the gutter
        this.solutionLines.forEach((line, index) => {
            if (line === undefined) {
                const actualLine = index - this.actualStartLine;
                const range = new ace.Range(actualLine, 0, actualLine, 1);
                session.addMarker(range, 'placeholder-line', 'fullLine');
                session.addGutterDecoration(actualLine, 'placeholder-line-gutter');
            }
        });

        // Copy the lines here, as otherwise they may be undefined in the gutter
        const solutionLinesCopy = this.solutionLines;
        const copyActualStartLine = this.actualStartLine;
        const copyActualEndLine = this.actualEndLine;
        let rowNumber: number;

        // Takes care of the correct numbering of the lines, as empty lines added by the processEntries methods are not counted
        session.gutterRenderer = {
            getWidth(session2: any, lastLineNumber: number, config: any) {
                return Math.max(
                    ...Array.from({ length: copyActualEndLine - copyActualStartLine }, (_, index) => index + 1).map((lineNumber) => {
                        return this.getText(session, lineNumber + copyActualStartLine - 1).toString().length * config.characterWidth;
                    }),
                );
            },
            getText(_: any, row: number): string | number {
                if (row === 0) {
                    rowNumber = copyActualStartLine + 1;
                }
                return solutionLinesCopy[row + copyActualStartLine] !== undefined ? rowNumber++ : '';
            },
        };
        this.editorPrevious.getEditor().resize();
    }
}
