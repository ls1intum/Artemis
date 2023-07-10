import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { QuizQuestionListEditComponent } from 'app/exercises/quiz/manage/quiz-question-list-edit.component';
import { CommonModule } from '@angular/common';
import { QuizQuestionListEditExistingComponent } from 'app/exercises/quiz/manage/quiz-question-list-edit-existing.component';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';

describe('QuizQuestionListEditComponent', () => {
    let fixture: ComponentFixture<QuizQuestionListEditComponent>;
    let component: QuizQuestionListEditComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CommonModule, ArtemisTestModule, HttpClientTestingModule],
            declarations: [
                QuizQuestionListEditComponent,
                MockComponent(QuizQuestionListEditExistingComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizQuestionListEditComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('should add multiple choice question to quizQuestions and emit onQuestionAdded Output', () => {
        const onQuestionAddedEmit = jest.spyOn(component.onQuestionAdded, 'emit');
        component.addMultipleChoiceQuestion();
        expect(component.quizQuestions).toBeArrayOfSize(1);
        expect(onQuestionAddedEmit).toHaveBeenCalledOnce();
    });

    it('should add drag and drop question to quizQuestions and emit onQuestionAdded Output', () => {
        const onQuestionAddedEmit = jest.spyOn(component.onQuestionAdded, 'emit');
        component.addDragAndDropQuestion();
        expect(component.quizQuestions).toBeArrayOfSize(1);
        expect(onQuestionAddedEmit).toHaveBeenCalledOnce();
    });

    it('should add short answer question to quizQuestions and emit onQuestionAdded Output', () => {
        const onQuestionAddedEmit = jest.spyOn(component.onQuestionAdded, 'emit');
        component.addShortAnswerQuestion();
        expect(component.quizQuestions).toBeArrayOfSize(1);
        expect(onQuestionAddedEmit).toHaveBeenCalledOnce();
    });

    it('should toggle show hide existing questions flag', () => {
        component.showHideExistingQuestions();
        expect(component.showExistingQuestions).toBeTrue();
        component.showHideExistingQuestions();
        expect(component.showExistingQuestions).toBeFalse();
    });

    it('should add existing quiz questions to quizQuestions and toggle show hide existing questions flag', () => {
        const question0 = new MultipleChoiceQuestion();
        const question1 = new ShortAnswerQuestion();
        component.showExistingQuestions = true;
        component.handleExistingQuestionsAdded([question0, question1]);
        expect(component.showExistingQuestions).toBeFalse();
        expect(component.quizQuestions).toBeArrayOfSize(2);
        expect(component.quizQuestions[0]).toEqual(question0);
        expect(component.quizQuestions[1]).toEqual(question1);
    });

    it('should emit onQuestionUpdated Output', () => {
        const onQuestionUpdatedEmit = jest.spyOn(component.onQuestionUpdated, 'emit');
        component.handleQuestionUpdated();
        expect(onQuestionUpdatedEmit).toHaveBeenCalledOnce();
    });

    it('should delete question and emit onQuestionUpdated Output', () => {
        const question0 = new MultipleChoiceQuestion();
        const question1 = new ShortAnswerQuestion();
        component.quizQuestions = [question0, question1];
        const onQuestionDeletedEmit = jest.spyOn(component.onQuestionDeleted, 'emit');
        component.handleQuestionDeleted(0);
        expect(onQuestionDeletedEmit).toHaveBeenCalledOnce();
        expect(component.quizQuestions).toBeArrayOfSize(1);
        expect(component.quizQuestions[0]).toEqual(question1);
    });
});
