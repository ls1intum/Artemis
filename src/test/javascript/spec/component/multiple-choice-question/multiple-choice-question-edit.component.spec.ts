import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { MultipleChoiceQuestionEditComponent } from 'app/exercises/quiz/manage/multiple-choice-question/multiple-choice-question-edit.component';
import { QuizScoringInfoModalComponent } from 'app/exercises/quiz/manage/quiz-scoring-info-modal/quiz-scoring-info-modal.component';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { ArtemisTestModule } from '../../test.module';
import { NgbCollapseMocksModule } from '../../helpers/mocks/directive/ngbCollapseMocks.module';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MultipleChoiceVisualQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-visual-question.component';
import { ScoringType } from 'app/entities/quiz/quiz-question.model';
import { QuizHintAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-hint.action';
import { QuizExplanationAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-explanation.action';
import { WrongMultipleChoiceAnswerAction } from 'app/shared/monaco-editor/model/actions/quiz/wrong-multiple-choice-answer.action';
import { CorrectMultipleChoiceAnswerAction } from 'app/shared/monaco-editor/model/actions/quiz/correct-multiple-choice-answer.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

describe('MultipleChoiceQuestionEditComponent', () => {
    let fixture: ComponentFixture<MultipleChoiceQuestionEditComponent>;
    let component: MultipleChoiceQuestionEditComponent;

    const question: MultipleChoiceQuestion = {
        exportQuiz: false,
        randomizeOrder: true,
        id: 1,
        text: 'some-text',
        hint: 'some-hint',
        explanation: 'some-explanation',
        invalid: false,
        answerOptions: [
            { id: 1, explanation: 'answer-explanation', hint: 'answer-hint', text: 'answer-text', invalid: false },
            { id: 2, text: 'answer-text-correct', isCorrect: true, invalid: false },
        ],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, DragDropModule, NgbCollapseMocksModule, MockDirective(NgbTooltip)],
            declarations: [
                MultipleChoiceQuestionEditComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(QuizScoringInfoModalComponent),
                MockComponent(MarkdownEditorMonacoComponent),
                MockComponent(SecuredImageComponent),
                MockComponent(DragAndDropQuestionComponent),
                MockComponent(MultipleChoiceQuestionComponent),
                MockComponent(MultipleChoiceVisualQuestionComponent),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(MultipleChoiceQuestionEditComponent);
        component = fixture.componentInstance;
        component.question = question;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize with question markdown text', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.questionEditorText).toEqual(
            'some-text\n' +
                '\t[hint] some-hint\n' +
                '\t[exp] some-explanation\n' +
                '\n' +
                '[wrong] answer-text\n' +
                '\t[hint] answer-hint\n' +
                '\t[exp] answer-explanation\n' +
                '[correct] answer-text-correct',
        );
    });

    it('should store scoring type when changed', () => {
        component.question = { ...question };
        component.question.scoringType = undefined;
        component.question.singleChoice = true;

        fixture.detectChanges();

        expect(component.question.scoringType).toBeUndefined();
        component.onSingleChoiceChanged();
        expect(component.question.scoringType).toBe(ScoringType.ALL_OR_NOTHING);
    });

    it('should parse answer options but not question titles', () => {
        component.domainActionsFound([
            { text: 'text1', action: new TestCaseAction() },
            { text: 'text2', action: new CorrectMultipleChoiceAnswerAction() },
            { text: 'text3', action: new WrongMultipleChoiceAnswerAction() },
            { text: 'text4', action: new QuizExplanationAction() },
            { text: 'text5', action: new QuizHintAction() },
        ]);

        const expected: MultipleChoiceQuestion = {
            id: question.id,
            exportQuiz: false,
            randomizeOrder: true,
            invalid: false,
            text: undefined,
            explanation: undefined,
            hint: undefined,
            hasCorrectOption: undefined,
            answerOptions: [
                {
                    isCorrect: true,
                    invalid: false,
                    text: 'text2',
                },
                {
                    isCorrect: false,
                    invalid: false,
                    text: 'text3',
                    explanation: 'text4',
                    hint: 'text5',
                },
            ],
        };

        expect(component.question).toEqual(expected);
        expect(component.showMultipleChoiceQuestionPreview).toBeTrue();
    });

    it('should parse answer options with question titles', () => {
        component.domainActionsFound([
            { text: 'text1', action: new QuizExplanationAction() },
            { text: 'text2', action: new QuizHintAction() },
            { text: 'text3', action: new TestCaseAction() },
            { text: 'text4', action: new CorrectMultipleChoiceAnswerAction() },
            { text: 'text5', action: new WrongMultipleChoiceAnswerAction() },
        ]);

        const expected: MultipleChoiceQuestion = {
            id: question.id,
            text: undefined,
            exportQuiz: false,
            randomizeOrder: true,
            invalid: false,
            hint: 'text2',
            explanation: 'text1',
            hasCorrectOption: undefined,
            answerOptions: [
                {
                    isCorrect: true,
                    invalid: false,
                    text: 'text4',
                },
                {
                    isCorrect: false,
                    invalid: false,
                    text: 'text5',
                },
            ],
        };

        expect(component.question).toEqual(expected);
        expect(component.showMultipleChoiceQuestionPreview).toBeTrue();
    });

    it('should parse question titles', () => {
        component.domainActionsFound([{ text: 'text1', action: undefined }]);

        const expected: MultipleChoiceQuestion = {
            id: question.id,
            text: 'text1',
            exportQuiz: false,
            randomizeOrder: true,
            invalid: false,
            hint: undefined,
            explanation: undefined,
            hasCorrectOption: undefined,
            answerOptions: [],
        };

        expect(component.question).toEqual(expected);
        expect(component.showMultipleChoiceQuestionPreview).toBeTrue();
    });

    it('should find no domain actions', () => {
        component.domainActionsFound([]);

        expectCleanupQuestion();
        expect(component.showMultipleChoiceQuestionPreview).toBeTrue();
    });

    it('should detect changes in markdown', () => {
        const spy = jest.spyOn(component.questionUpdated, 'emit');

        fixture.detectChanges();
        component.changesInMarkdown();

        expectCleanupQuestion();
        expect(spy).toHaveBeenCalledOnce();
    });

    function expectCleanupQuestion() {
        expect(component.question.answerOptions).toHaveLength(0);
        expect(component.question.text).toBeUndefined();
        expect(component.question.explanation).toBeUndefined();
        expect(component.question.hint).toBeUndefined();
        expect(component.question.hasCorrectOption).toBeUndefined();
    }

    it('should trigger delete button', () => {
        const spy = jest.spyOn(component, 'deleteQuestion');
        const deleteButton = fixture.debugElement.query(By.css(`.delete-button`));
        deleteButton.nativeElement.click();
        expect(spy).toHaveBeenCalledOnce();
    });

    it('should parse markdown when preparing for save in edit mode', () => {
        fixture.detectChanges();
        component['markdownEditor'].inVisualMode = false;
        const parseMarkdownSpy = jest.spyOn(component['markdownEditor'], 'parseMarkdown');
        component.prepareForSave();
        expect(parseMarkdownSpy).toHaveBeenCalledOnce();
    });

    it('should update markdown from the visual component when preparing for save in visual mode', () => {
        fixture.detectChanges();
        component['markdownEditor'].inVisualMode = true;
        const parseQuestionStub = jest.spyOn(component['visualChild'], 'parseQuestion').mockReturnValue('parsed-question');
        component.prepareForSave();
        expect(parseQuestionStub).toHaveBeenCalledOnce();
        expect(component['markdownEditor'].markdown).toBe('parsed-question');
    });
});
