import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { GitDiffLineStatComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { GitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.component';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { GitDiffFilePanelComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel.component';

describe('ProgrammingExerciseGitDiffReport Component', () => {
    let comp: GitDiffReportComponent;
    let fixture: ComponentFixture<GitDiffReportComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule],
            declarations: [GitDiffReportComponent, MockPipe(ArtemisTranslatePipe), MockComponent(GitDiffFilePanelComponent), MockComponent(GitDiffLineStatComponent)],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffReportComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput(
            'templateFileContentByPath',
            new Map<string, string>([['src/a.java', 'L1\nL2\nL3\nL4\nL5\nL6\nL7\nL8\nL9\nL10\nL11\nL12\nL13\nL14\nL15\nL16']]),
        );
        fixture.componentRef.setInput(
            'solutionFileContentByPath',
            new Map<string, string>([['src/a.java', 'L1\nL2\nL3\nL4\nL5\nL6\nL7\nL8\nL9\nL10\nL11\nL12\nL13\nL14\nL15\nL16']]),
        );
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Should sort entries', () => {
        const entries = [
            { filePath: 'src/a.java', previousStartLine: 3 },
            { filePath: 'src/b.java', startLine: 1 },
            { filePath: 'src/a.java', startLine: 2 },
            { filePath: 'src/a.java', previousStartLine: 4 },
            { filePath: 'src/b.java', startLine: 2 },
            { filePath: 'src/a.java', startLine: 1 },
        ] as ProgrammingExerciseGitDiffEntry[];

        const expectedEntries = [
            { filePath: 'src/a.java', startLine: 1 },
            { filePath: 'src/a.java', startLine: 2 },
            { filePath: 'src/a.java', previousStartLine: 3 },
            { filePath: 'src/a.java', previousStartLine: 4 },
            { filePath: 'src/b.java', startLine: 1 },
            { filePath: 'src/b.java', startLine: 2 },
        ] as ProgrammingExerciseGitDiffEntry[];

        fixture.componentRef.setInput('report', { entries } as ProgrammingExerciseGitDiffReport);
        fixture.detectChanges();
        expect(comp.entries()).toStrictEqual(expectedEntries);
    });

    it('Should set added/removed lines to 1-0', () => {
        const entries = [{ filePath: 'src/a.java', previousFilePath: 'src/a.java', startLine: 1, lineCount: 1 }] as ProgrammingExerciseGitDiffEntry[];

        fixture.componentRef.setInput('report', { entries } as ProgrammingExerciseGitDiffReport);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(1);
        expect(comp.removedLineCount()).toBe(0);
    });

    it('Should set added/removed lines to 4-1', () => {
        const entries = [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 4,
                previousStartLine: 5,
                previousLineCount: 1,
            },
        ] as ProgrammingExerciseGitDiffEntry[];

        fixture.componentRef.setInput('report', { entries } as ProgrammingExerciseGitDiffReport);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(4);
        expect(comp.removedLineCount()).toBe(1);
    });

    it('Should set added/removed lines to 3-2', () => {
        const entries = [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 3,
                previousStartLine: 5,
                previousLineCount: 2,
            },
        ] as ProgrammingExerciseGitDiffEntry[];

        fixture.componentRef.setInput('report', { entries } as ProgrammingExerciseGitDiffReport);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(3);
        expect(comp.removedLineCount()).toBe(2);
    });

    it('Should set added/removed lines to 2-3', () => {
        const entries = [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 2,
                previousStartLine: 5,
                previousLineCount: 3,
            },
        ] as ProgrammingExerciseGitDiffEntry[];

        fixture.componentRef.setInput('report', { entries } as ProgrammingExerciseGitDiffReport);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(2);
        expect(comp.removedLineCount()).toBe(3);
    });

    it('Should set added/removed lines to 1-4', () => {
        const entries = [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 1,
                previousStartLine: 5,
                previousLineCount: 4,
            },
        ] as ProgrammingExerciseGitDiffEntry[];

        fixture.componentRef.setInput('report', { entries } as ProgrammingExerciseGitDiffReport);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(1);
        expect(comp.removedLineCount()).toBe(4);
    });

    it('Should set added/removed lines to 0-1', () => {
        const entries = [{ filePath: 'src/a.java', previousFilePath: 'src/a.java', previousStartLine: 1, previousLineCount: 1 }] as ProgrammingExerciseGitDiffEntry[];

        fixture.componentRef.setInput('report', { entries } as ProgrammingExerciseGitDiffReport);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(0);
        expect(comp.removedLineCount()).toBe(1);
    });

    it('should record for each path whether the diff is ready', () => {
        const filePath1 = 'src/a.java';
        const filePath2 = 'src/b.java';
        const entries: ProgrammingExerciseGitDiffEntry[] = [
            { filePath: 'src/a.java', previousStartLine: 3 },
            { filePath: 'src/b.java', startLine: 1 },
            { filePath: 'src/a.java', startLine: 2 },
            { filePath: 'src/a.java', previousStartLine: 4 },
            { filePath: 'src/b.java', startLine: 2 },
            { filePath: 'src/a.java', startLine: 1 },
        ];
        fixture.componentRef.setInput(
            'solutionFileContentByPath',
            new Map<string, string>([
                ['src/a.java', 'some file content'],
                ['src/b.java', 'some other file content'],
            ]),
        );
        fixture.componentRef.setInput('templateFileContentByPath', comp.solutionFileContentByPath());
        fixture.componentRef.setInput('report', { entries } as ProgrammingExerciseGitDiffReport);
        fixture.detectChanges();
        // Initialization
        expect(comp.allDiffsReady()).toBeFalse();
        expect(comp.diffInformationForPaths()[0].diffReady).toBeFalse();
        expect(comp.diffInformationForPaths()[1].diffReady).toBeFalse();
        // First file ready
        comp.onDiffReady(filePath1, true);
        expect(comp.allDiffsReady()).toBeFalse();
        expect(comp.diffInformationForPaths()[0].diffReady).toBeTrue();
        expect(comp.diffInformationForPaths()[1].diffReady).toBeFalse();
        // Second file ready
        comp.onDiffReady(filePath2, true);
        expect(comp.allDiffsReady()).toBeTrue();
        expect(comp.diffInformationForPaths()[0].diffReady).toBeTrue();
        expect(comp.diffInformationForPaths()[1].diffReady).toBeTrue();
    });

    it('should correctly identify renamed files', () => {
        const originalFilePath1 = 'src/original-a.java';
        const originalFilePath2 = 'src/original-b.java';
        const renamedFilePath1 = 'src/renamed-without-changes.java';
        const renamedFilePath2 = 'src/renamed-with-changes.java';
        const notRenamedFilePath = 'src/not-renamed.java';
        const entries: ProgrammingExerciseGitDiffEntry[] = [
            { filePath: renamedFilePath1, previousFilePath: originalFilePath1 },
            { filePath: renamedFilePath2, previousFilePath: originalFilePath2, startLine: 1 },
            { filePath: notRenamedFilePath, previousFilePath: notRenamedFilePath, startLine: 1 },
        ];
        const defaultContent = 'some content that might change';
        const modifiedContent = 'some content that has changed';
        fixture.componentRef.setInput('report', { entries } as ProgrammingExerciseGitDiffReport);
        const originalFileContents = new Map<string, string>();
        const modifiedFileContents = new Map<string, string>();
        [originalFilePath1, originalFilePath2, notRenamedFilePath].forEach((path) => {
            originalFileContents.set(path, defaultContent);
            modifiedFileContents.set(path, path === originalFilePath1 ? defaultContent : modifiedContent);
        });
        fixture.componentRef.setInput('templateFileContentByPath', originalFileContents);
        fixture.componentRef.setInput('solutionFileContentByPath', modifiedFileContents);

        fixture.detectChanges();

        expect(comp.renamedFilePaths()).toEqual({ [renamedFilePath1]: originalFilePath1, [renamedFilePath2]: originalFilePath2 });
    });
});
