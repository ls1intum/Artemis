import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { GitDiffFilePanelComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel.component';
import { GitDiffLineStatComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';

describe('ProgrammingExerciseGitDiffFilePanel Component', () => {
    let comp: GitDiffFilePanelComponent;
    let fixture: ComponentFixture<GitDiffFilePanelComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [GitDiffFilePanelComponent, MockPipe(ArtemisTranslatePipe), MockComponent(GitDiffLineStatComponent)],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffFilePanelComponent);
        comp = fixture.componentInstance;
        comp.templateFileContent = 'L1\nL2\nL3\nL4\nL5\nL6\nL7\nL8\nL9\nL10\nL11\nL12\nL13\nL14\nL15\nL16';
        comp.solutionFileContent = 'L1\nL2\nL3\nL4\nL5\nL6\nL7\nL8\nL9\nL10\nL11\nL12\nL13\nL14\nL15\nL16';
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Should extract file path', () => {
        comp.diffEntries = [{ filePath: 'src/a.java', previousFilePath: 'src/b.java' }] as ProgrammingExerciseGitDiffEntry[];
        comp.ngOnInit();
        expect(comp.filePath).toBe('src/a.java');
        expect(comp.previousFilePath).toBe('src/b.java');
    });

    it('Should set added/removed lines to 1-0', () => {
        comp.diffEntries = [{ filePath: 'src/a.java', startLine: 1, lineCount: 1 }] as ProgrammingExerciseGitDiffEntry[];
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(1);
        expect(comp.removedLineCount).toBe(0);
    });

    it('Should set added/removed lines to 4-1', () => {
        comp.diffEntries = [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 4,
                previousStartLine: 5,
                previousLineCount: 1,
            },
        ] as ProgrammingExerciseGitDiffEntry[];
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(4);
        expect(comp.removedLineCount).toBe(1);
    });

    it('Should set added/removed lines to 3-2', () => {
        comp.diffEntries = [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 3,
                previousStartLine: 5,
                previousLineCount: 2,
            },
        ] as ProgrammingExerciseGitDiffEntry[];
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(3);
        expect(comp.removedLineCount).toBe(2);
    });

    it('Should set added/removed lines to 2-3', () => {
        comp.diffEntries = [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 2,
                previousStartLine: 5,
                previousLineCount: 3,
            },
        ] as ProgrammingExerciseGitDiffEntry[];
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(2);
        expect(comp.removedLineCount).toBe(3);
    });

    it('Should set added/removed lines to 1-4', () => {
        comp.diffEntries = [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 1,
                previousStartLine: 5,
                previousLineCount: 4,
            },
        ] as ProgrammingExerciseGitDiffEntry[];
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(1);
        expect(comp.removedLineCount).toBe(4);
    });

    it('Should set added/removed lines to 0-1', () => {
        comp.diffEntries = [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                previousStartLine: 1,
                previousLineCount: 1,
            },
        ] as ProgrammingExerciseGitDiffEntry[];
        comp.ngOnInit();
        expect(comp.addedLineCount).toBe(0);
        expect(comp.removedLineCount).toBe(1);
    });
});
