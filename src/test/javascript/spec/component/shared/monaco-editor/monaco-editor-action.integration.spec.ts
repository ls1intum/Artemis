import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ArtemisTestModule } from '../../../test.module';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';
import { MonacoBoldAction } from 'app/shared/monaco-editor/model/actions/monaco-bold.action';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { MonacoItalicAction } from 'app/shared/monaco-editor/model/actions/monaco-italic.action';
import { MonacoCodeAction } from 'app/shared/monaco-editor/model/actions/monaco-code.action';
import { MonacoColorAction } from 'app/shared/monaco-editor/model/actions/monaco-color.action';
import { MonacoUnderlineAction } from 'app/shared/monaco-editor/model/actions/monaco-underline.action';
import { MonacoCodeBlockAction } from 'app/shared/monaco-editor/model/actions/monaco-code-block.action';
import { MonacoFormulaAction } from 'app/shared/monaco-editor/model/actions/monaco-formula.action';
import { MonacoQuoteAction } from 'app/shared/monaco-editor/model/actions/monaco-quote.action';
import { MonacoFullscreenAction } from 'app/shared/monaco-editor/model/actions/monaco-fullscreen.action';
import * as FullscreenUtil from 'app/shared/util/fullscreen.util';
import { MonacoTaskAction } from 'app/shared/monaco-editor/model/actions/monaco-task.action';
import { MonacoTestCaseAction } from 'app/shared/monaco-editor/model/actions/monaco-test-case.action';
import { MonacoHeadingAction } from 'app/shared/monaco-editor/model/actions/monaco-heading.action';
import { MonacoUrlAction } from 'app/shared/monaco-editor/model/actions/monaco-url.action';
import { MonacoAttachmentAction } from 'app/shared/monaco-editor/model/actions/monaco-attachment.action';
import { MonacoOrderedListAction } from 'app/shared/monaco-editor/model/actions/monaco-ordered-list.action';
import { MonacoUnorderedListAction } from 'app/shared/monaco-editor/model/actions/monaco-unordered-list.action';
import * as monaco from 'monaco-editor';

describe('MonacoEditorActionIntegration', () => {
    let fixture: ComponentFixture<MonacoEditorComponent>;
    let comp: MonacoEditorComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MonacoEditorModule],
            declarations: [MonacoEditorComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MonacoEditorComponent);
                comp = fixture.componentInstance;
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should throw when trying to register an action twice', () => {
        const action = new MonacoBoldAction();
        comp.registerAction(action);
        const registerAction = () => comp.registerAction(action);
        expect(registerAction).toThrow(Error);
    });

    it.each([
        { action: new MonacoAttachmentAction(), text: 'Attachment', url: 'https://test.invalid/img.png', defaultText: MonacoAttachmentAction.DEFAULT_INSERT_TEXT },
        { action: new MonacoUrlAction(), text: 'Link', url: 'https://test.invalid/', defaultText: MonacoUrlAction.DEFAULT_INSERT_TEXT },
    ])('should insert $text', ({ action, text, url, defaultText }: { action: MonacoUrlAction | MonacoAttachmentAction; text: string; url: string; defaultText: string }) => {
        const prefix = text === 'Attachment' ? '!' : '';
        comp.registerAction(action);
        action.executeInCurrentEditor({ text, url });
        expect(comp.getText()).toBe(`${prefix}[${text}](${url})`);
        // No arguments -> insert default text
        comp.setText('');
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(defaultText);
    });

    it('should insert unordered list', () => {
        const action = new MonacoUnorderedListAction();
        comp.registerAction(action);
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe('- ');
    });

    it('should toggle unordered list, skipping empty lines', () => {
        const action = new MonacoUnorderedListAction();
        comp.registerAction(action);
        const lines = ['One', '', 'Two', 'Three'];
        const bulletedLines = lines.map((line) => (line ? `- ${line}` : ''));
        comp.setText(lines.join('\n'));
        comp.setSelection({ startLineNumber: 1, startColumn: 1, endLineNumber: lines.length, endColumn: lines[lines.length - 1].length + 1 });
        // Introduce list
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(bulletedLines.join('\n'));
        // Remove list
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(lines.join('\n'));
    });

    it('should insert ordered list', () => {
        const action = new MonacoOrderedListAction();
        comp.registerAction(action);
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe('1. ');
    });

    it('should toggle ordered list, skipping empty lines', () => {
        const action = new MonacoOrderedListAction();
        comp.registerAction(action);
        const lines = ['One', '', 'Two', 'Three'];
        const numberedLines = lines.map((line, index) => (line ? `${index + 1}. ${line}` : ''));
        comp.setText(lines.join('\n'));
        comp.setSelection({ startLineNumber: 1, startColumn: 1, endLineNumber: lines.length, endColumn: lines[lines.length - 1].length + 1 });
        // Introduce list
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(numberedLines.join('\n'));
        // Remove list
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(lines.join('\n'));
    });

    it.each([1, 2, 3])('Should toggle heading %i on selected line', (headingLevel) => {
        const action = new MonacoHeadingAction(headingLevel);
        comp.registerAction(action);
        // No selection -> insert heading
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(`${'#'.repeat(headingLevel)} Heading ${headingLevel}`);
        // Selection -> toggle heading
        comp.setSelection({ startLineNumber: 1, startColumn: 1, endLineNumber: 1, endColumn: comp.getText().length + 1 });
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(`Heading ${headingLevel}`);
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(`${'#'.repeat(headingLevel)} Heading ${headingLevel}`);
    });

    it('should insert test case names', () => {
        const action = new MonacoTestCaseAction();
        const testCaseName = 'testCase()';
        action.values = [{ value: testCaseName, id: '1' }];
        // With specified test case
        comp.registerAction(action);
        action.executeInCurrentEditor({ selectedItem: action.values[0] });
        expect(comp.getText()).toBe(testCaseName);
        // Without specified test case
        comp.setText('');
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(MonacoTestCaseAction.DEFAULT_INSERT_TEXT);
    });

    it('should throw when trying to register a completer without a model', () => {
        const action = new MonacoTestCaseAction();
        const registerAction = () => comp.registerAction(action);
        // Detach model (should not happen in practice)
        comp['_editor'].setModel(null);
        expect(registerAction).toThrow(Error);
    });

    it('should provide test case completions', () => {
        comp.changeModel('testCase', '', 'custom-md');
        const model = comp.models[0];
        const action = new MonacoTestCaseAction();
        action.values = [
            { value: 'testCase1', id: '1' },
            { value: 'testCase2', id: '2' },
        ];
        const registerCompletionProviderStub = jest.spyOn(monaco.languages, 'registerCompletionItemProvider').mockImplementation();
        comp.registerAction(action);
        expect(registerCompletionProviderStub).toHaveBeenCalledOnce();
        const completionFunction = registerCompletionProviderStub.mock.calls[0][1].provideCompletionItems;
        expect(completionFunction).toBeDefined();
        // We do not use completionContext and cancellationToken, but they are required by the function signature. Therefore, we pass empty objects.
        const completionList = completionFunction(model, new monaco.Position(1, 1), {} as monaco.languages.CompletionContext, {} as monaco.CancellationToken);
        const suggestions = (completionList as monaco.languages.CompletionList)!.suggestions;
        expect(suggestions).toHaveLength(2);
        expect(suggestions[0].label).toBe(action.values[0].value);
        expect(suggestions[1].label).toBe(action.values[1].value);

        // The completion provider should only provide completions for the current model.
        comp.changeModel('other', '', 'custom-md');
        const otherModel = comp.models[1];
        const completionListOther = completionFunction(otherModel, new monaco.Position(1, 1), {} as monaco.languages.CompletionContext, {} as monaco.CancellationToken);
        expect(completionListOther).toBeUndefined();
    });

    it('should insert tasks', () => {
        const action = new MonacoTaskAction();
        comp.registerAction(action);
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(`[task]${MonacoTaskAction.TEXT}`);
    });

    it('should enter fullscreen', () => {
        const action = new MonacoFullscreenAction();
        comp.registerAction(action);
        const enterFullscreenStub = jest.spyOn(FullscreenUtil, 'enterFullscreen').mockImplementation();
        jest.spyOn(FullscreenUtil, 'isFullScreen').mockReturnValue(false);
        const dummyElement = document.createElement('div');
        action.element = dummyElement;
        action.executeInCurrentEditor();
        expect(enterFullscreenStub).toHaveBeenCalledExactlyOnceWith(dummyElement);

        // Without a specified element, it should use the editor's DOM node
        action.element = undefined;
        action.executeInCurrentEditor();
        const editorElement = document.querySelector<HTMLDivElement>('.monaco-editor');
        expect(enterFullscreenStub).toHaveBeenCalledTimes(2);
        expect(enterFullscreenStub).toHaveBeenNthCalledWith(2, editorElement);
    });

    it('should leave fullscreen', () => {
        const action = new MonacoFullscreenAction();
        comp.registerAction(action);
        const exitFullscreenStub = jest.spyOn(FullscreenUtil, 'exitFullscreen').mockImplementation();
        jest.spyOn(FullscreenUtil, 'isFullScreen').mockReturnValue(true);
        action.executeInCurrentEditor();
        expect(exitFullscreenStub).toHaveBeenCalledOnce();
    });

    it.each([
        { action: new MonacoBoldAction(), textWithoutDelimiters: 'Here is some bold text.', textWithDelimiters: '**Here is some bold text.**' },
        { action: new MonacoItalicAction(), textWithoutDelimiters: 'Here is some italic text.', textWithDelimiters: '*Here is some italic text.*' },
        { action: new MonacoUnderlineAction(), textWithoutDelimiters: 'Here is some underlined text.', textWithDelimiters: '<ins>Here is some underlined text.</ins>' },
        { action: new MonacoCodeAction(), textWithoutDelimiters: 'Here is some code.', textWithDelimiters: '`Here is some code.`' },
        {
            action: new MonacoColorAction(),
            textWithoutDelimiters: 'Here is some blue.',
            textWithDelimiters: '<span class="blue">Here is some blue.</span>',
            actionArgs: { color: 'blue' },
        },
        {
            action: new MonacoColorAction(),
            textWithoutDelimiters: 'Here is some red.',
            textWithDelimiters: '<span class="red">Here is some red.</span>', // No argument -> default color is red
        },
        { action: new MonacoCodeBlockAction(), textWithoutDelimiters: 'public void main() { }', textWithDelimiters: '```\npublic void main() { }\n```' },
        { action: new MonacoCodeBlockAction('java'), textWithoutDelimiters: 'public void main() { }', textWithDelimiters: '```java\npublic void main() { }\n```' },
        {
            action: new MonacoQuoteAction(),
            initialText: '> ',
            textToType: 'some quoted text',
            textWithDelimiters: '> some quoted text',
            textWithoutDelimiters: 'some quoted text',
        },
        {
            action: new MonacoFormulaAction(),
            initialText: `$$ ${MonacoFormulaAction.DEFAULT_FORMULA} $$`,
            textToType: '+ 42x',
            textWithDelimiters: `$$ ${MonacoFormulaAction.DEFAULT_FORMULA}+ 42x $$`,
            textWithoutDelimiters: `${MonacoFormulaAction.DEFAULT_FORMULA}+ 42x`,
        },
    ])(
        'Delimiter action ($action.id) should insert delimiters at position and toggle around selection',
        ({
            action,
            textWithoutDelimiters,
            textWithDelimiters,
            actionArgs,
            textToType,
            initialText,
        }: {
            action: MonacoEditorAction;
            textWithoutDelimiters: string;
            textWithDelimiters: string;
            actionArgs?: object;
            textToType?: string;
            initialText?: string;
        }) => {
            testDelimiterAction(action, textWithoutDelimiters, textWithDelimiters, actionArgs, textToType, initialText);
        },
    );

    /**
     * Test the action by inserting delimiters at position and toggling around selection.
     * @param action The action to test
     * @param textWithoutDelimiters The text without delimiters (e.g. 'Here is some bold text.')
     * @param textWithDelimiters The text with delimiters (e.g. '**Here is some bold text.**')
     * @param actionArgs The argument to pass to the action
     * @param textToType The text to type after the action has been triggered without a selection.
     * @param initialText The initial text to expect after the action has been triggered without a selection.
     */
    function testDelimiterAction(
        action: MonacoEditorAction,
        textWithoutDelimiters: string,
        textWithDelimiters: string,
        actionArgs?: object,
        textToType?: string,
        initialText?: string,
    ): void {
        const runSpy = jest.spyOn(action, 'run');
        comp.registerAction(action);
        // Position
        action.executeInCurrentEditor(actionArgs);
        if (initialText) {
            expect(comp.getText()).toBe(initialText);
        }
        comp.triggerKeySequence(textToType ?? textWithoutDelimiters);
        const text = comp.getText();
        expect(text).toBe(textWithDelimiters);
        // Selection
        const textLines = text.split('\n');
        const fullSelection = { startLineNumber: 1, startColumn: 1, endLineNumber: textLines.length, endColumn: textLines[textLines.length - 1].length + 1 };
        comp.setSelection(fullSelection);
        // Toggle off
        action.executeInCurrentEditor(actionArgs);
        expect(comp.getText()).toBe(textWithoutDelimiters);
        // Toggle on
        action.executeInCurrentEditor(actionArgs);
        expect(comp.getText()).toBe(textWithDelimiters);
        expect(runSpy).toHaveBeenCalledTimes(3);
    }
});
