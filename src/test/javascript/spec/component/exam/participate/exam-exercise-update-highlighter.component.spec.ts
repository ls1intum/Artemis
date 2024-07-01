import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { BehaviorSubject } from 'rxjs';
import { ExamExerciseUpdate, ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';

describe('ExamExerciseUpdateHighlighterComponent', () => {
    let fixture: ComponentFixture<ExamExerciseUpdateHighlighterComponent>;
    let component: ExamExerciseUpdateHighlighterComponent;

    const examExerciseIdAndProblemStatementSourceMock = new BehaviorSubject<ExamExerciseUpdate>({ exerciseId: -1, problemStatement: 'initialProblemStatementValue' });
    const mockExamExerciseUpdateService = {
        currentExerciseIdAndProblemStatement: examExerciseIdAndProblemStatementSourceMock.asObservable(),
    };

    const oldProblemStatement = 'problem statement with errors';
    const updatedProblemStatement = 'new updated ProblemStatement';
    const textExerciseDummy = { id: 42, problemStatement: oldProblemStatement } as Exercise;
    beforeAll(() => {
        return TestBed.configureTestingModule({
            declarations: [MockPipe(ArtemisTranslatePipe), ExamExerciseUpdateHighlighterComponent],
            providers: [{ provide: ExamExerciseUpdateService, useValue: mockExamExerciseUpdateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamExerciseUpdateHighlighterComponent);
                component = fixture.componentInstance;

                component.exercise = textExerciseDummy;
                const exerciseId = component.exercise.id!;
                const update = { exerciseId, problemStatement: updatedProblemStatement };

                fixture.detectChanges();
                examExerciseIdAndProblemStatementSourceMock.next(update);
            });
    });

    it('should update problem statement', () => {
        const result = component.updatedProblemStatement;
        expect(result).toEqual(updatedProblemStatement);
        expect(result).not.toEqual(oldProblemStatement);
    });

    it('should highlight differences', () => {
        const result =
            '<p><del class="diffmod">problem</del><ins class="diffmod">new</ins> <del class="diffmod">statement</del><ins class="diffmod">updated</ins> <del class="diffmod">with errors</del><ins class="diffmod">ProblemStatement</ins></p>';
        expect(result).toEqual(component.updatedProblemStatementWithHighlightedDifferencesHTML);
    });

    it('should display different problem statement after toggle method is called', () => {
        const mouseEvent = new MouseEvent('click');
        const stopPropagationSpy = jest.spyOn(mouseEvent, 'stopPropagation');
        const problemStatementBeforeClick = htmlForMarkdown(component.exercise.problemStatement);
        expect(problemStatementBeforeClick).toEqual(component.updatedProblemStatementHTML);

        component.toggleHighlightedProblemStatement(mouseEvent);

        const problemStatementAfterClick = component.exercise.problemStatement;
        expect(problemStatementAfterClick).toEqual(updatedProblemStatement);
        expect(problemStatementAfterClick).not.toEqual(component.updatedProblemStatementWithHighlightedDifferencesHTML);
        expect(problemStatementAfterClick).not.toEqual(problemStatementBeforeClick);
        expect(stopPropagationSpy).toHaveBeenCalledOnce();
    });

    describe('ExamExerciseUpdateHighlighterComponent for programming exercises', () => {
        const programmingExerciseDummy = { id: 42, problemStatement: oldProblemStatement, type: ExerciseType.PROGRAMMING } as Exercise;
        beforeAll(() => {
            return TestBed.configureTestingModule({
                declarations: [MockPipe(ArtemisTranslatePipe), ExamExerciseUpdateHighlighterComponent],
                providers: [{ provide: ExamExerciseUpdateService, useValue: mockExamExerciseUpdateService }],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(ExamExerciseUpdateHighlighterComponent);
                    component = fixture.componentInstance;

                    component.exercise = programmingExerciseDummy;
                    const exerciseId = component.exercise.id!;
                    const update = { exerciseId, problemStatement: updatedProblemStatement };

                    fixture.detectChanges();
                    examExerciseIdAndProblemStatementSourceMock.next(update);
                });
        });
        it('should not highlight differences for programming exercise', () => {
            // For programming exercises, the highlighting of differences is handled in the programming-exercise-instruction.component.ts.
            // Therefore, the highlightProblemStatementDifferences method is not called and updatedProblemStatementWithHighlightedDifferencesHTML
            // and updatedProblemStatementHTML remain undefined
            const highlightDifferencesSpy = jest.spyOn(component, 'highlightProblemStatementDifferences');
            expect(highlightDifferencesSpy).not.toHaveBeenCalled();
            expect(component.updatedProblemStatementWithHighlightedDifferencesHTML).toBeUndefined();
            expect(component.updatedProblemStatementHTML).toBeUndefined();
        });
    });
});
