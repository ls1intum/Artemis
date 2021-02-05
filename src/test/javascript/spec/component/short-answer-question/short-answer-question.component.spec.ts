import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslatePipe } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { NgbPopoverModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { QuizScoringInfoStudentModalComponent } from 'app/exercises/quiz/shared/questions/quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { stub } from 'sinon';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';

chai.use(sinonChai);
const expect = chai.expect;

const question = new ShortAnswerQuestion();
question.id = 1;

describe('ShortAnswerQuestionComponent', () => {
    let fixture: ComponentFixture<ShortAnswerQuestionComponent>;
    let component: ShortAnswerQuestionComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, NgbPopoverModule, ArtemisMarkdownEditorModule],
            declarations: [ShortAnswerQuestionComponent, MockPipe(TranslatePipe), MockComponent(QuizScoringInfoStudentModalComponent), MockDirective(NgbTooltip)],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(ShortAnswerQuestionComponent);
        component = fixture.componentInstance;
    });

    beforeEach(() => {
        component.submittedTexts = [];
        component.clickDisabled = false;
        component.showResult = true;
        component.questionIndex = 0;
        component.score = 0;
        component._question = question;
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.id = 10;
        const text = 'Please explain this question about stuff';
        alternativeQuestion.text = text;
        const hint = 'new Hint!';
        alternativeQuestion.hint = hint;
        const explanation = 'This is a very good explanation!';
        alternativeQuestion.explanation = explanation;

        component.question = alternativeQuestion;

        expect(component.textParts).to.deep.equal([[`<p>${text}</p>`]]);
        expect(component._question).to.deep.equal(alternativeQuestion);
        expect(component.renderedQuestion.text['changingThisBreaksApplicationSecurity']).to.equal(`<p>${text}</p>`);
        expect(component.renderedQuestion.hint['changingThisBreaksApplicationSecurity']).to.equal(`<p>${hint}</p>`);
        expect(component.renderedQuestion.explanation['changingThisBreaksApplicationSecurity']).to.equal(`<p>${explanation}</p>`);
    });

    it('should set submitted texts', () => {
        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.id = 10;
        const text = 'Please explain this question about [-spot 1]';
        alternativeQuestion.text = text;
        alternativeQuestion.hint = 'new Hint!';
        alternativeQuestion.explanation = 'This is a very good explanation!';
        const spot = new ShortAnswerSpot();
        spot.spotNr = 1;
        alternativeQuestion.spots = [spot];
        component.fnOnSubmittedTextUpdate = function () {
            return true;
        };
        const returnValue = ({ value: text } as unknown) as HTMLElement;
        const getNavigationStub = stub(document, 'getElementById').returns(returnValue);

        component.question = alternativeQuestion;
        component.setSubmittedText();

        expect(getNavigationStub).to.have.been.called;
        expect(component.submittedTexts.length).to.equal(1);
        expect(component.submittedTexts[0].text).to.equal(text);
        expect(component.submittedTexts[0].spot).to.deep.equal(spot);
    });

    it('should show sample solution', () => {
        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.id = 10;
        alternativeQuestion.text = 'Please explain this question about stuff';
        alternativeQuestion.hint = 'new Hint!';
        alternativeQuestion.explanation = 'This is a very good explanation!';
        const spot = new ShortAnswerSpot();
        spot.spotNr = 1;
        alternativeQuestion.spots = [spot];
        const solution = new ShortAnswerSolution();
        const mapping = new ShortAnswerMapping(spot, solution);
        alternativeQuestion.correctMappings = [mapping];

        component.question = alternativeQuestion;
        component.showSampleSolution();

        expect(component.sampleSolutions.length).to.equal(1);
        expect(component.sampleSolutions[0]).to.deep.equal(solution);
        expect(component.showingSampleSolution).to.be.true;
    });

    it('should toggle show sample solution', () => {
        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.spots = [];
        component._question = alternativeQuestion;
        component.showResult = true;
        component.showingSampleSolution = true;
        component.forceSampleSolution = true;
        expect(component.showingSampleSolution).to.be.true;
        component.forceSampleSolution = false;
        component.hideSampleSolution();

        expect(component.showingSampleSolution).to.be.false;
    });

    it('should get submitted text size for spot', () => {
        const submitted = new ShortAnswerSubmittedText();
        submitted.text = 'expectedReturnText';
        const spot = new ShortAnswerSpot();
        spot.spotNr = 1;
        submitted.spot = spot;
        const tag = '[-spot 1]';
        component.submittedTexts = [submitted];

        expect(component.getSubmittedTextSizeForSpot(tag)).to.deep.equal(submitted.text.length + 2);
    });

    it('should get sample solution size for spot', () => {
        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.id = 10;
        const spot = new ShortAnswerSpot();
        spot.spotNr = 1;
        alternativeQuestion.spots = [new ShortAnswerSpot(), spot];
        component._question = alternativeQuestion;

        const solution = new ShortAnswerSolution();
        solution.text = 'expectedReturnText';
        component.sampleSolutions = [new ShortAnswerSolution(), solution];
        const tag = '[-spot 1]';

        expect(component.getSampleSolutionSizeForSpot(tag)).to.deep.equal(solution.text.length + 2);
    });

    it('should get background color for input field', () => {
        const text = 'This is the solution text for the ultimate solution';
        const submittedText = new ShortAnswerSubmittedText();
        const spot = new ShortAnswerSpot();
        spot.spotNr = 1;
        submittedText.spot = spot;
        submittedText.isCorrect = true;
        submittedText.text = text;
        component.submittedTexts = [submittedText];
        const tag = '[-spot 1]';

        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.id = 10;
        alternativeQuestion.spots = [spot];
        const solution = new ShortAnswerSolution();
        solution.id = 1;
        solution.text = text;
        const mapping = new ShortAnswerMapping(spot, solution);
        alternativeQuestion.correctMappings = [mapping];

        component._question = alternativeQuestion;
        expect(component.getBackgroundColourForInputField(tag)).to.equal('lightgreen');
        component._question.correctMappings = [];
        expect(component.getBackgroundColourForInputField(tag)).to.equal('yellow');
        component.submittedTexts = [];
        expect(component.getBackgroundColourForInputField(tag)).to.equal('red');
    });
});
