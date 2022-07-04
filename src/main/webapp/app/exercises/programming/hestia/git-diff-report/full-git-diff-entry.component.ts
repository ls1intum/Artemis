import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { ProgrammingExerciseFullGitDiffEntry } from 'app/entities/hestia/programming-exercise-full-git-diff-entry.model';
import { Theme, ThemeService } from 'app/core/theme/theme.service';

@Component({
    selector: 'jhi-git-diff-entry',
    templateUrl: './full-git-diff-entry.component.html',
})
export class FullGitDiffEntryComponent implements OnInit {
    @ViewChild('editorPrevious', { static: true })
    editorPrevious: AceEditorComponent;
    @ViewChild('editorNow', { static: true })
    editorNow: AceEditorComponent;
    @Input()
    diffEntry: ProgrammingExerciseFullGitDiffEntry;
    addedLineCount: number;
    removedLineCount: number;

    constructor(private themeService: ThemeService) {}

    ngOnInit(): void {
        const isDark = this.themeService.getCurrentTheme() === Theme.DARK;
        this.setupEditor(this.editorPrevious, this.diffEntry.previousLine, this.diffEntry.previousCode ?? '', isDark ? 'rgba(248, 81, 73, 0.15)' : 'rgba(248, 81, 73, 0.5)');
        this.setupEditor(this.editorNow, this.diffEntry.line, this.diffEntry.code ?? '', isDark ? 'rgba(46, 160, 67, 0.15)' : 'rgba(63, 185, 80, 0.5)');
        this.addedLineCount = this.diffEntry.code?.split('\n').filter((line) => line && line.trim().length !== 0).length ?? 0;
        this.removedLineCount = this.diffEntry.previousCode?.split('\n').filter((line) => line && line.trim().length !== 0).length ?? 0;
    }

    /**
     * Sets up an ace editor for displaying one part of a git-diff entry
     *
     * @param editor The ace editor instance
     * @param line The starting line of the editor
     * @param code The code that should be in the editor
     * @param color The background color of the editor
     */
    private setupEditor(editor: AceEditorComponent, line: number | undefined, code: string, color: string) {
        editor.getEditor().setOptions({
            animatedScroll: true,
            maxLines: Infinity,
        });
        // Ensure that the line counter is according to the diff entry
        editor.getEditor().session.gutterRenderer = {
            getWidth(session: any, lastLineNumber: number, config: any) {
                return this.getText(session, lastLineNumber).toString().length * config.characterWidth;
            },
            getText(session: any, row: number): string | number {
                return line === undefined ? '' : row + line;
            },
        };
        editor.getEditor().getSession().setValue(code);
        editor.getEditor().resize();
        // Do not color the editor if there is no code
        if (code.length !== 0) {
            editor.getEditor().container.style.background = color;
        }
    }
}
