import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { GitDiffLineStatComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { GitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';

describe('ProgrammingExerciseGitDiffReport Component', () => {
    let comp: GitDiffReportComponent;
    let fixture: ComponentFixture<GitDiffReportComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [GitDiffReportComponent, MockPipe(ArtemisTranslatePipe), MockComponent(GitDiffLineStatComponent)],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffReportComponent);
        comp = fixture.componentInstance;
        comp.templateFileContentByPath = new Map<string, string>([['src/a.java', 'L1\nL2\nL3\nL4\nL5\nL6\nL7\nL8\nL9\nL10\nL11\nL12\nL13\nL14\nL15\nL16']]);
        comp.solutionFileContentByPath = new Map<string, string>([['src/a.java', 'L1\nL2\nL3\nL4\nL5\nL6\nL7\nL8\nL9\nL10\nL11\nL12\nL13\nL14\nL15\nL16']]);
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

        comp.report = { entries } as ProgrammingExerciseGitDiffReport;
        comp.ngOnInit();
        expect(comp.entries).toStrictEqual(expectedEntries);
    });

    it('Should set added/removed lines to 1-0', () => {
        const entries = [{ filePath: 'src/a.java', previousFilePath: 'src/a.java', startLine: 1, lineCount: 1 }] as ProgrammingExerciseGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(1);
        expect(comp.removedLineCount).toBe(0);
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

        comp.report = { entries } as ProgrammingExerciseGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(4);
        expect(comp.removedLineCount).toBe(1);
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

        comp.report = { entries } as ProgrammingExerciseGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(3);
        expect(comp.removedLineCount).toBe(2);
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

        comp.report = { entries } as ProgrammingExerciseGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(2);
        expect(comp.removedLineCount).toBe(3);
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

        comp.report = { entries } as ProgrammingExerciseGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(1);
        expect(comp.removedLineCount).toBe(4);
    });

    it('Should set added/removed lines to 0-1', () => {
        const entries = [{ filePath: 'src/a.java', previousFilePath: 'src/a.java', previousStartLine: 1, previousLineCount: 1 }] as ProgrammingExerciseGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(0);
        expect(comp.removedLineCount).toBe(1);
    });
});
