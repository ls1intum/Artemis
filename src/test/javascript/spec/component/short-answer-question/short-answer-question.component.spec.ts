import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { QuizScoringInfoStudentModalComponent } from 'app/exercises/quiz/shared/questions/quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSpot, SpotType } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { By } from '@angular/platform-browser';

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
        const text = 'Please explain this question about [-spot 1]. There are [-spot-number 2] or [-spot-number 3] questions.';
        alternativeQuestion.text = text;
        alternativeQuestion.hint = 'new Hint!';
        alternativeQuestion.explanation = 'This is a very good explanation!';
        const spot1 = new ShortAnswerSpot();
        spot1.spotNr = 1;
        const spot2 = new ShortAnswerSpot();
        spot2.type = SpotType.NUMBER;
        spot2.spotNr = 2;
        const spot3 = new ShortAnswerSpot();
        spot3.type = SpotType.NUMBER;
        spot3.spotNr = 3;
        alternativeQuestion.spots = [spot1, spot2, spot3];
        component.fnOnSubmittedTextUpdate = function () {
            return true;
        };
        const getNavigationStub = jest
            .spyOn(document, 'getElementById')
            .mockReturnValueOnce({ value: text } as unknown as HTMLElement)
            .mockReturnValueOnce({ value: '10' } as unknown as HTMLElement)
            .mockReturnValueOnce({ value: 'test' } as unknown as HTMLElement);

        component.question = alternativeQuestion;
        component.setSubmittedText();

        expect(getNavigationStub).toHaveBeenCalledTimes(3);
        expect(component.submittedTexts).toHaveLength(3);
        expect(component.submittedTexts[0].text).toStrictEqual(text);
        expect(component.submittedTexts[0].spot).toStrictEqual(spot1);
        expect(component.submittedTexts[1].text).toStrictEqual('10');
        expect(component.submittedTexts[1].spot).toStrictEqual(spot2);
        expect(component.submittedTexts[2].text).toStrictEqual('');
        expect(component.submittedTexts[2].spot).toStrictEqual(spot3);
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

    it('should get spot type for spot', () => {
        const shortAnswerQuestion = new ShortAnswerQuestion();
        const spot1 = new ShortAnswerSpot();
        spot1.spotNr = 1;
        const spot2 = new ShortAnswerSpot();
        spot2.type = SpotType.TEXT;
        spot2.spotNr = 2;
        const spot3 = new ShortAnswerSpot();
        spot3.type = SpotType.NUMBER;
        spot3.spotNr = 3;
        shortAnswerQuestion.spots = [spot1, spot2, spot3];
        component.shortAnswerQuestion = shortAnswerQuestion;
        expect(component.getSpotType('[-spot 1]')).toEqual(SpotType.TEXT);
        expect(component.getSpotType('[-spot 2]')).toEqual(SpotType.TEXT);
        expect(component.getSpotType('[-spot-number 3]')).toEqual(SpotType.NUMBER);
    });

    it('should get spot input type for spot', () => {
        const shortAnswerQuestion = new ShortAnswerQuestion();
        const spot1 = new ShortAnswerSpot();
        spot1.spotNr = 1;
        const spot2 = new ShortAnswerSpot();
        spot2.type = SpotType.TEXT;
        spot2.spotNr = 2;
        const spot3 = new ShortAnswerSpot();
        spot3.type = SpotType.NUMBER;
        spot3.spotNr = 3;
        shortAnswerQuestion.spots = [spot1, spot2, spot3];
        component.shortAnswerQuestion = shortAnswerQuestion;
        expect(component.getSpotInputType('[-spot 1]')).toEqual('text');
        expect(component.getSpotInputType('[-spot 2]')).toEqual('text');
        expect(component.getSpotInputType('[-spot-number 3]')).toEqual('number');
    });

    it('should remove input if spot type is number and input is invalid', () => {
        const shortAnswerQuestion = new ShortAnswerQuestion();
        const spot1 = new ShortAnswerSpot();
        spot1.spotNr = 1;
        const spot2 = new ShortAnswerSpot();
        spot2.type = SpotType.NUMBER;
        spot2.spotNr = 2;
        const spot3 = new ShortAnswerSpot();
        spot3.type = SpotType.NUMBER;
        spot3.spotNr = 3;
        shortAnswerQuestion.id = 0;
        shortAnswerQuestion.spots = [spot1, spot2, spot3];
        component.shortAnswerQuestion = shortAnswerQuestion;
        component.textParts = [['[-spot 1]', '[-spot-number 2]', '[-spot-number 3]']];
        component.showResult = false;
        fixture.detectChanges();
        const input1 = fixture.debugElement.query(By.css('#solution-0-0-0')).nativeElement;
        input1.value = 'text';
        input1.dispatchEvent(new FocusEvent('blur'));
        expect(input1.value).toEqual('text');
        const input2 = fixture.debugElement.query(By.css('#solution-0-1-0')).nativeElement;
        input2.value = 'text';
        input2.dispatchEvent(new FocusEvent('blur'));
        expect(input2.value).toEqual('');
        const input3 = fixture.debugElement.query(By.css('#solution-0-2-0')).nativeElement;
        input3.value = '-1.2345';
        input3.dispatchEvent(new FocusEvent('blur'));
        expect(input3.value).toEqual('-1.2345');
    });
});
