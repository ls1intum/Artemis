import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { ExerciseType } from 'app/entities/exercise.model';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { QuizExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

describe('Quiz Exercise Group Cell Component', () => {
    let fixture: ComponentFixture<QuizExerciseGroupCellComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TranslatePipeMock],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizExerciseGroupCellComponent);
            });
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
