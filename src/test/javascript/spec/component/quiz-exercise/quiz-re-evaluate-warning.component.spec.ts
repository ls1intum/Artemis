import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ArtemisTestModule } from '../../test.module';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { QuizReEvaluateWarningComponent } from 'app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate-warning.component';

describe('QuizExercise Re-evaluate Warning Component', () => {
    let comp: QuizReEvaluateWarningComponent;
    let fixture: ComponentFixture<QuizReEvaluateWarningComponent>;
    let quizService: QuizExerciseService;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'MyQuiz';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizReEvaluateWarningComponent);
        comp = fixture.componentInstance;
        quizService = fixture.debugElement.injector.get(QuizExerciseService);
        const quizQuestion1 = new MultipleChoiceQuestion();
        const quizQuestion2 = new DragAndDropQuestion();
        quizExercise.quizQuestions = [quizQuestion1, quizQuestion2];

        // initial value
        comp.quizExercise = quizExercise;
        jest.spyOn(quizService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize quiz exercise', () => {
        comp.ngOnInit();
        expect(comp.backUpQuiz).toEqual(quizExercise);
    });
});
