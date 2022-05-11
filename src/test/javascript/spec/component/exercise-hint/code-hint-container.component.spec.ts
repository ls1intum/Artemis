import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { CodeHintContainerComponent } from 'app/exercises/shared/exercise-hint/shared/code-hint-container.component';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';

describe('ExerciseHint Management Component', () => {
    let comp: CodeHintContainerComponent;
    let fixture: ComponentFixture<CodeHintContainerComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CodeHintContainerComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeHintContainerComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should sort solution entries by file name', () => {
        const solutionEntry1 = new ProgrammingExerciseSolutionEntry();
        solutionEntry1.line = 1;
        solutionEntry1.filePath = 'b';
        const solutionEntry2 = new ProgrammingExerciseSolutionEntry();
        solutionEntry2.line = 1;
        solutionEntry2.filePath = 'a';
        const codeHint = new CodeHint();
        codeHint.solutionEntries = [solutionEntry1, solutionEntry2];

        comp.codeHint = codeHint;

        comp.ngOnInit();
        expect(comp.sortedSolutionEntries).toEqual([solutionEntry2, solutionEntry1]);
    });

    it('should sort solution entries for same file name but different line', () => {
        const solutionEntry1 = new ProgrammingExerciseSolutionEntry();
        solutionEntry1.line = 3;
        solutionEntry1.filePath = 'a';
        const solutionEntry2 = new ProgrammingExerciseSolutionEntry();
        solutionEntry2.line = 2;
        solutionEntry2.filePath = 'a';
        const codeHint = new CodeHint();
        codeHint.solutionEntries = [solutionEntry1, solutionEntry2];

        comp.codeHint = codeHint;

        comp.ngOnInit();
        expect(comp.sortedSolutionEntries).toEqual([solutionEntry2, solutionEntry1]);
    });
});
