import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
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
import { MockClipboardItem } from 'test/helpers/mocks/service/mock-clipboard-item';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { RewriteAction } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewrite.action';
import { signal, WritableSignal } from '@angular/core';
import { of } from 'rxjs';
import { ConsistencyCheckResult, RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence-results';
import { FaqConsistencyAction } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/FaqConsistencyAction';

describe('MonacoEditorActionIntegration', () => {
    let fixture: ComponentFixture<MonacoEditorComponent>;
    let comp: MonacoEditorComponent;
    let mockArtemisService = {
        rewrite: jest.fn(),
        faqConsistencyCheck: jest.fn(),
        consistencyCheck: jest.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MonacoEditorComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
                { provide: ArtemisIntelligenceService, useValue: mockArtemisService as unknown as ArtemisIntelligenceService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(MonacoEditorComponent);
        comp = fixture.componentInstance;
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });

        Object.assign(navigator, {
            clipboard: {
                read: jest.fn(),
            },
        });
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
        jest.restoreAllMocks();
    });

    it('should throw when trying to register an action twice', () => {
        const action = new BoldAction();
        comp.registerAction(action);
        const registerAction = () => comp.registerAction(action);
        expect(registerAction).toThrow(Error);
    });

    it.each([
        {
            action: new AttachmentAction(),
            text: 'Attachment',
            url: 'https://test.invalid/img.png',
            defaultText: AttachmentAction.DEFAULT_INSERT_TEXT,
        },
        {
            action: new UrlAction(),
            text: 'Link',
            url: 'https://test.invalid/',
            defaultText: UrlAction.DEFAULT_INSERT_TEXT,
        },
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

    it('should not access the clipboard if no upload callback is specified', async () => {
        const clipboardReadSpy = jest.spyOn(navigator.clipboard, 'read');
        const addPasteListenerSpy = jest.spyOn(comp['textEditorAdapter'], 'addPasteListener');
        const action = new AttachmentAction();
        comp.registerAction(action);
        // The addPasteListenerSpy should have received a function that does not result in the clipboard being read when called.
        expect(addPasteListenerSpy).toHaveBeenCalled();
        const pasteListener = addPasteListenerSpy.mock.calls[0][0];
        expect(pasteListener).toBeDefined();
        await pasteListener('');
        expect(clipboardReadSpy).not.toHaveBeenCalled();
    });

    it('should process files from the clipboard', async () => {
        const imageBlob = new Blob([]);
        const imageClipboardItem: MockClipboardItem = {
            types: ['image/png'],
            getType: jest.fn().mockResolvedValue(imageBlob),
            presentationStyle: 'inline',
        };

        const nonImageBlob = new Blob(['Sample text content']);
        const textClipboardItem: MockClipboardItem = {
            types: ['text/plain'],
            getType: jest.fn().mockResolvedValue(nonImageBlob),
            presentationStyle: 'inline',
        };

        // Mock the clipboard read function to return the created ClipboardItems
        const clipboardReadSpy = jest.spyOn(navigator.clipboard, 'read').mockResolvedValue([imageClipboardItem, textClipboardItem]);
        const addPasteListenerSpy = jest.spyOn(comp['textEditorAdapter'], 'addPasteListener');
        const uploadCallback = jest.fn();
        const action = new AttachmentAction();
        action.setUploadCallback(uploadCallback);
        comp.registerAction(action);
        const pasteListener = addPasteListenerSpy.mock.calls[0][0];
        expect(pasteListener).toBeDefined();
        await pasteListener('');
        expect(clipboardReadSpy).toHaveBeenCalledOnce();
        expect(uploadCallback).toHaveBeenCalledExactlyOnceWith([new File([imageBlob], 'image.png', { type: 'image/png' })]);
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
        comp.setSelection({
            startLineNumber: 1,
            startColumn: 1,
            endLineNumber: lines.length,
            endColumn: lines[lines.length - 1].length + 1,
        });
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
        {
            action: new BoldAction(),
            textWithoutDelimiters: 'Here is some bold text.',
            textWithDelimiters: '**Here is some bold text.**',
        },
        {
            action: new ItalicAction(),
            textWithoutDelimiters: 'Here is some italic text.',
            textWithDelimiters: '*Here is some italic text.*',
        },
        {
            action: new UnderlineAction(),
            textWithoutDelimiters: 'Here is some underlined text.',
            textWithDelimiters: '<ins>Here is some underlined text.</ins>',
        },
        {
            action: new CodeAction(),
            textWithoutDelimiters: 'Here is some code.',
            textWithDelimiters: '`Here is some code.`',
        },
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
        {
            action: new CodeBlockAction(),
            textWithoutDelimiters: 'public void main() { }',
            textWithDelimiters: '```\npublic void main() { }\n```',
        },
        {
            action: new CodeBlockAction('java'),
            textWithoutDelimiters: 'public void main() { }',
            textWithDelimiters: '```java\npublic void main() { }\n```',
        },
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
        const fullSelection = {
            startLineNumber: 1,
            startColumn: 1,
            endLineNumber: textLines.length,
            endColumn: textLines[textLines.length - 1].length + 1,
        };
        comp.setSelection(fullSelection);
        // Toggle off
        action.executeInCurrentEditor(actionArgs);
        expect(comp.getText()).toBe(textWithoutDelimiters);
        // Toggle on
        action.executeInCurrentEditor(actionArgs);
        expect(comp.getText()).toBe(textWithDelimiters);
        expect(runSpy).toHaveBeenCalledTimes(3);
    }

    it('should rewrite editor content via ArtemisIntelligenceService (FAQ variant)', async () => {
        const courseId = 42;
        const originalText = 'Original text to be rewritten';
        const rewrittenText = 'Rewritten text from service';

        const rewriteResult: RewriteResult = {
            result: rewrittenText,
            inconsistencies: undefined,
            suggestions: undefined,
            improvement: 'Text was improved',
        };

        mockArtemisService.rewrite.mockReturnValue(of(rewriteResult));

        comp.setText(originalText);
        const action = new RewriteAction(TestBed.inject(ArtemisIntelligenceService), RewritingVariant.FAQ, courseId);

        comp.registerAction(action);
        action.executeInCurrentEditor();

        expect(mockArtemisService.rewrite).toHaveBeenCalledTimes(1);
        expect(mockArtemisService.rewrite).toHaveBeenCalledWith(originalText, RewritingVariant.FAQ, courseId);
        expect(comp.getText()).toBe(rewrittenText);
    });

    it('should pass through non-FAQ variant to ArtemisIntelligenceService.rewrite', async () => {
        const courseId = 7;
        const originalText = 'PS to improve';
        const rewrittenText = 'Improved problem statement';

        const rewriteResult: RewriteResult = {
            result: rewrittenText,
            inconsistencies: undefined,
            suggestions: undefined,
            improvement: 'Text was improved',
        };

        mockArtemisService.rewrite.mockReturnValue(of(rewriteResult));

        comp.setText(originalText);
        const variant = (Object.values(RewritingVariant).find((v) => v !== RewritingVariant.FAQ) ?? RewritingVariant.FAQ) as RewritingVariant;

        const action = new RewriteAction(TestBed.inject(ArtemisIntelligenceService), variant, courseId);
        comp.registerAction(action);
        action.executeInCurrentEditor();

        expect(mockArtemisService.rewrite).toHaveBeenCalledTimes(1);
        expect(mockArtemisService.rewrite).toHaveBeenCalledWith(originalText, variant, courseId);
        expect(comp.getText()).toBe(rewriteResult.result);
    });

    it('should run FAQ consistency check and update provided signal', async () => {
        const courseId = 99;
        const faqText = 'Is this consistent with course FAQ?';
        const result: ConsistencyCheckResult = {
            consistent: true,
            improvement: [],
            faqIds: [],
        } as unknown as ConsistencyCheckResult;

        mockArtemisService.faqConsistencyCheck.mockReturnValue(of(result));

        comp.setText(faqText);

        const resultSignal: WritableSignal<ConsistencyCheckResult> = signal<ConsistencyCheckResult>(undefined as unknown as ConsistencyCheckResult);
        const action = new FaqConsistencyAction(TestBed.inject(ArtemisIntelligenceService), courseId, resultSignal);

        comp.registerAction(action);
        action.executeInCurrentEditor();

        expect(mockArtemisService.faqConsistencyCheck).toHaveBeenCalledTimes(1);
        expect(mockArtemisService.faqConsistencyCheck).toHaveBeenCalledWith(courseId, faqText);
        expect(resultSignal()).toEqual(result);
        expect(comp.getText()).toBe(faqText);
    });
});
