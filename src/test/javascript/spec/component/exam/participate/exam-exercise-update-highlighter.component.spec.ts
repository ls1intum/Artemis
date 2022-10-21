import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Exercise } from 'app/entities/exercise.model';
import { ExamExerciseUpdate, ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { BehaviorSubject } from 'rxjs';

describe('ExamExerciseUpdateHighlighterComponent', () => {
    let fixture: ComponentFixture<ExamExerciseUpdateHighlighterComponent>;
    let component: ExamExerciseUpdateHighlighterComponent;

    const examExerciseIdAndProblemStatementSourceMock = new BehaviorSubject<ExamExerciseUpdate>({ exerciseId: -1, problemStatement: 'initialProblemStatementValue' });
    const mockExamExerciseUpdateService = {
        currentExerciseIdAndProblemStatement: examExerciseIdAndProblemStatementSourceMock.asObservable(),
    };

    const oldProblemStatement = 'problem statement with errors';
    const updatedProblemStatement = 'new updated ProblemStatement';
    const exerciseDummy = { id: 42, problemStatement: oldProblemStatement } as Exercise;

    beforeAll(() => {
        return TestBed.configureTestingModule({
            declarations: [MockPipe(ArtemisTranslatePipe), ExamExerciseUpdateHighlighterComponent],
            providers: [{ provide: ExamExerciseUpdateService, useValue: mockExamExerciseUpdateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamExerciseUpdateHighlighterComponent);
                component = fixture.componentInstance;

                component.exercise = exerciseDummy;
                const exerciseId = component.exercise.id!;
                const update = { exerciseId, problemStatement: updatedProblemStatement };

                fixture.detectChanges();
                examExerciseIdAndProblemStatementSourceMock.next(update);
            });
    });

    it('should update problem statement', () => {
        // not component.exercise.problemStatement, due to inserted HTML via Diff-Highlighting
        const result = component.updatedProblemStatement;
        expect(result).toEqual(updatedProblemStatement);
        expect(result).not.toEqual(oldProblemStatement);
    });

    it('should highlight differences', () => {
        const result = component.exercise.problemStatement;
        expect(result).toEqual(component.updatedProblemStatementWithHighlightedDifferences);
    });

    it('should display different problem statement after toggle method is called', () => {
        const problemStatementBeforeClick = component.exercise.problemStatement;
        expect(problemStatementBeforeClick).toEqual(component.updatedProblemStatementWithHighlightedDifferences);

        component.toggleHighlightedProblemStatement();

        const problemStatementAfterClick = component.exercise.problemStatement;
        expect(problemStatementAfterClick).toEqual(updatedProblemStatement);
        expect(problemStatementAfterClick).not.toEqual(component.updatedProblemStatementWithHighlightedDifferences);
        expect(problemStatementAfterClick).not.toEqual(problemStatementBeforeClick);
    });
});
