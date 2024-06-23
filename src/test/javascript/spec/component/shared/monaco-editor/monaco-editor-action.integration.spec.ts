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

    it.each([
        { action: new MonacoBoldAction(), textWithoutDelimiters: 'Here is some bold text.', textWithDelimiters: '**Here is some bold text.**' },
        { action: new MonacoItalicAction(), textWithoutDelimiters: 'Here is some italic text.', textWithDelimiters: '*Here is some italic text.*' },
        { action: new MonacoUnderlineAction(), textWithoutDelimiters: 'Here is some underlined text.', textWithDelimiters: '<ins>Here is some underlined text.</ins>' },
        { action: new MonacoCodeAction(), textWithoutDelimiters: 'Here is some code.', textWithDelimiters: '`Here is some code.`' },
        { action: new MonacoColorAction(), textWithoutDelimiters: 'Here is some red.', textWithDelimiters: '<span class="red">Here is some red.</span>', actionArgs: 'red' },
        { action: new MonacoCodeBlockAction(), textWithoutDelimiters: 'public void main() { }', textWithDelimiters: '```java\npublic void main() { }\n```' },
        {
            action: new MonacoQuoteAction(),
            initialText: '> Quote',
            textToType: ' some other text',
            textWithDelimiters: '> Quote some other text',
            textWithoutDelimiters: 'Quote some other text',
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
            actionArgs?: string;
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
        actionArgs?: string,
        textToType?: string,
        initialText?: string,
    ): void {
        const runSpy = jest.spyOn(action, 'run');
        comp.registerAction(action);
        // Position
        comp.triggerAction(action.id, actionArgs);
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
        comp.triggerAction(action.id, actionArgs);
        expect(comp.getText()).toBe(textWithoutDelimiters);
        comp.triggerAction(action.id, actionArgs);
        expect(comp.getText()).toBe(textWithDelimiters);
        expect(runSpy).toHaveBeenCalledTimes(3);
    }
});
