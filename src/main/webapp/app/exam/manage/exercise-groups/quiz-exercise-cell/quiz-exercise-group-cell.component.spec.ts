import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';

describe('Quiz Exercise Group Cell Component', () => {
    let fixture: ComponentFixture<QuizExerciseGroupCellComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();
        fixture = TestBed.createComponent(QuizExerciseGroupCellComponent);
    });

    it('should display number of quiz questions', () => {
        const exercise: QuizExercise = {
            id: 1,
            type: ExerciseType.QUIZ,
            quizQuestions: [{}, {}],
        } as any as QuizExercise;
        fixture.componentRef.setInput('exercise', exercise);

        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('2');
    });

    it('should not display anything for other exercise types', () => {
        const exercise: QuizExercise = {
            id: 1,
            type: ExerciseType.TEXT,
            quizQuestions: [{}, {}],
        } as any as QuizExercise;
        fixture.componentRef.setInput('exercise', exercise);

        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toBe('');
    });
});
