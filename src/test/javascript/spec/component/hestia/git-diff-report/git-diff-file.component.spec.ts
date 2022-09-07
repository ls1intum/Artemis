import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { GitDiffFileComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file.component';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import * as ace from 'brace';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';

function createDiffEntry(
    id: number,
    previousFilePath: string | undefined,
    filePath: string | undefined,
    previousStartLine: number | undefined,
    startLine: number | undefined,
    previousLineCount: number | undefined,
    lineCount: number | undefined,
): ProgrammingExerciseGitDiffEntry {
    const diffEntry = new ProgrammingExerciseGitDiffEntry();
    diffEntry.id = id;
    diffEntry.previousFilePath = previousFilePath;
    diffEntry.filePath = filePath;
    diffEntry.previousStartLine = previousStartLine;
    diffEntry.startLine = startLine;
    diffEntry.previousLineCount = previousLineCount;
    diffEntry.lineCount = lineCount;
    return diffEntry;
}

function createFileContent(lineCount: number): string {
    return new Array(lineCount)
        .fill('')
        .map((_, index) => `Line ${index + 1}`)
        .join('\n');
}

function insertEmptyLines(fileContent: string, startLine: number, lineCount: number): string {
    const lines = fileContent.split('\n');
    const insertIndex = startLine - 1;
    const insertLines = new Array(lineCount).fill('');
    return [...lines.slice(0, insertIndex), ...insertLines, ...lines.slice(insertIndex)].join('\n');
}

describe('ProgrammingExerciseGitDiffEntry Component', () => {
    ace.acequire('ace/ext/modelist');
    let comp: GitDiffFileComponent;
    let fixture: ComponentFixture<GitDiffFileComponent>;
    const singleAddition = {
        name: 'single-addition',
        diffEntries: [createDiffEntry(1, 'file1', 'file1', undefined, 4, undefined, 4)],
        templateFileContent: createFileContent(10),
        solutionFileContent: createFileContent(14),
        expectedTemplateFileContent: insertEmptyLines(createFileContent(10), 4, 4),
        expectedSolutionFileContent: createFileContent(14),
        actualStartLineWithoutOffset: 4,
        actualEndLineWithoutOffset: 8,
        coloringTemplate: ['placeholder', 'placeholder', 'placeholder', 'placeholder'],
        coloringSolution: ['addition', 'addition', 'addition', 'addition'],
        gutterWidthTemplate: 1,
        gutterWidthSolution: 2,
    };
    const singleDeletion = {
        name: 'single-deletion',
        diffEntries: [createDiffEntry(1, 'file1', 'file1', 4, undefined, 4, undefined)],
        templateFileContent: createFileContent(14),
        solutionFileContent: createFileContent(10),
        expectedTemplateFileContent: createFileContent(14),
        expectedSolutionFileContent: insertEmptyLines(createFileContent(10), 4, 4),
        actualStartLineWithoutOffset: 4,
        actualEndLineWithoutOffset: 8,
        coloringTemplate: ['deletion', 'deletion', 'deletion', 'deletion'],
        coloringSolution: ['placeholder', 'placeholder', 'placeholder', 'placeholder'],
        gutterWidthTemplate: 2,
        gutterWidthSolution: 1,
    };
    const singleChangeMoreAdded = {
        name: 'single-change-more-added',
        diffEntries: [createDiffEntry(1, 'file1', 'file1', 4, 4, 2, 4)],
        templateFileContent: createFileContent(10),
        solutionFileContent: createFileContent(12),
        expectedTemplateFileContent: insertEmptyLines(createFileContent(10), 6, 2),
        expectedSolutionFileContent: createFileContent(12),
        actualStartLineWithoutOffset: 4,
        actualEndLineWithoutOffset: 8,
        coloringTemplate: ['deletion', 'deletion', 'placeholder', 'placeholder'],
        coloringSolution: ['addition', 'addition', 'addition', 'addition'],
        gutterWidthTemplate: 1,
        gutterWidthSolution: 2,
    };
    const singleChangeMoreDeleted = {
        name: 'single-change-more-deleted',
        diffEntries: [createDiffEntry(1, 'file1', 'file1', 4, 4, 4, 2)],
        templateFileContent: createFileContent(12),
        solutionFileContent: createFileContent(10),
        expectedTemplateFileContent: createFileContent(12),
        expectedSolutionFileContent: insertEmptyLines(createFileContent(10), 6, 2),
        actualStartLineWithoutOffset: 4,
        actualEndLineWithoutOffset: 8,
        coloringTemplate: ['deletion', 'deletion', 'deletion', 'deletion'],
        coloringSolution: ['addition', 'addition', 'placeholder', 'placeholder'],
        gutterWidthTemplate: 2,
        gutterWidthSolution: 1,
    };
    const singleChangeEqual = {
        name: 'single-change-equal',
        diffEntries: [createDiffEntry(1, 'file1', 'file1', 4, 4, 4, 4)],
        templateFileContent: createFileContent(10) + '\n',
        solutionFileContent: createFileContent(10) + '\n',
        expectedTemplateFileContent: createFileContent(10),
        expectedSolutionFileContent: createFileContent(10),
        actualStartLineWithoutOffset: 4,
        actualEndLineWithoutOffset: 8,
        coloringTemplate: ['deletion', 'deletion', 'deletion', 'deletion'],
        coloringSolution: ['addition', 'addition', 'addition', 'addition'],
        gutterWidthTemplate: 2,
        gutterWidthSolution: 2,
    };
    const multipleChanges = {
        name: 'multiple-changes',
        diffEntries: [createDiffEntry(1, 'file1', 'file1', 1, 1, 1, 2), createDiffEntry(2, 'file1', 'file1', 3, 4, 2, 1), createDiffEntry(3, 'file1', 'file1', 6, 6, 1, 1)],
        templateFileContent: 'Line 1 (Changed A)\nLine 3\nLine 4 (Changed A)\nLine 5 (Removed)\nLine 6\nLine 7 (Changed A)\nLine 8\nLine 9\n',
        solutionFileContent: 'Line 1 (Changed B)\nLine 2 (Added)\nLine 3\nLine 4 (Changed B)\nLine 6\nLine 7 (Changed B)\nLine 8\nLine 9\n',
        expectedTemplateFileContent: 'Line 1 (Changed A)\n\nLine 3\nLine 4 (Changed A)\nLine 5 (Removed)\nLine 6\nLine 7 (Changed A)\nLine 8\nLine 9',
        expectedSolutionFileContent: 'Line 1 (Changed B)\nLine 2 (Added)\nLine 3\nLine 4 (Changed B)\n\nLine 6\nLine 7 (Changed B)\nLine 8\nLine 9',
        actualStartLineWithoutOffset: 1,
        actualEndLineWithoutOffset: 8,
        coloringTemplate: ['deletion', 'placeholder', undefined, 'deletion', 'deletion', undefined, 'deletion'],
        coloringSolution: ['addition', 'addition', undefined, 'addition', 'placeholder', undefined, 'addition'],
        gutterWidthTemplate: 1,
        gutterWidthSolution: 1,
    };

    const allCases = [singleAddition, singleDeletion, singleChangeMoreAdded, singleChangeMoreDeleted, singleChangeEqual, multipleChanges];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule],
            declarations: [GitDiffFileComponent, AceEditorComponent, CodeEditorAceComponent],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffFileComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each(allCases)('Check that the editor lines were created correctly for: $name', (testData) => {
        const diffEntriesCopy = testData.diffEntries.map((entry) => ({ ...entry }));
        comp.diffEntries = testData.diffEntries;
        comp.templateFileContent = testData.templateFileContent;
        comp.solutionFileContent = testData.solutionFileContent;
        comp.ngOnInit();

        // Check that the original diff entries were not modified
        expect(testData.diffEntries).toEqual(diffEntriesCopy);
        // Check that the empty lines were inserted correctly
        expect(comp.templateLines).toHaveLength(comp.solutionLines.length);
        expect(comp.templateLines.map((line) => line ?? '').join('\n')).toEqual(testData.expectedTemplateFileContent);
        expect(comp.solutionLines.map((line) => line ?? '').join('\n')).toEqual(testData.expectedSolutionFileContent);

        // Check that the actual start and end lines were calculated correctly
        expect(comp.actualStartLine).toEqual(Math.max(0, testData.actualStartLineWithoutOffset - 1 - 3));
        expect(comp.actualEndLine).toEqual(Math.min(comp.templateLines.length, testData.actualEndLineWithoutOffset - 1 + 3));

        // Check that the editor lines were set correctly
        expect(comp.editorPrevious.getEditor().getSession().getValue().split('\n')).toHaveLength(comp.actualEndLine - comp.actualStartLine);
        expect(comp.editorPrevious.getEditor().getSession().getValue().split('\n')).toEqual(
            comp.templateLines.slice(comp.actualStartLine, comp.actualEndLine).map((line) => line ?? ''),
        );
        expect(comp.editorNow.getEditor().getSession().getValue().split('\n')).toHaveLength(comp.actualEndLine - comp.actualStartLine);
        expect(comp.editorNow.getEditor().getSession().getValue().split('\n')).toEqual(
            comp.solutionLines.slice(comp.actualStartLine, comp.actualEndLine).map((line) => line ?? ''),
        );

        // Check that the editor lines were colored correctly
        const contextLines = new Array(testData.actualStartLineWithoutOffset - comp.actualStartLine - 1).fill(undefined);
        const expectedDecorationsTemplate = [
            ...contextLines,
            ...testData.coloringTemplate.map((type) => {
                if (type === 'addition') {
                    return ' added-line-gutter';
                } else if (type === 'deletion') {
                    return ' removed-line-gutter';
                } else if (type === 'placeholder') {
                    return ' placeholder-line-gutter';
                }
            }),
        ];
        const expectedDecorationsSolution = [
            ...contextLines,
            ...testData.coloringSolution.map((type) => {
                if (type === 'addition') {
                    return ' added-line-gutter';
                } else if (type === 'deletion') {
                    return ' removed-line-gutter';
                } else if (type === 'placeholder') {
                    return ' placeholder-line-gutter';
                }
            }),
        ];
        expect(
            comp.editorPrevious
                .getEditor()
                .getSession()
                .$decorations.map((s: string) => s.replace(' removed-line-gutter removed-line-gutter', ' removed-line-gutter'))
                .map((s: string) => s.replace(' placeholder-line-gutter placeholder-line-gutter', ' placeholder-line-gutter')),
        ).toEqual(expectedDecorationsTemplate);
        expect(
            comp.editorNow
                .getEditor()
                .getSession()
                .$decorations.map((s: string) => s.replace(' added-line-gutter added-line-gutter', ' added-line-gutter'))
                .map((s: string) => s.replace(' placeholder-line-gutter placeholder-line-gutter', ' placeholder-line-gutter')),
        ).toEqual(expectedDecorationsSolution);

        // Check that the gutter renderer works correctly
        const gutterRendererPrevious = comp.editorPrevious.getEditor().getSession().gutterRenderer;
        const gutterRendererNow = comp.editorNow.getEditor().getSession().gutterRenderer;
        let currentTemplateLine = comp.actualStartLine + 1;
        let currentSolutionLine = comp.actualStartLine + 1;
        for (let i = comp.actualStartLine; i < comp.actualEndLine; i++) {
            expect(gutterRendererPrevious.getText(0, i - comp.actualStartLine)).toBe(comp.templateLines[i] ? currentTemplateLine++ : '');
            expect(gutterRendererNow.getText(0, i - comp.actualStartLine)).toBe(comp.solutionLines[i] ? currentSolutionLine++ : '');
        }
        expect(gutterRendererPrevious.getWidth(null, comp.actualEndLine, { characterWidth: 1 })).toBe(testData.gutterWidthTemplate);
        expect(gutterRendererNow.getWidth(null, comp.actualEndLine, { characterWidth: 1 })).toBe(testData.gutterWidthSolution);
    });
});
