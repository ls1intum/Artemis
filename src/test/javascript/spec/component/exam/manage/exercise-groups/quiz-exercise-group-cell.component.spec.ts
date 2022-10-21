import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { ExerciseType } from 'app/entities/exercise.model';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { QuizExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

describe('Quiz Exercise Group Cell Component', () => {
    let comp: QuizExerciseGroupCellComponent;
    let fixture: ComponentFixture<QuizExerciseGroupCellComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [QuizExerciseGroupCellComponent, TranslatePipeMock],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizExerciseGroupCellComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should display number of quiz questions', () => {
        comp.exercise = {
            id: 1,
            type: ExerciseType.QUIZ,
            quizQuestions: [{}, {}],
        } as any as QuizExercise;

        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('2');
    });

    it('should not display anything for other exercise types', () => {
        comp.exercise = {
            id: 1,
            type: ExerciseType.TEXT,
            quizQuestions: [{}, {}],
        } as any as QuizExercise;

        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toBe('');
    });
});
