import { ArtemisTestModule } from '../../../test.module';
import { FullGitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/full-git-diff-report.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseFullGitDiffEntry } from 'app/entities/hestia/programming-exercise-full-git-diff-entry.model';
import { ProgrammingExerciseFullGitDiffReport } from 'app/entities/hestia/programming-exercise-full-git-diff-report.model';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';

describe('ProgrammingExerciseFullGitDiffReport Component', () => {
    let comp: FullGitDiffReportComponent;
    let fixture: ComponentFixture<FullGitDiffReportComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [FullGitDiffReportComponent],
            providers: [{ provide: ActivatedRoute, useValue: new MockActivatedRoute() }],
        }).compileComponents();
        fixture = TestBed.createComponent(FullGitDiffReportComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Should sort entries', () => {
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

    it('Should show 5-0 boxes', () => {
        const entries = [{ filePath: 'src/a.java', code: 'Test' }] as ProgrammingExerciseFullGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseFullGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(1);
        expect(comp.removedLineCount).toBe(0);
    });

    it('Should show 4-1 boxes', () => {
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

    it('Should show 3-2 boxes', () => {
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

    it('Should show 2-3 boxes', () => {
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

    it('Should show 1-4 boxes', () => {
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

    it('Should show 0-5 boxes', () => {
        const entries = [{ filePath: 'src/a.java', previousCode: '1' }] as ProgrammingExerciseFullGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseFullGitDiffReport;
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(0);
        expect(comp.removedLineCount).toBe(1);
    });
});
