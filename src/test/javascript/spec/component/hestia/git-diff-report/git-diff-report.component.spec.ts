import { ArtemisTestModule } from '../../../test.module';
import { GitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';

describe('ProgrammingExerciseGitDiffReport Component', () => {
    let comp: GitDiffReportComponent;
    let fixture: ComponentFixture<GitDiffReportComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [GitDiffReportComponent],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffReportComponent);
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
        ] as ProgrammingExerciseGitDiffEntry[];

        const expectedEntries = [
            { filePath: 'src/a.java', line: 1 },
            { filePath: 'src/a.java', line: 2 },
            { filePath: 'src/a.java', previousLine: 3 },
            { filePath: 'src/a.java', previousLine: 4 },
            { filePath: 'src/b.java', line: 1 },
            { filePath: 'src/b.java', line: 2 },
        ] as ProgrammingExerciseGitDiffEntry[];

        comp.report = { entries } as ProgrammingExerciseGitDiffReport;
        comp.ngOnInit();
        expect(comp.entries).toStrictEqual(expectedEntries);
    });
});
