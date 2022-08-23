import { ArtemisTestModule } from '../../../test.module';
import { FullGitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/full-git-diff-report.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseFullGitDiffEntry } from 'app/entities/hestia/programming-exercise-full-git-diff-entry.model';
import { ProgrammingExerciseFullGitDiffReport } from 'app/entities/hestia/programming-exercise-full-git-diff-report.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { GitDiffLineStatComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';

describe('ProgrammingExerciseFullGitDiffReport Component', () => {
    let comp: FullGitDiffReportComponent;
    let fixture: ComponentFixture<FullGitDiffReportComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [FullGitDiffReportComponent, MockPipe(ArtemisTranslatePipe), MockComponent(GitDiffLineStatComponent)],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(FullGitDiffReportComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should sort entries', () => {
        const entries = [
            { filePath: 'src/a.java', previousLine: 3 },
            { filePath: 'src/b.java', line: 1 },
            { filePath: 'src/a.java', line: 2 },
            { filePath: 'src/a.java', previousLine: 4 },
            { filePath: 'src/b.java', line: 2 },
            { filePath: 'src/a.java', line: 1 },
        ] as ProgrammingExerciseFullGitDiffEntry[];

        const expectedEntries = [
            { filePath: 'src/a.java', line: 1 },
            { filePath: 'src/a.java', line: 2 },
            { filePath: 'src/a.java', previousLine: 3 },
            { filePath: 'src/a.java', previousLine: 4 },
            { filePath: 'src/b.java', line: 1 },
            { filePath: 'src/b.java', line: 2 },
        ] as ProgrammingExerciseFullGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseFullGitDiffReport;
        comp.ngOnInit();
        expect(comp.entries).toStrictEqual(expectedEntries);
    });

    it('should set added/removed lines to 1-0', () => {
        const entries = [{ filePath: 'src/a.java', code: 'Test' }] as ProgrammingExerciseFullGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseFullGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(1);
        expect(comp.removedLineCount).toBe(0);
    });

    it('should set added/removed lines to 4-1', () => {
        const entries = [
            {
                filePath: 'src/a.java',
                code: '1\n2\n3\n4',
                previousCode: '1',
            },
        ] as ProgrammingExerciseFullGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseFullGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(4);
        expect(comp.removedLineCount).toBe(1);
    });

    it('should set added/removed lines to 3-2', () => {
        const entries = [
            {
                filePath: 'src/a.java',
                code: '1\n2\n3',
                previousCode: '1\n2',
            },
        ] as ProgrammingExerciseFullGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseFullGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(3);
        expect(comp.removedLineCount).toBe(2);
    });

    it('should set added/removed lines to 2-3', () => {
        const entries = [
            {
                filePath: 'src/a.java',
                code: '1\n2',
                previousCode: '1\n2\n3',
            },
        ] as ProgrammingExerciseFullGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseFullGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(2);
        expect(comp.removedLineCount).toBe(3);
    });

    it('should set added/removed lines to 1-4', () => {
        const entries = [
            {
                filePath: 'src/a.java',
                code: '1',
                previousCode: '1\n2\n3\n4',
            },
        ] as ProgrammingExerciseFullGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseFullGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(1);
        expect(comp.removedLineCount).toBe(4);
    });

    it('should set added/removed lines to 0-1', () => {
        const entries = [{ filePath: 'src/a.java', previousCode: '1' }] as ProgrammingExerciseFullGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseFullGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(0);
        expect(comp.removedLineCount).toBe(1);
    });
});
