import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { TranslatePipe } from '@ngx-translate/core';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { QuizScoringInfoStudentModalComponent } from 'app/exercises/quiz/shared/questions/quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { SafeHtml } from '@angular/platform-browser';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('MultipleChoiceQuestionComponent', () => {
    let fixture: ComponentFixture<MultipleChoiceQuestionComponent>;
    let component: MultipleChoiceQuestionComponent;
    let artemisMarkdownService: ArtemisMarkdownService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                MultipleChoiceQuestionComponent,
                MockPipe(TranslatePipe),
                MockDirective(NgbPopover),
                MockDirective(NgbTooltip),
                MockComponent(QuizScoringInfoStudentModalComponent),
            ],
            providers: [ArtemisMarkdownService],
        }).compileComponents();
        fixture = TestBed.createComponent(MultipleChoiceQuestionComponent);
        component = fixture.componentInstance;
        artemisMarkdownService = TestBed.inject(ArtemisMarkdownService);
    });

    beforeEach(() => {});

    afterEach(function () {
        sinon.restore();
    });

    it('should update rendered question and answer with html when question is set', () => {
        expect(component).to.be.ok;

        const question: MultipleChoiceQuestion = {
            id: 1,
            text: 'some-text',
            hint: 'some-hint',
            explanation: 'some-explanation',
            answerOptions: [{ id: 1, explanation: 'answer-explanation', hint: 'answer-hint', text: 'answer-text' }],
        };

        component.question = question;
        expect(component.renderedQuestion.text).to.eql(artemisMarkdownService.safeHtmlForMarkdown(question.text));
        expect(component.renderedQuestion.hint).to.eql(artemisMarkdownService.safeHtmlForMarkdown(question.hint));
        expect(component.renderedQuestion.explanation).to.eql(artemisMarkdownService.safeHtmlForMarkdown(question.explanation));
        expect(component.renderedQuestion.renderedSubElements.length).to.equal(1);

        const expectedAnswer = question.answerOptions![0];
        const renderedAnswer = component.renderedQuestion.renderedSubElements[0];
        expect(safeHtmlToString(renderedAnswer.text)).to.eql(toHtml(expectedAnswer.text!));
        expect(safeHtmlToString(renderedAnswer.hint)).to.eql(toHtml(expectedAnswer.hint!));
        expect(safeHtmlToString(renderedAnswer.explanation)).to.eql(toHtml(expectedAnswer.explanation!));
        expect(renderedAnswer.renderedSubElements.length).to.equal(0);
    });

    it('should update rendered question and answer with empty strings when question/answer values are undefined', () => {
        expect(component).to.be.ok;

        const question: MultipleChoiceQuestion = {
            text: 'some-text',
            hint: undefined,
            answerOptions: [{ explanation: 'answer-explanation', text: 'false' }],
        };

        component.question = question;
        expect(component.renderedQuestion.text).to.eql(artemisMarkdownService.safeHtmlForMarkdown(question.text));
        expect(component.renderedQuestion.hint).to.equal('');
        expect(component.renderedQuestion.explanation).to.equal('');
        expect(component.renderedQuestion.renderedSubElements.length).to.equal(1);

        const expectedAnswer = question.answerOptions![0];
        const renderedAnswer = component.renderedQuestion.renderedSubElements[0];
        expect(safeHtmlToString(renderedAnswer.explanation)).to.equal(toHtml(expectedAnswer.explanation!));
        expect(safeHtmlToString(renderedAnswer.text)).to.equal(toHtml(expectedAnswer.text!));
        expect(safeHtmlToString(renderedAnswer.hint)).to.equal('');
    });

    function toHtml(value: string) {
        return `<p>${value}</p>`;
    }

    function safeHtmlToString(safeHtml: SafeHtml) {
        return safeHtml['changingThisBreaksApplicationSecurity'] ?? '';
    }

    it('should return true is if the answer option was selected', function () {
        const answerOptions: AnswerOption[] = [{ id: 1 }, { id: 2 }, { id: 3 }];

        component.selectedAnswerOptions = [answerOptions[0], answerOptions[2]];
        expect(component.isAnswerOptionSelected(answerOptions[0])).to.be.true;
        expect(component.isAnswerOptionSelected(answerOptions[1])).to.be.false;
        expect(component.isAnswerOptionSelected(answerOptions[2])).to.be.true;
    });

    it('should not toggle anything on disabled click', function () {
        const answerOptions: AnswerOption[] = [{ id: 1 }, { id: 2 }, { id: 3 }];

        component.clickDisabled = true;
        component.selectedAnswerOptions = [];
        component.toggleSelection(answerOptions[1]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).to.be.false;
        expect(component.isAnswerOptionSelected(answerOptions[1])).to.be.false;
        expect(component.isAnswerOptionSelected(answerOptions[2])).to.be.false;
    });

    it('should toggle answer options', function () {
        const answerOptions: AnswerOption[] = [{ id: 1 }, { id: 2 }];

        component.selectedAnswerOptions = [];
        component.toggleSelection(answerOptions[1]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).to.be.false;
        expect(component.isAnswerOptionSelected(answerOptions[1])).to.be.true;

        // Re-toggle
        component.toggleSelection(answerOptions[1]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).to.be.false;
        expect(component.isAnswerOptionSelected(answerOptions[1])).to.be.false;

        component.toggleSelection(answerOptions[1]);
        component.toggleSelection(answerOptions[0]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).to.be.true;
        expect(component.isAnswerOptionSelected(answerOptions[1])).to.be.true;

        component.toggleSelection(answerOptions[0]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).to.be.false;
        expect(component.isAnswerOptionSelected(answerOptions[1])).to.be.true;

        component.toggleSelection(answerOptions[1]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).to.be.false;
        expect(component.isAnswerOptionSelected(answerOptions[1])).to.be.false;
    });
});
