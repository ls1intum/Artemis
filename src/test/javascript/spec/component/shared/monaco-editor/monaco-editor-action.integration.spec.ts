import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ArtemisTestModule } from '../../../test.module';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';
import { BoldAction } from 'app/shared/monaco-editor/model/actions/bold.action';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { ItalicAction } from 'app/shared/monaco-editor/model/actions/italic.action';
import { CodeAction } from 'app/shared/monaco-editor/model/actions/code.action';
import { ColorAction } from 'app/shared/monaco-editor/model/actions/color.action';
import { UnderlineAction } from 'app/shared/monaco-editor/model/actions/underline.action';
import { CodeBlockAction } from 'app/shared/monaco-editor/model/actions/code-block.action';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { QuoteAction } from 'app/shared/monaco-editor/model/actions/quote.action';
import { FullscreenAction } from 'app/shared/monaco-editor/model/actions/fullscreen.action';
import * as FullscreenUtil from 'app/shared/util/fullscreen.util';
import { TaskAction } from 'app/shared/monaco-editor/model/actions/task.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { HeadingAction } from 'app/shared/monaco-editor/model/actions/heading.action';
import { UrlAction } from 'app/shared/monaco-editor/model/actions/url.action';
import { AttachmentAction } from 'app/shared/monaco-editor/model/actions/attachment.action';
import { OrderedListAction } from 'app/shared/monaco-editor/model/actions/ordered-list.action';
import { UnorderedListAction } from 'app/shared/monaco-editor/model/actions/unordered-list.action';
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
        const action = new BoldAction();
        comp.registerAction(action);
        const registerAction = () => comp.registerAction(action);
        expect(registerAction).toThrow(Error);
    });

    it.each([
        { action: new AttachmentAction(), text: 'Attachment', url: 'https://test.invalid/img.png', defaultText: AttachmentAction.DEFAULT_INSERT_TEXT },
        { action: new UrlAction(), text: 'Link', url: 'https://test.invalid/', defaultText: UrlAction.DEFAULT_INSERT_TEXT },
    ])('should insert $text', ({ action, text, url, defaultText }: { action: UrlAction | AttachmentAction; text: string; url: string; defaultText: string }) => {
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
        const action = new UnorderedListAction();
        comp.registerAction(action);
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe('- ');
    });

    it('should toggle unordered list, skipping empty lines', () => {
        const action = new UnorderedListAction();
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
        const action = new OrderedListAction();
        comp.registerAction(action);
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe('1. ');
    });

    it('should toggle ordered list, skipping empty lines', () => {
        const action = new OrderedListAction();
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
        const action = new HeadingAction(headingLevel);
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
        const action = new TestCaseAction();
        const testCaseName = 'testCase()';
        action.values = [{ value: testCaseName, id: '1' }];
        // With specified test case
        comp.registerAction(action);
        action.executeInCurrentEditor({ selectedItem: action.values[0] });
        expect(comp.getText()).toBe(testCaseName);
        // Without specified test case
        comp.setText('');
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(TestCaseAction.DEFAULT_INSERT_TEXT);
    });

    it('should throw when trying to register a completer without a model', () => {
        const action = new TestCaseAction();
        const registerAction = () => comp.registerAction(action);
        // Detach model (should not happen in practice)
        comp['_editor'].setModel(null);
        expect(registerAction).toThrow(Error);
    });

    it('should provide test case completions', async () => {
        comp.changeModel('testCase', '', 'custom-md');
        const model = comp.models[0];
        const action = new TestCaseAction();
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
        const completionList = await completionFunction(model, new monaco.Position(1, 1), {} as monaco.languages.CompletionContext, {} as monaco.CancellationToken);
        const suggestions = (completionList as monaco.languages.CompletionList)!.suggestions;
        expect(suggestions).toHaveLength(2);
        expect(suggestions[0].label).toBe(action.values[0].value);
        expect(suggestions[1].label).toBe(action.values[1].value);

        // The completion provider should only provide completions for the current model.
        comp.changeModel('other', '', 'custom-md');
        const otherModel = comp.models[1];
        const completionListOther = await completionFunction(otherModel, new monaco.Position(1, 1), {} as monaco.languages.CompletionContext, {} as monaco.CancellationToken);
        expect(completionListOther).toBeUndefined();
    });

    it('should insert tasks', () => {
        const action = new TaskAction();
        comp.registerAction(action);
        action.executeInCurrentEditor();
        expect(comp.getText()).toBe(`[task]${TaskAction.TEXT}`);
    });

    it('should enter fullscreen', () => {
        const action = new FullscreenAction();
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
        const action = new FullscreenAction();
        comp.registerAction(action);
        const exitFullscreenStub = jest.spyOn(FullscreenUtil, 'exitFullscreen').mockImplementation();
        jest.spyOn(FullscreenUtil, 'isFullScreen').mockReturnValue(true);
        action.executeInCurrentEditor();
        expect(exitFullscreenStub).toHaveBeenCalledOnce();
    });

    it.each([
        { action: new BoldAction(), textWithoutDelimiters: 'Here is some bold text.', textWithDelimiters: '**Here is some bold text.**' },
        { action: new ItalicAction(), textWithoutDelimiters: 'Here is some italic text.', textWithDelimiters: '*Here is some italic text.*' },
        { action: new UnderlineAction(), textWithoutDelimiters: 'Here is some underlined text.', textWithDelimiters: '<ins>Here is some underlined text.</ins>' },
        { action: new CodeAction(), textWithoutDelimiters: 'Here is some code.', textWithDelimiters: '`Here is some code.`' },
        {
            action: new ColorAction(),
            textWithoutDelimiters: 'Here is some blue.',
            textWithDelimiters: '<span class="blue">Here is some blue.</span>',
            actionArgs: { color: 'blue' },
        },
        {
            action: new ColorAction(),
            textWithoutDelimiters: 'Here is some red.',
            textWithDelimiters: '<span class="red">Here is some red.</span>', // No argument -> default color is red
        },
        { action: new CodeBlockAction(), textWithoutDelimiters: 'public void main() { }', textWithDelimiters: '```\npublic void main() { }\n```' },
        { action: new CodeBlockAction('java'), textWithoutDelimiters: 'public void main() { }', textWithDelimiters: '```java\npublic void main() { }\n```' },
        {
            action: new QuoteAction(),
            initialText: '> ',
            textToType: 'some quoted text',
            textWithDelimiters: '> some quoted text',
            textWithoutDelimiters: 'some quoted text',
        },
        {
            action: new FormulaAction(),
            initialText: `$$ ${FormulaAction.DEFAULT_FORMULA} $$`,
            textToType: '+ 42x',
            textWithDelimiters: `$$ ${FormulaAction.DEFAULT_FORMULA}+ 42x $$`,
            textWithoutDelimiters: `${FormulaAction.DEFAULT_FORMULA}+ 42x`,
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
            action: TextEditorAction;
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
        action: TextEditorAction,
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
