import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { QuizScoringInfoStudentModalComponent } from 'app/exercises/quiz/shared/questions/quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { SafeHtml } from '@angular/platform-browser';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { ScoringType } from 'app/entities/quiz/quiz-question.model';

describe('MultipleChoiceQuestionComponent', () => {
    let fixture: ComponentFixture<MultipleChoiceQuestionComponent>;
    let component: MultipleChoiceQuestionComponent;
    let artemisMarkdownService: ArtemisMarkdownService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                MultipleChoiceQuestionComponent,
                MockPipe(ArtemisTranslatePipe),
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

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should update rendered question and answer with html when question is set', () => {
        expect(component).not.toBeNull();

        const question: MultipleChoiceQuestion = {
            id: 1,
            text: 'some-text',
            hint: 'some-hint',
            explanation: 'some-explanation',
            exportQuiz: false,
            randomizeOrder: true,
            invalid: false,
            answerOptions: [{ id: 1, explanation: 'answer-explanation', hint: 'answer-hint', text: 'answer-text', invalid: false }],
        };

        component.question = question;
        expect(component.renderedQuestion.text).toEqual(artemisMarkdownService.safeHtmlForMarkdown(question.text));
        expect(component.renderedQuestion.hint).toEqual(artemisMarkdownService.safeHtmlForMarkdown(question.hint));
        expect(component.renderedQuestion.explanation).toEqual(artemisMarkdownService.safeHtmlForMarkdown(question.explanation));
        expect(component.renderedQuestion.renderedSubElements.length).toEqual(1);

        const expectedAnswer = question.answerOptions![0];
        const renderedAnswer = component.renderedQuestion.renderedSubElements[0];
        expect(safeHtmlToString(renderedAnswer.text)).toEqual(toHtml(expectedAnswer.text!));
        expect(safeHtmlToString(renderedAnswer.hint)).toEqual(toHtml(expectedAnswer.hint!));
        expect(safeHtmlToString(renderedAnswer.explanation)).toEqual(toHtml(expectedAnswer.explanation!));
        expect(renderedAnswer.renderedSubElements.length).toEqual(0);
    });

    it('should update rendered question and answer with empty strings when question/answer values are undefined', () => {
        expect(component).not.toBeNull();

        const question: MultipleChoiceQuestion = {
            text: 'some-text',
            hint: undefined,
            exportQuiz: false,
            randomizeOrder: true,
            invalid: false,
            answerOptions: [{ explanation: 'answer-explanation', text: 'false', invalid: false }],
        };

        component.question = question;
        expect(component.renderedQuestion.text).toEqual(artemisMarkdownService.safeHtmlForMarkdown(question.text));
        expect(component.renderedQuestion.hint).toEqual('');
        expect(component.renderedQuestion.explanation).toEqual('');
        expect(component.renderedQuestion.renderedSubElements.length).toEqual(1);

        const expectedAnswer = question.answerOptions![0];
        const renderedAnswer = component.renderedQuestion.renderedSubElements[0];
        expect(safeHtmlToString(renderedAnswer.explanation)).toEqual(toHtml(expectedAnswer.explanation!));
        expect(safeHtmlToString(renderedAnswer.text)).toEqual(toHtml(expectedAnswer.text!));
        expect(safeHtmlToString(renderedAnswer.hint)).toEqual('');
    });

    function toHtml(value: string) {
        return `<p>${value}</p>`;
    }

    function safeHtmlToString(safeHtml: SafeHtml) {
        return safeHtml['changingThisBreaksApplicationSecurity'] ?? '';
    }

    it('should return true is if the answer option was selected', () => {
        const answerOptions: AnswerOption[] = [
            { id: 1, invalid: false },
            { id: 2, invalid: false },
            { id: 3, invalid: false },
        ];

        component.selectedAnswerOptions = [answerOptions[0], answerOptions[2]];
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeTrue();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeFalse();
        expect(component.isAnswerOptionSelected(answerOptions[2])).toBeTrue();
    });

    it('should not toggle anything on disabled click', () => {
        const answerOptions: AnswerOption[] = [
            { id: 1, invalid: false },
            { id: 2, invalid: false },
            { id: 3, invalid: false },
        ];

        component.clickDisabled = true;
        component.selectedAnswerOptions = [];
        component.toggleSelection(answerOptions[1]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeFalse();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeFalse();
        expect(component.isAnswerOptionSelected(answerOptions[2])).toBeFalse();
    });

    it('should toggle answer options', () => {
        const answerOptions: AnswerOption[] = [
            { id: 1, invalid: false },
            { id: 2, invalid: false },
        ];

        const question: MultipleChoiceQuestion = {
            text: 'some-text',
            exportQuiz: false,
            randomizeOrder: true,
            invalid: false,
            answerOptions,
            scoringType: ScoringType.ALL_OR_NOTHING,
        };

        component.selectedAnswerOptions = [];
        component.question = question;

        component.toggleSelection(answerOptions[1]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeFalse();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeTrue();

        // Re-toggle
        component.toggleSelection(answerOptions[1]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeFalse();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeFalse();

        component.toggleSelection(answerOptions[1]);
        component.toggleSelection(answerOptions[0]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeTrue();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeTrue();

        component.toggleSelection(answerOptions[0]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeFalse();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeTrue();

        component.toggleSelection(answerOptions[1]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeFalse();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeFalse();
    });

    it('should toggle answer options, but only allow one to be selected for single choice questions', () => {
        const answerOptions: AnswerOption[] = [
            { id: 1, invalid: false },
            { id: 2, invalid: false },
        ];

        const question: MultipleChoiceQuestion = {
            text: 'some-text',
            exportQuiz: false,
            randomizeOrder: true,
            invalid: false,
            answerOptions,
            scoringType: ScoringType.ALL_OR_NOTHING,
            singleChoice: true,
        };

        component.selectedAnswerOptions = [];
        component.question = question;

        component.toggleSelection(answerOptions[1]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeFalse();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeTrue();

        // Re-toggle
        component.toggleSelection(answerOptions[1]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeFalse();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeFalse();

        component.toggleSelection(answerOptions[1]);
        component.toggleSelection(answerOptions[0]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeTrue();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeFalse();

        component.toggleSelection(answerOptions[0]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeFalse();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeFalse();

        component.toggleSelection(answerOptions[1]);
        expect(component.isAnswerOptionSelected(answerOptions[0])).toBeFalse();
        expect(component.isAnswerOptionSelected(answerOptions[1])).toBeTrue();
    });
});
