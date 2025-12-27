import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { QuizScoringInfoStudentModalComponent } from 'app/quiz/shared/questions/quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerMapping } from 'app/quiz/shared/entities/short-answer-mapping.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { SafeHtml } from '@angular/platform-browser';

const question = new ShortAnswerQuestion();
question.id = 1;

describe('ShortAnswerQuestionComponent', () => {
    let fixture: ComponentFixture<ShortAnswerQuestionComponent>;
    let component: ShortAnswerQuestionComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FontAwesomeModule],
            declarations: [ShortAnswerQuestionComponent, MockPipe(ArtemisTranslatePipe), MockComponent(QuizScoringInfoStudentModalComponent)],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(ShortAnswerQuestionComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('question', question);
        fixture.componentRef.setInput('submittedTexts', []);
        fixture.componentRef.setInput('clickDisabled', false);
        fixture.componentRef.setInput('showResult', true);
        fixture.componentRef.setInput('questionIndex', 0);
        fixture.componentRef.setInput('score', 0);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const extractSafeHtmlText = (safeHtml: SafeHtml) => {
            return 'changingThisBreaksApplicationSecurity' in safeHtml ? safeHtml.changingThisBreaksApplicationSecurity : '';
        };
        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.id = 10;
        const text = 'Please explain this question about stuff';
        alternativeQuestion.text = text;
        const hint = 'new Hint!';
        alternativeQuestion.hint = hint;
        const explanation = 'This is a very good explanation!';
        alternativeQuestion.explanation = explanation;
        jest.spyOn(component, 'hideSampleSolution');

        fixture.componentRef.setInput('question', alternativeQuestion);
        fixture.detectChanges();

        expect(component.textParts).toStrictEqual([[`<p>${text}</p>`]]);
        expect(component.shortAnswerQuestion()).toStrictEqual(alternativeQuestion);
        expect(extractSafeHtmlText(component.renderedQuestion.text)).toBe(`<p>${text}</p>`);
        expect(extractSafeHtmlText(component.renderedQuestion.hint)).toBe(`<p>${hint}</p>`);
        expect(extractSafeHtmlText(component.renderedQuestion.explanation)).toBe(`<p>${explanation}</p>`);
        expect(component.hideSampleSolution).toHaveBeenCalledOnce();
        expect(component.showingSampleSolution()).toBeFalsy();
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
        fixture.componentRef.setInput('fnOnSubmittedTextUpdate', () => {
            return true;
        });
        const returnValue = { value: text } as unknown as HTMLElement;
        const getNavigationStub = jest.spyOn(document, 'getElementById').mockReturnValue(returnValue);

        fixture.componentRef.setInput('question', alternativeQuestion);
        fixture.componentRef.setInput('submittedTexts', []);
        fixture.detectChanges();

        const sub = component.submittedTextsChange.subscribe((v) => {
            fixture.componentRef.setInput('submittedTexts', v ?? []);
            fixture.changeDetectorRef.detectChanges();
        });

        component.setSubmittedText();

        expect(getNavigationStub).toHaveBeenCalledOnce();
        expect(component.submittedTexts()).toHaveLength(1);
        expect(component.submittedTexts()[0].text).toStrictEqual(text);
        expect(component.submittedTexts()[0].spot).toStrictEqual(spot);
        sub.unsubscribe();
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

        fixture.componentRef.setInput('question', alternativeQuestion);
        fixture.detectChanges();
        component.showSampleSolution();

        expect(component.sampleSolutions).toHaveLength(1);
        expect(component.sampleSolutions[0]).toStrictEqual(solution);
        expect(component.showingSampleSolution()).toBeTrue();
    });

    it('should toggle show sample solution', () => {
        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.text = 'Some text';
        alternativeQuestion.spots = [];

        fixture.componentRef.setInput('question', alternativeQuestion);
        fixture.componentRef.setInput('showResult', true);
        component.showingSampleSolution.set(true);

        fixture.componentRef.setInput('forceSampleSolution', false);
        fixture.changeDetectorRef.detectChanges();
        fixture.componentRef.setInput('forceSampleSolution', true);
        fixture.changeDetectorRef.detectChanges();
        expect(component.showingSampleSolution()).toBeTruthy();

        fixture.componentRef.setInput('forceSampleSolution', false);
        fixture.changeDetectorRef.detectChanges();
        component.hideSampleSolution();

        expect(component.showingSampleSolution()).toBeFalse();
    });

    it('should get submitted text size for spot', () => {
        const submitted = new ShortAnswerSubmittedText();
        question.text = 'Some text';
        submitted.text = 'expectedReturnText';
        const spot = new ShortAnswerSpot();
        spot.spotNr = 1;
        submitted.spot = spot;
        const tag = '[-spot 1]';

        fixture.componentRef.setInput('submittedTexts', [submitted]);
        fixture.changeDetectorRef.detectChanges();

        expect(component.getSubmittedTextSizeForSpot(tag)).toBe(submitted.text.length + 2);
    });

    it('should get sample solution size for spot', () => {
        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.text = 'Some text';
        alternativeQuestion.id = 10;
        const spot = new ShortAnswerSpot();
        spot.spotNr = 1;
        alternativeQuestion.spots = [new ShortAnswerSpot(), spot];

        fixture.componentRef.setInput('question', alternativeQuestion);
        fixture.detectChanges();

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
        fixture.componentRef.setInput('submittedTexts', [submittedText]);
        const tag = '[-spot 1]';

        const alternativeQuestion = new ShortAnswerQuestion();
        alternativeQuestion.text = 'Some text';
        alternativeQuestion.id = 10;
        alternativeQuestion.spots = [spot];
        const solution = new ShortAnswerSolution();
        solution.id = 1;
        solution.text = text;
        const mapping = new ShortAnswerMapping(spot, solution);
        alternativeQuestion.correctMappings = [mapping];

        fixture.componentRef.setInput('question', alternativeQuestion);
        fixture.detectChanges();
        expect(component.classifyInputField(tag)).toBe('completely-correct');
        submittedText.text += '!';
        expect(component.classifyInputField(tag)).toBe('correct');
        component.shortAnswerQuestion().correctMappings = [];
        expect(component.classifyInputField(tag)).toBe('correct');
        fixture.componentRef.setInput('submittedTexts', []);
        fixture.changeDetectorRef.detectChanges();
        expect(component.classifyInputField(tag)).toBe('wrong');
        spot.invalid = true;
        expect(component.classifyInputField(tag)).toBe('invalid');
        spot.invalid = false;
        alternativeQuestion.invalid = true;
        expect(component.classifyInputField(tag)).toBe('invalid');
    });
});
