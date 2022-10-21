import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ReEvaluateDragAndDropQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.component';
import { ReEvaluateMultipleChoiceQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/multiple-choice-question/re-evaluate-multiple-choice-question.component';
import { QuizReEvaluateWarningComponent } from 'app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate-warning.component';
import { ReEvaluateShortAnswerQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/short-answer-question/re-evaluate-short-answer-question.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

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
            declarations: [
                NgModel,
                QuizReEvaluateWarningComponent,
                MockComponent(ReEvaluateMultipleChoiceQuestionComponent),
                MockComponent(ReEvaluateDragAndDropQuestionComponent),
                MockComponent(ReEvaluateShortAnswerQuestionComponent),
                MockTranslateValuesDirective,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [NgbModal, NgbActiveModal, { provide: TranslateService, useClass: MockTranslateService }],
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
