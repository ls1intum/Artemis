import { ArtemisTestModule } from '../../../test.module';
import { FullGitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/full-git-diff-report.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseFullGitDiffEntry } from 'app/entities/hestia/programming-exercise-full-git-diff-entry.model';
import { ProgrammingExerciseFullGitDiffReport } from 'app/entities/hestia/programming-exercise-full-git-diff-report.model';

describe('ProgrammingExerciseFullGitDiffReport Component', () => {
    let comp: FullGitDiffReportComponent;
    let fixture: ComponentFixture<FullGitDiffReportComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [FullGitDiffReportComponent],
            providers: [],
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
});
