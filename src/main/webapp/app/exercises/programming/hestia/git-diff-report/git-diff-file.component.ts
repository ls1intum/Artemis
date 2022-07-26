import { Component, Input, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { ThemeService } from 'app/core/theme/theme.service';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import ace, { acequire, Editor } from 'brace';

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

    constructor(private themeService: ThemeService) {}

    ngOnInit(): void {
        ace.Range = ace.acequire('ace/range').Range;

        this.filePath = this.diffEntries
            .map((entry) => entry.filePath)
            .filter((filePath) => filePath)
            .first();

        this.previousFilePath = this.diffEntries
            .map((entry) => entry.previousFilePath)
            .filter((filePath) => filePath)
            .first();

        this.templateLines = this.templateFileContent?.split('\n') ?? [];
        this.solutionLines = this.solutionFileContent?.split('\n') ?? [];
        this.solutionLines.pop();
        this.templateLines.pop();

        this.actualStartLine = Math.max(
            0,
            Math.min(
                ...[...this.diffEntries.map((entry) => entry.startLine ?? Number.MAX_VALUE), ...this.diffEntries.map((entry) => entry.previousStartLine ?? Number.MAX_VALUE)],
            ) -
                this.numberOfContextLines -
                1,
        );

        this.previousEndLine = Math.max(...this.diffEntries.map((entry) => (entry.previousStartLine ?? 0) + (entry.previousLineCount ?? 0))) + this.numberOfContextLines - 1;
        this.endLine = Math.max(...this.diffEntries.map((entry) => (entry.startLine ?? 0) + (entry.lineCount ?? 0))) + this.numberOfContextLines - 1;

        this.diffEntries
            .filter((entry) => entry.previousStartLine && entry.previousLineCount && !entry.startLine && !entry.lineCount)
            .forEach((entry) => {
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
            });

        this.diffEntries
            .filter((entry) => !entry.previousStartLine && !entry.previousLineCount && entry.startLine && entry.lineCount)
            .forEach((entry) => {
                this.templateLines = [...this.templateLines.slice(0, entry.startLine!), ...Array(entry.lineCount).fill(undefined), ...this.templateLines.slice(entry.startLine!)];
                this.previousEndLine += entry.lineCount!;
                this.diffEntries
                    .filter((entry2) => entry2.previousStartLine && entry2.previousLineCount && entry2.previousStartLine >= entry.startLine!)
                    .forEach((entry2) => {
                        entry2.previousStartLine! += entry.lineCount!;
                    });
            });

        this.diffEntries
            .filter((entry) => entry.previousStartLine && entry.previousLineCount && entry.startLine && entry.lineCount)
            .forEach((entry) => {
                if (entry.previousLineCount! < entry.lineCount!) {
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
            });

        this.actualEndLine = Math.min(Math.max(this.templateLines.length, this.solutionLines.length), Math.max(this.previousEndLine, this.endLine));

        this.solutionFileContent = this.solutionLines
            .slice(this.actualStartLine, this.actualEndLine)
            .map((line) => line ?? '')
            .join('\n');
        this.templateFileContent = this.templateLines
            .slice(this.actualStartLine, this.actualEndLine)
            .map((line) => line ?? '')
            .join('\n');

        this.setupEditor(this.editorPrevious);
        this.setupEditor(this.editorNow);
        this.renderTemplateFile();
        this.renderSolutionFile();
    }

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

    private renderTemplateFile() {
        const session = this.editorPrevious.getEditor().getSession();
        session.setValue(this.templateFileContent ?? '');

        Object.entries(session.getMarkers() ?? {}).forEach(([, v]) => session.removeMarker((v as any).id));

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
        let rowNumber: number;

        session.gutterRenderer = {
            getWidth(session2: any, lastLineNumber: number, config: any) {
                return Math.max(
                    ...Array.from({ length: templateLinesCopy.length }, (_, index) => index + 1).map((lineNumber) => {
                        return this.getText(session, lineNumber).toString().length * config.characterWidth;
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

    private renderSolutionFile() {
        const session = this.editorNow.getEditor().getSession();
        session.setValue(this.solutionFileContent ?? '');

        Object.entries(session.getMarkers() ?? {}).forEach(([, v]) => session.removeMarker((v as any).id));

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
        let rowNumber: number;

        session.gutterRenderer = {
            getWidth(session2: any, lastLineNumber: number, config: any) {
                return Math.max(
                    ...Array.from({ length: solutionLinesCopy.length }, (_, index) => index + 1).map((lineNumber) => {
                        return this.getText(session, lineNumber).toString().length * config.characterWidth;
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
