import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { InsertShortAnswerOptionAction } from 'app/shared/monaco-editor/model/actions/quiz/insert-short-answer-option.action';
import { InsertShortAnswerSpotAction } from 'app/shared/monaco-editor/model/actions/quiz/insert-short-answer-spot.action';
import { WrongMultipleChoiceAnswerAction } from 'app/shared/monaco-editor/model/actions/quiz/wrong-multiple-choice-answer.action';
import { CorrectMultipleChoiceAnswerAction } from 'app/shared/monaco-editor/model/actions/quiz/correct-multiple-choice-answer.action';
import { QuizExplanationAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-explanation.action';
import { QuizHintAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-hint.action';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';

describe('MonacoEditorActionQuizIntegration', () => {
    let fixture: ComponentFixture<MonacoEditorComponent>;
    let comp: MonacoEditorComponent;

    // Actions
    let insertShortAnswerOptionAction: InsertShortAnswerOptionAction;
    let insertShortAnswerSpotAction: InsertShortAnswerSpotAction;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MonacoEditorComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MonacoEditorComponent);
                comp = fixture.componentInstance;
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
                insertShortAnswerOptionAction = new InsertShortAnswerOptionAction();
                insertShortAnswerSpotAction = new InsertShortAnswerSpotAction(insertShortAnswerOptionAction);
                fixture.detectChanges();
                comp.registerAction(insertShortAnswerOptionAction);
                comp.registerAction(insertShortAnswerSpotAction);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('MonacoInsertShortAnswerOption', () => {
        const getLastLine = () => comp.getText().split('\n').last();

        it('should insert text with the default answer option', () => {
            insertShortAnswerOptionAction.executeInCurrentEditor();
            // Text must match
            expect(getLastLine()).toBe(`[-option #] ${InsertShortAnswerOptionAction.DEFAULT_TEXT}`);
            // Also test if the selection works. Type the option text.
            comp.triggerKeySequence('This is an actual option!');
            expect(getLastLine()).toBe('[-option #] This is an actual option!');
        });

        it('should insert the default text for blank option texts', () => {
            insertShortAnswerOptionAction.executeInCurrentEditor({ optionText: '' });
            expect(getLastLine()).toBe(`[-option #] ${InsertShortAnswerOptionAction.DEFAULT_TEXT}`);
        });

        it('should insert text with the specified spot number', () => {
            insertShortAnswerOptionAction.executeInCurrentEditor({ spotNumber: 5 });
            // Text must match
            expect(getLastLine()).toBe(`[-option 5] ${InsertShortAnswerOptionAction.DEFAULT_TEXT}`);
            // Also test if the selection works. Type the option text.
            comp.triggerKeySequence('This is an actual option!');
            expect(getLastLine()).toBe('[-option 5] This is an actual option!');
        });

        it('should insert text with the specified option text and spot number', () => {
            insertShortAnswerOptionAction.executeInCurrentEditor({ optionText: 'This is a custom option!', spotNumber: 5 });
            expect(getLastLine()).toBe('[-option 5] This is a custom option!');
        });

        it('should insert text with the specified option text', () => {
            insertShortAnswerOptionAction.executeInCurrentEditor({ optionText: 'This is a custom option!' });
            expect(getLastLine()).toBe('[-option #] This is a custom option!');
        });

        it('should insert text after the last option', () => {
            const text = '[-option 1] Option 1\n[-option 2] Option 2\n[-option 3] Option 3';
            const expectedText = text + `\n[-option #] ${InsertShortAnswerOptionAction.DEFAULT_TEXT}`;
            comp.setText('[-option 1] Option 1\n[-option 2] Option 2\n[-option 3] Option 3');
            insertShortAnswerOptionAction.executeInCurrentEditor();
            expect(comp.getText()).toBe(expectedText);
        });

        it('should insert text with more space if the last line is not an option', () => {
            const text = 'Some question text\nof a question\nwith lines';
            const expectedText = text + `\n\n\n[-option #] ${InsertShortAnswerOptionAction.DEFAULT_TEXT}`;
            comp.setText(text);
            insertShortAnswerOptionAction.executeInCurrentEditor();
            expect(comp.getText()).toBe(expectedText);
        });
    });

    describe('EditorInsertShortAnswerSpotAction', () => {
        let insertAnswerOptionActionExecuteStub: jest.SpyInstance;

        beforeEach(() => {
            insertAnswerOptionActionExecuteStub = jest.spyOn(insertShortAnswerOptionAction, 'executeInCurrentEditor').mockImplementation();
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should forward the spot number & selection to the insertShortAnswerOptionAction', () => {
            comp.setText('Some text of a question');
            comp.setSelection({ startLineNumber: 1, startColumn: 6, endLineNumber: 1, endColumn: 10 });
            insertShortAnswerSpotAction.spotNumber = 5;
            insertShortAnswerSpotAction.executeInCurrentEditor();
            expect(insertAnswerOptionActionExecuteStub).toHaveBeenCalledWith({ spotNumber: 5, optionText: 'text' });
        });

        it('should insert a spot at the current position (no selection)', () => {
            const text = 'I am about to put a spot here: ';
            // Type the text so the cursor moves along
            comp.triggerKeySequence(text);
            insertShortAnswerSpotAction.executeInCurrentEditor();
            expect(comp.getText()).toBe(text + '[-spot 1]');
        });

        it('should insert a spot at the current position (with selection)', () => {
            comp.setText('I am about to put a spot here.');
            comp.setSelection({ startLineNumber: 1, startColumn: 21, endLineNumber: 1, endColumn: 25 });
            insertShortAnswerSpotAction.executeInCurrentEditor();
            expect(comp.getText()).toBe('I am about to put a [-spot 1] here.');
        });

        it('should insert a spot and attach an option', () => {
            insertAnswerOptionActionExecuteStub.mockRestore();
            const questionText = 'This is a question.\nIt can have a spot where students can put an answer, e.g.: ';
            // Type the text so the cursor moves along
            comp.triggerKeySequence(questionText);
            insertShortAnswerSpotAction.executeInCurrentEditor();
            expect(comp.getText()).toBe(questionText + '[-spot 1]' + `\n\n\n[-option 1] ${InsertShortAnswerOptionAction.DEFAULT_TEXT}`);
        });
    });

    describe('Multiple Choice answer options', () => {
        it('should insert a wrong MC option', () => {
            comp.triggerKeySequence('This is a question that needs some options.');
            const action = new WrongMultipleChoiceAnswerAction();
            comp.registerAction(action);
            action.executeInCurrentEditor();
            expect(comp.getText()).toBe('This is a question that needs some options.\n[wrong] Enter a wrong answer option here');
        });

        it('should insert a correct MC option', () => {
            comp.triggerKeySequence('This is a question that needs some options.');
            const action = new CorrectMultipleChoiceAnswerAction();
            comp.registerAction(action);
            action.executeInCurrentEditor();
            expect(comp.getText()).toBe('This is a question that needs some options.\n[correct] Enter a correct answer option here');
        });

        it('should add an explanation to an answer option', () => {
            comp.triggerKeySequence('This is a question that has an option.\n[correct] Option 1');
            const action = new QuizExplanationAction();
            comp.registerAction(action);
            action.executeInCurrentEditor();
            expect(comp.getText()).toBe(
                'This is a question that has an option.\n[correct] Option 1\n\t[exp] Add an explanation here (only visible in feedback after quiz has ended)',
            );
        });

        it('should add a hint to an answer option', () => {
            comp.triggerKeySequence('This is a question that has an option.\n[correct] Option 1');
            const action = new QuizHintAction();
            comp.registerAction(action);
            action.executeInCurrentEditor();
            expect(comp.getText()).toBe('This is a question that has an option.\n[correct] Option 1\n\t[hint] Add a hint here (visible during the quiz via ?-Button)');
        });
    });
});
