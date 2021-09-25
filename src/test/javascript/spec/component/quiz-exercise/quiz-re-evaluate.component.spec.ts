import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { Course } from 'app/entities/course.model';
import { QuizReEvaluateComponent } from 'app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { QuizExercisePopupService } from 'app/exercises/quiz/manage/quiz-exercise-popup.service';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { restore, SinonStub, spy, stub } from 'sinon';
import { ReEvaluateMultipleChoiceQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/multiple-choice-question/re-evaluate-multiple-choice-question.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ReEvaluateDragAndDropQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.component';
import { ReEvaluateShortAnswerQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/short-answer-question/re-evaluate-short-answer-question.component';
import { NgModel } from '@angular/forms';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { Duration } from 'app/exercises/quiz/manage/quiz-exercise-interfaces';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { SimpleChange } from '@angular/core';
import { IncludedInOverallScorePickerComponent } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';

chai.use(sinonChai);
const expect = chai.expect;

describe('QuizExercise Re-evaluate Component', () => {
    let comp: QuizReEvaluateComponent;
    let fixture: ComponentFixture<QuizReEvaluateComponent>;
    let quizService: QuizExerciseService;
    let quizServiceFindSpy: SinonStub;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'MyQuiz';

    const route = { params: of({ exerciseId: 123 }) } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                NgModel,
                QuizReEvaluateComponent,
                MockComponent(ReEvaluateMultipleChoiceQuestionComponent),
                MockComponent(ReEvaluateDragAndDropQuestionComponent),
                MockComponent(ReEvaluateShortAnswerQuestionComponent),
                MockTranslateValuesDirective,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(IncludedInOverallScorePickerComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                NgbModal,
                QuizExercisePopupService,
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizReEvaluateComponent);
        comp = fixture.componentInstance;
        quizService = fixture.debugElement.injector.get(QuizExerciseService);
        const quizQuestion1 = new MultipleChoiceQuestion();
        const quizQuestion2 = new DragAndDropQuestion();
        quizExercise.quizQuestions = [quizQuestion1, quizQuestion2];
        quizServiceFindSpy = stub(quizService, 'find').returns(of(new HttpResponse({ body: quizExercise })));
    });

    afterEach(() => {
        restore();
    });

    it('Should initialize quiz exercise', () => {
        comp.ngOnInit();
        expect(comp.validQuiz()).to.be.true;
        expect(comp.quizExercise).to.equal(quizExercise);
        expect(quizServiceFindSpy).to.have.been.calledOnce;
    });

    it('Should create correct duration strings', () => {
        comp.duration = new Duration(1, 0);
        expect(comp.durationString()).to.equal('1:00');

        comp.duration = new Duration(1, 9);
        expect(comp.durationString()).to.equal('1:09');

        comp.duration = new Duration(1, 10);
        expect(comp.durationString()).to.equal('1:10');
    });

    it('Should delete quiz question', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.quizQuestions!.length).to.equal(2);
        comp.deleteQuestion(comp.quizExercise.quizQuestions![0]);
        expect(comp.quizExercise.quizQuestions!.length).to.equal(1);
    });

    it('Should update and reset quiz questions', () => {
        comp.ngOnInit();
        comp.quizExercise.title = 'New Title';
        comp.quizExercise.quizQuestions![0].points = 5;
        // update question
        comp.onQuestionUpdated();
        expect(comp.quizExercise).to.equal(quizExercise);
        // reset title
        comp.resetQuizTitle();
        expect(comp.quizExercise.title).to.equal(comp.backupQuiz.title);
        // reset all
        comp.resetAll();
        expect(comp.quizExercise).to.deep.equal(comp.backupQuiz);
    });

    it('Should have pending changes', () => {
        comp.ngOnInit();
        comp.quizExercise.quizQuestions![0].points = 5;
        expect(comp.pendingChanges()).to.be.true;
    });

    it('Should move down the quiz question', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.quizQuestions![0].type).to.equal(QuizQuestionType.MULTIPLE_CHOICE);
        comp.moveDown(comp.quizExercise.quizQuestions![0]);
        expect(comp.quizExercise.quizQuestions![1].type).to.equal(QuizQuestionType.MULTIPLE_CHOICE);
    });

    it('Should move up the quiz question', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.quizQuestions![1].type).to.equal(QuizQuestionType.DRAG_AND_DROP);
        comp.moveUp(comp.quizExercise.quizQuestions![1]);
        expect(comp.quizExercise.quizQuestions![0].type).to.equal(QuizQuestionType.DRAG_AND_DROP);
    });

    it('Updates quiz on changes', () => {
        const prepareEntitySpy = spy(comp, 'prepareEntity');
        comp.ngOnInit();
        comp.ngOnChanges({
            quizExercise: { currentValue: quizExercise } as SimpleChange,
        });

        expect(prepareEntitySpy).to.have.been.called;
    });

    it('Should save a quiz', () => {
        comp.ngOnInit();
    });

    it('Should change score calculation type', () => {
        comp.ngOnInit();
        expect(comp.quizExercise.includedInOverallScore).to.equal(IncludedInOverallScore.INCLUDED_COMPLETELY);
        comp.includedInOverallScoreChange(IncludedInOverallScore.INCLUDED_AS_BONUS);
        expect(comp.quizExercise.includedInOverallScore).to.equal(IncludedInOverallScore.INCLUDED_AS_BONUS);
    });
});
