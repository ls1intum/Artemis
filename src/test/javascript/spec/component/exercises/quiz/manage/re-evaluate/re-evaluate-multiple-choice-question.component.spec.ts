import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import * as chai from 'chai';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../../../../test.module';
import { ReEvaluateMultipleChoiceQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/multiple-choice-question/re-evaluate-multiple-choice-question.component';
import { MultipleChoiceQuestionEditComponent } from 'app/exercises/quiz/manage/multiple-choice-question/multiple-choice-question-edit.component';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { NgModel } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortableComponent } from 'ng2-dnd';
import { Directive, Input } from '@angular/core';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { IncorrectOptionCommand } from 'app/shared/markdown-editor/domainCommands/incorrectOptionCommand';

chai.use(sinonChai);
const expect = chai.expect;

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[sortableData]' })
export class MockSortableDataDirective {
    @Input('sortableData') data: any;
}

describe('ReEvaluateMultipleChoiceQuestionComponent', () => {
    let fixture: ComponentFixture<ReEvaluateMultipleChoiceQuestionComponent>;
    let component: ReEvaluateMultipleChoiceQuestionComponent;

    const answer1 = { id: 1 } as AnswerOption;
    const answerBackup = { id: 1, text: 'backup' } as AnswerOption;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ReEvaluateMultipleChoiceQuestionComponent,
                MockComponent(MultipleChoiceQuestionEditComponent),
                MockComponent(MarkdownEditorComponent),
                MockDirective(NgModel),
                MockDirective(MockSortableDataDirective),
                MockComponent(SortableComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ReEvaluateMultipleChoiceQuestionComponent);
                component = fixture.componentInstance;

                // Provide the @Inputs
                const question = {
                    title: 'Test Question',
                    answerOptions: [answer1],
                } as MultipleChoiceQuestion;
                const backupQuestion = {
                    title: 'Backup Test Question',
                    text: 'Backup Text',
                    explanation: 'Backup Explanation',
                    hint: 'Backup Hint',
                    answerOptions: [answerBackup],
                } as MultipleChoiceQuestion;

                component.question = question;
                component.backupQuestion = backupQuestion;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    describe('should react to button presses', () => {
        it('move-up', () => {
            const emitSpy = sinon.spy(component.questionMoveUp, 'emit');
            const button = fixture.debugElement.nativeElement.querySelector('#move-up-button');
            expect(button).to.exist;

            button.click();
            fixture.detectChanges();

            expect(emitSpy).to.have.been.called;
        });

        it('move-down', () => {
            const emitSpy = sinon.spy(component.questionMoveDown, 'emit');
            const button = fixture.debugElement.nativeElement.querySelector('#move-down-button');
            expect(button).to.exist;

            button.click();
            fixture.detectChanges();

            expect(emitSpy).to.have.been.called;
        });

        it('delete', () => {
            const emitSpy = sinon.spy(component.questionDeleted, 'emit');
            const button = fixture.debugElement.nativeElement.querySelector('#delete-button');
            expect(button).to.exist;

            button.click();
            fixture.detectChanges();

            expect(emitSpy).to.have.been.called;
        });

        it('reset', () => {
            const button = fixture.debugElement.nativeElement.querySelector('#reset-button');
            expect(button).to.exist;

            button.click();
            fixture.detectChanges();

            expect(component.question.title).to.equal(component.backupQuestion.title);
            expect(component.question.text).to.equal(component.backupQuestion.text);
            expect(component.question.explanation).to.equal(component.backupQuestion.explanation);
            expect(component.question.hint).to.equal(component.backupQuestion.hint);
            expect(component.question.answerOptions).to.deep.equal(component.backupQuestion.answerOptions);
        });
    });

    it('should delete an answer', () => {
        component.deleteAnswer(answer1);
        fixture.detectChanges();

        expect(component.question.answerOptions!.length).to.equal(0);
    });

    it('should reset an answer', () => {
        component.resetAnswer(answer1);
        fixture.detectChanges();

        expect(component.question.answerOptions).to.deep.equal(component.backupQuestion.answerOptions);
    });

    it('should invalidate answers', () => {
        component.setAnswerInvalid(answer1);
        fixture.detectChanges();

        expect(component.question.answerOptions!.length).to.equal(1);

        const answer = component.question.answerOptions![0];
        expect(answer.invalid).to.be.true;
        expect(component.isAnswerInvalid(answer)).to.be.true;
    });

    it('should react to answer option changes', () => {
        const markdownSpy = sinon.spy(ArtemisMarkdownService, 'parseTextHintExplanation');

        component.onAnswerOptionChange('solution[wrong]answer', answer1);
        fixture.detectChanges();

        expect(markdownSpy).to.have.been.calledOnce;
        expect(component.question.answerOptions!.length).to.equal(1);

        const answer = component.question.answerOptions![0];
        expect(answer.isCorrect).to.be.false;
    });

    it('should generate answer markdown', () => {
        const generatedText = 'explanation';
        const markdownService = TestBed.inject(ArtemisMarkdownService);
        const markdownStub = sinon.stub(markdownService, 'generateTextHintExplanation').returns(generatedText);

        const result = component.generateAnswerMarkdown(answer1);
        fixture.detectChanges();

        expect(markdownStub).to.have.been.called;
        expect(result).to.equal(IncorrectOptionCommand.identifier + ' ' + generatedText);
    });

    it('should react to question changes', () => {
        const questionText = 'new text';
        const markdownSpy = sinon.spy(ArtemisMarkdownService, 'parseTextHintExplanation');

        component.onQuestionChange(questionText);
        fixture.detectChanges();

        expect(markdownSpy).to.have.been.called;
    });

    it('should get question text', () => {
        const fakeText = 'fake';
        const markdownService = TestBed.inject(ArtemisMarkdownService);
        const markdownStub = sinon.stub(markdownService, 'generateTextHintExplanation').returns(fakeText);

        const text = component.getQuestionText(component.question);
        fixture.detectChanges();

        expect(markdownStub).to.have.been.called;
        expect(text).to.equal(fakeText);
    });
});
