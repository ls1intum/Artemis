import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { QuizScoringInfoStudentModalComponent } from 'app/exercises/quiz/shared/questions/quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';

const question = new ShortAnswerQuestion();
question.id = 1;

describe('ShortAnswerQuestionComponent', () => {
    let fixture: ComponentFixture<ShortAnswerQuestionComponent>;
    let component: ShortAnswerQuestionComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                ShortAnswerQuestionComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(QuizScoringInfoStudentModalComponent),
                MockComponent(FaIconComponent),
                MockDirective(NgbPopover),
                MockDirective(NgbTooltip),
            ],
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
        component.shortAnswerQuestion = question;
    });

    afterEach(() => {
        jest.restoreAllMocks();
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

        expect(component.textParts).toStrictEqual([[`<p>${text}</p>`]]);
        expect(component.shortAnswerQuestion).toStrictEqual(alternativeQuestion);
        expect(component.renderedQuestion.text['changingThisBreaksApplicationSecurity']).toBe(`<p>${text}</p>`);
        expect(component.renderedQuestion.hint['changingThisBreaksApplicationSecurity']).toBe(`<p>${hint}</p>`);
        expect(component.renderedQuestion.explanation['changingThisBreaksApplicationSecurity']).toBe(`<p>${explanation}</p>`);
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
        const returnValue = { value: text } as unknown as HTMLElement;
        const getNavigationStub = jest.spyOn(document, 'getElementById').mockReturnValue(returnValue);

        component.question = alternativeQuestion;
        component.setSubmittedText();

        expect(getNavigationStub).toHaveBeenCalledOnce();
        expect(component.submittedTexts).toHaveLength(1);
        expect(component.submittedTexts[0].text).toStrictEqual(text);
        expect(component.submittedTexts[0].spot).toStrictEqual(spot);
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

        component.shortAnswerQuestion = alternativeQuestion;
        component.showSampleSolution();

        expect(component.sampleSolutions).toHaveLength(1);
        expect(component.sampleSolutions[0]).toStrictEqual(solution);
        expect(component.showingSampleSolution).toBeTrue();
    });

    it('should toggle show sample solution', () => {
        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.spots = [];
        component.shortAnswerQuestion = alternativeQuestion;
        component.showResult = true;
        component.showingSampleSolution = true;
        component.forceSampleSolution = true;
        expect(component.showingSampleSolution).toBeTrue();
        component.forceSampleSolution = false;
        component.hideSampleSolution();

        expect(component.showingSampleSolution).toBeFalse();
    });

    it('should get submitted text size for spot', () => {
        const submitted = new ShortAnswerSubmittedText();
        submitted.text = 'expectedReturnText';
        const spot = new ShortAnswerSpot();
        spot.spotNr = 1;
        submitted.spot = spot;
        const tag = '[-spot 1]';
        component.submittedTexts = [submitted];

        expect(component.getSubmittedTextSizeForSpot(tag)).toBe(submitted.text.length + 2);
    });

    it('should get sample solution size for spot', () => {
        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.id = 10;
        const spot = new ShortAnswerSpot();
        spot.spotNr = 1;
        alternativeQuestion.spots = [new ShortAnswerSpot(), spot];
        component.shortAnswerQuestion = alternativeQuestion;

        const solution = new ShortAnswerSolution();
        solution.text = 'expectedReturnText';
        component.sampleSolutions = [new ShortAnswerSolution(), solution];
        const tag = '[-spot 1]';

        expect(component.getSampleSolutionSizeForSpot(tag)).toBe(solution.text.length + 2);
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

        component.shortAnswerQuestion = alternativeQuestion;
        expect(component.classifyInputField(tag)).toBe('completely-correct');
        submittedText.text += '!';
        expect(component.classifyInputField(tag)).toBe('correct');
        component.shortAnswerQuestion.correctMappings = [];
        expect(component.classifyInputField(tag)).toBe('correct');
        component.submittedTexts = [];
        expect(component.classifyInputField(tag)).toBe('wrong');
        spot.invalid = true;
        expect(component.classifyInputField(tag)).toBe('invalid');
        spot.invalid = false;
        alternativeQuestion.invalid = true;
        expect(component.classifyInputField(tag)).toBe('invalid');
    });
});
