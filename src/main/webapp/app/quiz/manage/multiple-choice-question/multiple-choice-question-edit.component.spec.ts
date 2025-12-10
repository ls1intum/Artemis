import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { MultipleChoiceQuestionEditComponent } from 'app/quiz/manage/multiple-choice-question/multiple-choice-question-edit.component';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizHintAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-hint.action';
import { QuizExplanationAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-explanation.action';
import { WrongMultipleChoiceAnswerAction } from 'app/shared/monaco-editor/model/actions/quiz/wrong-multiple-choice-answer.action';
import { CorrectMultipleChoiceAnswerAction } from 'app/shared/monaco-editor/model/actions/quiz/correct-multiple-choice-answer.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { MockResizeObserver } from 'src/test/javascript/spec/helpers/mocks/service/mock-resize-observer';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'src/test/javascript/spec/helpers/mocks/service/mock-theme.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'src/test/javascript/spec/helpers/mocks/service/mock-ngb-modal.service';

describe('MultipleChoiceQuestionEditComponent', () => {
    let fixture: ComponentFixture<MultipleChoiceQuestionEditComponent>;
    let component: MultipleChoiceQuestionEditComponent;
    let modalService: NgbModal;

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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
                { provide: NgbModal, useClass: MockNgbModalService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(MultipleChoiceQuestionEditComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
        fixture.componentRef.setInput('question', question);
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        fixture.componentRef.setInput('question', question);
        fixture.componentRef.setInput('questionIndex', 1);
        fixture.detectChanges();
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
        const newQuestion = Object.assign({}, question);
        newQuestion.scoringType = undefined;
        newQuestion.singleChoice = true;
        fixture.componentRef.setInput('question', newQuestion);
        fixture.detectChanges();

        expect(component.question().scoringType).toBeUndefined();
        component.onSingleChoiceChanged();
        expect(component.question().scoringType).toBe(ScoringType.ALL_OR_NOTHING);
    });

    it('should parse answer options but not question titles', () => {
        const originalConsoleWarn = console.warn;
        console.warn = () => {};

        component.domainActionsFound([
            { text: 'text1', action: new TestCaseAction() },
            { text: 'text2', action: new CorrectMultipleChoiceAnswerAction() },
            { text: 'text3', action: new WrongMultipleChoiceAnswerAction() },
            { text: 'text4', action: new QuizExplanationAction() },
            { text: 'text5', action: new QuizHintAction() },
        ]);

        console.warn = originalConsoleWarn;

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

        expect(component.question()).toEqual(expected);
        expect(component.showMultipleChoiceQuestionPreview).toBeTrue();
    });

    it('should parse answer options with question titles', () => {
        const originalConsoleWarn = console.warn;
        console.warn = () => {};

        component.domainActionsFound([
            { text: 'text1', action: new QuizExplanationAction() },
            { text: 'text2', action: new QuizHintAction() },
            { text: 'text3', action: new TestCaseAction() },
            { text: 'text4', action: new CorrectMultipleChoiceAnswerAction() },
            { text: 'text5', action: new WrongMultipleChoiceAnswerAction() },
        ]);

        console.warn = originalConsoleWarn;

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

        expect(component.question()).toEqual(expected);
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

        expect(component.question()).toEqual(expected);
        expect(component.showMultipleChoiceQuestionPreview).toBeTrue();
    });

    it('should find no domain actions', () => {
        component.domainActionsFound([]);

        expectCleanupQuestion();
        expect(component.showMultipleChoiceQuestionPreview).toBeTrue();
    });

    it('should detect changes in markdown', () => {
        const spy = jest.spyOn(component.questionUpdated, 'emit');

        component.changesInMarkdown();

        expectCleanupQuestion();
        expect(spy).toHaveBeenCalledOnce();
    });

    function expectCleanupQuestion() {
        expect(component.question().answerOptions).toHaveLength(0);
        expect(component.question().text).toBeUndefined();
        expect(component.question().explanation).toBeUndefined();
        expect(component.question().hint).toBeUndefined();
        expect(component.question().hasCorrectOption).toBeUndefined();
    }

    it('should trigger delete button', () => {
        const spy = jest.spyOn(component, 'deleteQuestion');
        const deleteButton = fixture.debugElement.query(By.css(`.delete-button`));
        deleteButton.nativeElement.click();
        expect(spy).toHaveBeenCalledOnce();
    });

    it('should parse markdown when preparing for save in edit mode', () => {
        component.markdownEditor()!.inVisualMode = false;
        const parseMarkdownSpy = jest.spyOn(component.markdownEditor()!, 'parseMarkdown');
        component.prepareForSave();
        expect(parseMarkdownSpy).toHaveBeenCalledOnce();
    });

    it('should update markdown from the visual component when preparing for save in visual mode', () => {
        component.markdownEditor()!.inVisualMode = true;
        // if we don't mock this, we get heap out of memory, probably due to some infinite recursion
        component.markdownEditor()!['monacoEditor'] = {
            setText: jest.fn(),
        } as Partial<MonacoEditorComponent> as MonacoEditorComponent;

        const parseQuestionStub = jest.spyOn(component.visualChild(), 'parseQuestion').mockReturnValue('parsed-question');
        component.prepareForSave();
        expect(parseQuestionStub).toHaveBeenCalledOnce();
        expect(component.markdownEditor()!['_markdown']).toBe('parsed-question');
    });

    it('should open modal', () => {
        const content = {};
        const modalSpy = jest.spyOn(modalService, 'open').mockReturnValue({ componentInstance: {} } as any);

        component.open(content);

        expect(modalSpy).toHaveBeenCalledExactlyOnceWith(content, { size: 'lg' });
    });

    it('should detect changes in visual mode', () => {
        const emitSpy = jest.spyOn(component.questionUpdated, 'emit');
        const detectChangesSpy = jest.spyOn(component['changeDetector'], 'detectChanges');

        component.changesInVisualMode();

        expect(emitSpy).toHaveBeenCalledOnce();
        expect(detectChangesSpy).toHaveBeenCalledOnce();
    });

    it('should move up', () => {
        const emitSpy = jest.spyOn(component.questionMoveUp, 'emit');

        component.moveUp();

        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should move down', () => {
        const emitSpy = jest.spyOn(component.questionMoveDown, 'emit');

        component.moveDown();

        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should reset question title', () => {
        component.backupQuestion = Object.assign({}, question, { title: 'backup-title' });
        component.question().title = 'current-title';

        component.resetQuestionTitle();

        expect(component.question().title).toBe('backup-title');
    });

    it('should reset question', () => {
        const backup = Object.assign({}, question, { title: 'backup-title', text: 'backup-text' });
        component.backupQuestion = backup;
        component.question().title = 'current-title';
        component.question().text = 'current-text';
        const detectChangesSpy = jest.spyOn(component['changeDetector'], 'detectChanges');

        component.resetQuestion();

        expect(component.question().title).toBe('backup-title');
        expect(component.question().text).toBe('backup-text');
        expect(detectChangesSpy).toHaveBeenCalledOnce();
    });
});
