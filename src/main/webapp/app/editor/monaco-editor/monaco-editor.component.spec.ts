import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MonacoEditorBuildAnnotationType } from 'app/editor/monaco-editor/model/monaco-editor-build-annotation.model';
import { MonacoCodeEditorElement } from 'app/editor/monaco-editor/model/monaco-code-editor-element.model';
import { MonacoEditorLineDecorationsHoverButton } from 'app/editor/monaco-editor/model/monaco-editor-line-decorations-hover-button.model';
import { Annotation } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { MonacoEditorOptionPreset } from 'app/editor/monaco-editor/model/monaco-editor-option-preset.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import * as monaco from 'monaco-editor';

// Capture the global ResizeObserver provided by the test setup so it can be restored after each test.
const originalResizeObserver = globalThis.ResizeObserver;

describe('MonacoEditorComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<MonacoEditorComponent>;
    let comp: MonacoEditorComponent;

    const singleLineText = 'public class Main { }';
    const multiLineText = ['public class Main {', 'static void main() {', 'foo();', '}', '}'].join('\n');
    const textWithEmoticons = 'Hello :)';
    const textWithEmojis = 'Hello 🙂';

    const buildAnnotationArray: Annotation[] = [{ fileName: 'example.java', row: 1, column: 0, timestamp: 0, type: MonacoEditorBuildAnnotationType.ERROR, text: 'example error' }];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MonacoEditorComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(MonacoEditorComponent);
        comp = fixture.componentInstance;
        global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;
    });

    afterEach(() => {
        vi.restoreAllMocks();
        globalThis.ResizeObserver = originalResizeObserver;
    });

    const createMockDiffEditor = () => ({
        dispose: vi.fn(),
        updateOptions: vi.fn(),
        layout: vi.fn(),
        getModifiedEditor: vi.fn().mockReturnValue({
            getValue: vi.fn().mockReturnValue('modified content'),
            setValue: vi.fn(),
            onDidChangeModelContent: vi.fn().mockReturnValue({ dispose: vi.fn() }),
            onDidFocusEditorText: vi.fn().mockReturnValue({ dispose: vi.fn() }),
            updateOptions: vi.fn(),
            dispose: vi.fn(),
            addCommand: vi.fn(),
        }),
        getOriginalEditor: vi.fn().mockReturnValue({ getValue: vi.fn(), updateOptions: vi.fn(), onDidLayoutChange: vi.fn().mockReturnValue({ dispose: vi.fn() }) }),
        setModel: vi.fn(),
        onDidUpdateDiff: vi.fn().mockReturnValue({ dispose: vi.fn() }),
        getLineChanges: vi.fn(),
    });

    it('should catch error during action re-registration', () => {
        fixture.detectChanges();
        // Suppress console.warn as it causes test failure with fail-on-console
        const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

        const mockAction = {
            id: 'mock-action',
            run: vi.fn(),
            dispose: vi.fn(),
            register: vi.fn().mockImplementation(() => {
                throw new Error('Registration failed');
            }),
        } as any;
        comp.actions = [mockAction];

        // Should not throw
        expect(() => comp['reRegisterActions']()).not.toThrow();
        expect(mockAction.dispose).toHaveBeenCalled();
        expect(mockAction.register).toHaveBeenCalled();
        expect(consoleWarnSpy).toHaveBeenCalled();

        consoleWarnSpy.mockRestore();
    });

    it('should set the text of the editor', () => {
        fixture.detectChanges();
        comp.setText(singleLineText);
        expect(comp.getText()).toEqual(singleLineText);
    });

    it('should layout matching mode with fixed size', () => {
        fixture.detectChanges();
        const layoutSpy = vi.spyOn(comp['_editor'], 'layout');

        // Normal mode
        comp.layoutWithFixedSize(500, 300);
        expect(layoutSpy).toHaveBeenCalledWith({ width: 500, height: 300 });

        // Diff mode
        const mockDiffEditor = createMockDiffEditor();
        vi.spyOn(comp['monacoEditorService'], 'createStandaloneDiffEditor').mockReturnValue(mockDiffEditor as any);
        fixture.componentRef.setInput('mode', 'diff');
        fixture.detectChanges();

        comp.layoutWithFixedSize(600, 400);
        expect(mockDiffEditor.layout).toHaveBeenCalledWith({ width: 600, height: 400 });
    });

    it('should keep the requested side-by-side diff layout and honor toggling it off', () => {
        fixture.componentRef.setInput('renderSideBySide', true);
        fixture.detectChanges();
        const mockDiffEditor = createMockDiffEditor();
        vi.spyOn(comp['monacoEditorService'], 'createStandaloneDiffEditor').mockReturnValue(mockDiffEditor as any);

        fixture.componentRef.setInput('mode', 'diff');
        fixture.detectChanges();
        // With side-by-side requested, Monaco must NOT fall back to the inline (unified) view in narrow
        // containers, otherwise the split-view toggle appears to do nothing.
        expect(mockDiffEditor.updateOptions).toHaveBeenCalledWith(expect.objectContaining({ renderSideBySide: true, useInlineViewWhenSpaceIsLimited: false }));

        // Toggling side-by-side off restores the inline view.
        fixture.componentRef.setInput('renderSideBySide', false);
        fixture.detectChanges();
        expect(mockDiffEditor.updateOptions).toHaveBeenCalledWith(expect.objectContaining({ renderSideBySide: false, useInlineViewWhenSpaceIsLimited: true }));
    });

    it('should extract file path from model uri on change', () => {
        fixture.detectChanges();
        const emitSpy = vi.spyOn(comp.textChanged, 'emit');
        // Ensure getText returns content, as extraction relies on it
        vi.spyOn(comp, 'getText').mockReturnValue('content');

        // Mock model with specific URI
        const mockModel = {
            getValue: vi.fn().mockReturnValue('content'),
            uri: { toString: () => 'inmemory://model/1/path/to/file.ts', path: '/model/1/path/to/file.ts' },
        };
        vi.spyOn(comp['_editor'], 'getModel').mockReturnValue(mockModel as any);

        // Trigger change
        comp['emitTextChangeEvent']();

        expect(emitSpy).toHaveBeenCalledWith({ text: 'content', fileName: 'path/to/file.ts' });
    });

    it('should notify when the text changes', () => {
        const valueCallbackStub = vi.fn();
        fixture.detectChanges();
        comp.textChanged.subscribe(valueCallbackStub);
        comp.setText(singleLineText);
        expect(valueCallbackStub).toHaveBeenCalledOnce();
        expect(valueCallbackStub).toHaveBeenCalledWith({ text: singleLineText, fileName: expect.any(String) });
    });

    it('should only send a notification once per delay interval', () => {
        vi.useFakeTimers();
        const delay = 1000;
        const valueCallbackStub = vi.fn();
        fixture.componentRef.setInput('textChangedEmitDelay', delay);
        fixture.detectChanges();
        comp.textChanged.subscribe(valueCallbackStub);
        comp.setText('too early');
        vi.advanceTimersByTime(1);
        comp.setText(singleLineText);
        vi.advanceTimersByTime(delay);
        expect(valueCallbackStub).toHaveBeenCalledOnce();
        expect(valueCallbackStub).toHaveBeenCalledWith({ text: singleLineText, fileName: expect.any(String) });
        vi.useRealTimers();
    });

    it('should be set to readOnly depending on the input', () => {
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();
        expect(comp.isReadOnly()).toBeTruthy();
        fixture.componentRef.setInput('readOnly', false);
        fixture.detectChanges();
        expect(comp.isReadOnly()).toBeFalsy();
    });

    it('should display hidden line widgets', () => {
        const lineWidgetDiv = document.createElement('div');
        // This is the case e.g. for feedback items.
        lineWidgetDiv.classList.add(MonacoCodeEditorElement.CSS_HIDDEN_CLASS);
        const widgetId = 'test-widget';
        fixture.detectChanges();
        comp.setText(multiLineText);
        comp.addLineWidget(2, widgetId, lineWidgetDiv);
        expect(lineWidgetDiv.classList).not.toContain(MonacoCodeEditorElement.CSS_HIDDEN_CLASS);
    });

    it('should display build annotations', () => {
        const annotation = buildAnnotationArray[0];
        const buildAnnotationId = `monaco-editor-glyph-margin-widget-${annotation.fileName}:${annotation.row + 1}:${annotation.text}`;
        fixture.detectChanges();
        comp.setAnnotations(buildAnnotationArray, false);
        comp.setText(multiLineText);
        const element = document.getElementById(buildAnnotationId);
        expect(comp.buildAnnotations).toHaveLength(1);
        expect(element).not.toBeNull();
        expect(element).toEqual(comp.buildAnnotations[0].getGlyphMarginDomNode());
    });

    it('should not display build annotations that are out of bounds', () => {
        const annotation = buildAnnotationArray[0];
        const buildAnnotationId = `monaco-editor-glyph-margin-widget-${annotation.fileName}:${annotation.row + 1}:${annotation.text}`;
        fixture.detectChanges();
        comp.setAnnotations(buildAnnotationArray, false);
        comp.setText(singleLineText);
        const element = document.getElementById(buildAnnotationId);
        expect(comp.buildAnnotations).toHaveLength(1);
        // Ensure that the element is actually there, but not displayed in the DOM.
        expect(element).toBeNull();
        expect(comp.buildAnnotations[0].getGlyphMarginDomNode().id).toBe(buildAnnotationId);
    });

    it('should mark build annotations as outdated if specified', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);
        comp.setAnnotations(buildAnnotationArray, true);
        expect(comp.buildAnnotations).toHaveLength(1);
        expect(comp.buildAnnotations[0].isOutdated()).toBeTruthy();
    });

    it('should mark build annotations as outdated when a keyboard input is made', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);
        comp.setAnnotations(buildAnnotationArray, false);
        expect(comp.buildAnnotations).toHaveLength(1);
        expect(comp.buildAnnotations[0].isOutdated()).toBeFalsy();
        comp.triggerKeySequence('typing');
        expect(comp.buildAnnotations[0].isOutdated()).toBeTruthy();
    });

    it('should track highlighted line ranges with the specified classnames', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);
        comp.highlightLines(1, 2, 'test-class-name', 'test-margin-class-name');
        comp.highlightLines(4, 4, 'test-class-name', 'test-margin-class-name');
        // The mock editor does not render decorations to the DOM, so we assert on the tracked highlight ranges.
        // Two highlight elements, each representing a range.
        expect(comp.getLineHighlights()).toHaveLength(2);
    });

    it('should get the number of lines in the editor', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);
        expect(comp.getNumberOfLines()).toBe(5);
    });

    it('should pass the current line number to the line decorations hover button when clicked', () => {
        const clickCallbackStub = vi.fn();
        const className = 'testClass';
        const monacoMouseEvent = { target: { position: { lineNumber: 1 }, element: { classList: { contains: () => true } } } };
        fixture.detectChanges();
        comp.setText(multiLineText);
        comp.setLineDecorationsHoverButton(className, clickCallbackStub);
        comp.lineDecorationsHoverButton?.onClick(monacoMouseEvent as unknown as any);
        monacoMouseEvent.target.position.lineNumber = 3;
        comp.lineDecorationsHoverButton?.onClick(monacoMouseEvent as unknown as any);
        expect(clickCallbackStub).toHaveBeenNthCalledWith(1, 1);
        expect(clickCallbackStub).toHaveBeenNthCalledWith(2, 3);
    });

    it('should hide the line decorations hover button when no line number is available', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);
        comp.setLineDecorationsHoverButton('testClass', () => {});
        const button: MonacoEditorLineDecorationsHoverButton = comp.lineDecorationsHoverButton!;
        // Case 1 - by default
        expect(button.isVisible()).toBeFalsy();
        button.moveAndUpdate(1);
        expect(button.isVisible()).toBeTruthy();
        // Case 2 - undefined is passed as line number
        button.moveAndUpdate(undefined);
        expect(button.isVisible()).toBeFalsy();
    });

    it('should restore previous Monaco options when clearing the line decorations hover button', () => {
        fixture.detectChanges();
        const updateOptionsSpy = vi.spyOn((comp as any)._editor, 'updateOptions');
        const originalGetOption = (comp as any)._editor.getOption.bind((comp as any)._editor);
        const getOptionSpy = vi.spyOn((comp as any)._editor, 'getOption').mockImplementation((option: monaco.editor.EditorOption) => {
            if (option === monaco.editor.EditorOption.folding) {
                return false;
            }
            if (option === monaco.editor.EditorOption.lineDecorationsWidth) {
                return '1ch';
            }
            return originalGetOption(option);
        });

        updateOptionsSpy.mockClear();
        comp.setLineDecorationsHoverButton('testClass', () => {});
        comp.clearLineDecorationsHoverButton();

        expect(updateOptionsSpy).toHaveBeenNthCalledWith(2, { folding: false, lineDecorationsWidth: '1ch' });
        expect(getOptionSpy).toHaveBeenCalledWith(monaco.editor.EditorOption.folding);
        expect(getOptionSpy).toHaveBeenCalledWith(monaco.editor.EditorOption.lineDecorationsWidth);
    });

    it('should not overwrite editor options when clearing without an active line decorations hover button', () => {
        fixture.detectChanges();
        const updateOptionsSpy = vi.spyOn((comp as any)._editor, 'updateOptions');
        updateOptionsSpy.mockClear();

        comp.clearLineDecorationsHoverButton();

        expect(updateOptionsSpy).not.toHaveBeenCalled();
    });

    it('should not allow editing in readonly mode', () => {
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();
        comp.setText(singleLineText);
        comp.triggerKeySequence('some ignored input');
        expect(comp.getText()).toBe(singleLineText);
    });

    it('should dispose and destroy its widgets and annotations when destroyed', () => {
        fixture.detectChanges();
        comp.setAnnotations(buildAnnotationArray);
        comp.addLineWidget(1, 'widget', document.createElement('div'));
        comp.setLineDecorationsHoverButton('testClass', vi.fn());
        comp.highlightLines(1, 1);
        const disposeAnnotationSpy = vi.spyOn(comp.buildAnnotations[0], 'dispose');
        const disposeWidgetSpy = vi.spyOn(comp.lineWidgets[0], 'dispose');
        const disposeHoverButtonSpy = vi.spyOn(comp.lineDecorationsHoverButton!, 'dispose');
        const disposeLineHighlightSpy = vi.spyOn(comp.lineHighlights[0], 'dispose');
        comp.ngOnDestroy();
        expect(disposeWidgetSpy).toHaveBeenCalledOnce();
        expect(disposeAnnotationSpy).toHaveBeenCalledOnce();
        expect(disposeHoverButtonSpy).toHaveBeenCalledOnce();
        expect(disposeLineHighlightSpy).toHaveBeenCalledOnce();
    });

    it('should switch to and update the text of a single model', () => {
        fixture.detectChanges();
        comp.changeModel('file', multiLineText);
        expect(comp.getText()).toBe(multiLineText);
        expect(comp.models).toHaveLength(1);
        expect(comp.models[0].getValue()).toBe(multiLineText);
        comp.changeModel('file', singleLineText);
        expect(comp.getText()).toBe(singleLineText);
        expect(comp.models).toHaveLength(1);
        expect(comp.models[0].getValue()).toBe(singleLineText);
    });

    it('should initialize an empty model if no text is specified', () => {
        fixture.detectChanges();
        comp.changeModel('file');
        expect(comp.getText()).toBe('');
        expect(comp.models).toHaveLength(1);
        expect(comp.models[0].getValue()).toBe('');
    });

    it('should switch between multiple models without changing their content', () => {
        fixture.detectChanges();
        // Set initial values
        comp.changeModel('file1', singleLineText);
        comp.changeModel('file2', multiLineText);
        expect(comp.getText()).toBe(multiLineText);
        // Switch without changing
        comp.changeModel('file1');
        expect(comp.getText()).toBe(singleLineText);
        comp.changeModel('file2');
        expect(comp.getText()).toBe(multiLineText);
    });

    it('should dispose its models when destroyed', () => {
        fixture.detectChanges();
        comp.changeModel('file1', singleLineText);
        const model = comp.models[0];
        const modelDisposeSpy = vi.spyOn(model, 'dispose');
        comp.ngOnDestroy();
        expect(comp.models).toHaveLength(0);
        expect(modelDisposeSpy).toHaveBeenCalledOnce();
        expect(model.isDisposed()).toBeTruthy();
    });

    it('should set the start line number via a line-number formatter', () => {
        fixture.detectChanges();
        comp.changeModel('file', multiLineText);
        const updateOptionsSpy = vi.spyOn(comp.getActiveEditor(), 'updateOptions');
        comp.setStartLineNumber(5);
        // The mock editor does not render the line-number gutter; assert on the formatter passed to Monaco instead.
        const lineNumbersFn = updateOptionsSpy.mock.calls.at(-1)![0]!.lineNumbers as (n: number) => string;
        expect(lineNumbersFn(1)).toBe('5');
        expect(lineNumbersFn(5)).toBe('9');
    });

    it('should apply option presets to the editor', () => {
        fixture.detectChanges();
        const preset = new MonacoEditorOptionPreset({ lineNumbers: 'off' });
        const applySpy = vi.spyOn(preset, 'apply');
        comp.applyOptionPreset(preset);
        expect(applySpy).toHaveBeenCalledOnce();
        expect(applySpy).toHaveBeenCalledWith(comp['_editor']);
    });

    it('should convert text emoticons to emojis using convertTextToEmoji', () => {
        fixture.detectChanges();
        const result = comp.convertTextToEmoji(textWithEmoticons);
        expect(result).toBe(textWithEmojis);
    });

    it('should detect if text is converted to emoji using isConvertedToEmoji', () => {
        fixture.detectChanges();
        const isConverted = comp.isConvertedToEmoji(textWithEmoticons, textWithEmojis);
        expect(isConverted).toBeTruthy();

        const notConverted = comp.isConvertedToEmoji(textWithEmojis, textWithEmojis);
        expect(notConverted).toBeFalsy();
    });

    it('should not change the editor text if no conversion is needed', () => {
        fixture.detectChanges();
        const originalText = 'Hello 😊';
        comp.setText(originalText);
        expect(comp.getText()).toBe(originalText);

        comp.setText(originalText);
        expect(comp.getText()).toBe(originalText);
    });

    it('should register a listener for model content changes', () => {
        const listenerStub = vi.fn();
        fixture.detectChanges();
        const disposable = comp.onDidChangeModelContent(listenerStub);
        comp.setText(singleLineText);
        expect(listenerStub).toHaveBeenCalled();
        disposable.dispose();
    });

    it('should register a listener for selection changes', () => {
        const listenerStub = vi.fn();
        fixture.detectChanges();
        comp.setText('hallo welt, hello world');
        const disposable = comp.onSelectionChange(listenerStub);
        comp.setSelection({ startLineNumber: 1, startColumn: 1, endLineNumber: 1, endColumn: 10 });
        expect(listenerStub).toHaveBeenCalled();
        disposable.dispose();
    });

    it('should retrieve the editor model', () => {
        fixture.detectChanges();
        comp.setText(singleLineText);
        const model = comp.getModel();
        expect(model).not.toBeNull();
        expect(model?.getValue()).toBe(singleLineText);
    });

    it('should return empty line content when no model is active', () => {
        fixture.detectChanges();
        comp.disposeModels();

        expect(comp.getLineContent(1)).toBe('');
    });

    it('should get the content of a specific line', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);
        const lineContent = comp.getLineContent(2);
        expect(lineContent).toBe('static void main() {');
    });

    it('should handle invalid line numbers in getLineContent', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);

        // Invalid line numbers
        expect(() => comp.getLineContent(0)).toThrow();
        expect(() => comp.getLineContent(-1)).toThrow();
        expect(() => comp.getLineContent(999)).toThrow();

        // Empty line
        comp.setText('line1\n\nline3');
        expect(comp.getLineContent(2)).toBe('');
    });

    it('should delegate setPosition to the Monaco editor', () => {
        fixture.detectChanges();
        const setPositionSpy = vi.spyOn((comp as any)._editor, 'setPosition');
        const position = { lineNumber: 3, column: 2 };

        comp.setPosition(position);

        expect(setPositionSpy).toHaveBeenCalledOnce();
        expect(setPositionSpy).toHaveBeenCalledWith(position);
    });

    it('should delete a combined emoji entirely on backspace press', () => {
        fixture.detectChanges();
        const combinedEmoji = '🇩🇪';
        comp.setText(combinedEmoji);

        const lines = combinedEmoji.split('\n');
        const lastLine = lines[lines.length - 1];
        comp.setPosition({ lineNumber: lines.length, column: lastLine.length + 1 });

        const commandId = comp.getCustomBackspaceCommandId();
        expect(commandId).toBeDefined();

        comp['_editor'].trigger('keyboard', commandId!, null);

        expect(comp.getText()).toBe('');
    });

    it('should delete combined emojis one cluster at a time on backspace press', () => {
        fixture.detectChanges();

        const emoji1 = '🇩🇪';
        const emoji2 = '🇫🇷';
        const combinedText = emoji1 + emoji2;

        comp.setText(combinedText);
        comp.setPosition({ lineNumber: 1, column: combinedText.length + 1 });

        let commandId = comp.getCustomBackspaceCommandId();
        expect(commandId).toBeDefined();
        comp['_editor'].trigger('keyboard', commandId!, null);

        expect(comp.getText()).toEqual(emoji1);

        comp.setPosition({ lineNumber: 1, column: emoji1.length + 1 });

        commandId = comp.getCustomBackspaceCommandId();
        expect(commandId).toBeDefined();
        comp['_editor'].trigger('keyboard', commandId!, null);

        expect(comp.getText()).toBe('');
    });

    it('should delete only one emoji at a time in mixed text', () => {
        fixture.detectChanges();

        const textWithEmoji = 'Hello 🇩🇪 World!';
        comp.setText(textWithEmoji);

        comp.setPosition({ lineNumber: 1, column: textWithEmoji.length - 6 });

        const commandId = comp.getCustomBackspaceCommandId();
        expect(commandId).toBeDefined();

        comp['_editor'].trigger('keyboard', commandId!, null);

        expect(comp.getText()).toBe('Hello  World!');
    });

    it('should place the cursor correctly after deleting an emoji', () => {
        fixture.detectChanges();

        const fullText = 'Hello 👋 World!';
        comp.setText(fullText);
        comp.setPosition({ lineNumber: 1, column: 9 });

        const commandId = comp.getCustomBackspaceCommandId();
        comp['_editor'].trigger('keyboard', commandId!, null);

        const newPosition = comp.getPosition();
        expect(newPosition.column).toBe(7);
    });

    it('should initialize diff mode when mode input is set to diff', () => {
        fixture.componentRef.setInput('mode', 'diff');
        fixture.detectChanges();

        expect(comp['_diffEditor']).toBeDefined();
    });

    it('should return the active editor correctly in normal mode', () => {
        fixture.detectChanges();
        const activeEditor = comp.getActiveEditor();
        expect(activeEditor).toBe(comp['_editor']);
    });

    it('should return undefined for getDiffText when not in diff mode', () => {
        fixture.detectChanges();
        const diffText = comp.getDiffText();
        expect(diffText).toBeUndefined();
    });

    it('should return undefined for getModifiedEditor when not in diff mode', () => {
        fixture.detectChanges();
        const modifiedEditor = comp.getModifiedEditor();
        expect(modifiedEditor).toBeUndefined();
    });

    it('should dispose selection change listeners on destroy', () => {
        fixture.detectChanges();

        const mockDisposable = { dispose: vi.fn() };
        comp['selectionChangeListeners'] = [{ listener: vi.fn(), disposable: mockDisposable }];

        comp.ngOnDestroy();

        expect(mockDisposable.dispose).toHaveBeenCalled();
        expect(comp['selectionChangeListeners']).toHaveLength(0);
    });

    it('should handle multiple models', () => {
        fixture.detectChanges();

        comp.changeModel('file1', 'content1');
        comp.changeModel('file2', 'content2');

        expect(comp.models).toHaveLength(2);
        expect(comp.models[0].getValue()).toBe('content1');
        expect(comp.models[1].getValue()).toBe('content2');
    });

    it('should track text changed emit timeouts', () => {
        fixture.componentRef.setInput('textChangedEmitDelay', 1000);
        fixture.detectChanges();

        expect(comp['textChangedEmitTimeouts']).toBeDefined();
    });

    it('should create diff editor lazily when entering diff mode', () => {
        fixture.detectChanges();
        // Initially undefined
        expect(comp['_diffEditor']).toBeUndefined();

        // Mock createStandaloneDiffEditor
        const mockDiffEditor = createMockDiffEditor();
        const createDiffSpy = vi.spyOn(comp['monacoEditorService'], 'createStandaloneDiffEditor').mockReturnValue(mockDiffEditor as any);

        fixture.componentRef.setInput('mode', 'diff');
        fixture.detectChanges();

        expect(createDiffSpy).toHaveBeenCalled();
        expect(comp['_diffEditor']).toBeDefined();
    });

    it('should dispose diff editor when leaving diff mode and sync content if needed', () => {
        fixture.detectChanges();

        const editorSetValueSpy = vi.spyOn(comp['_editor'], 'setValue').mockImplementation(() => {});

        // Setup diff mode first
        const mockDiffEditor = createMockDiffEditor();
        vi.spyOn(comp['monacoEditorService'], 'createStandaloneDiffEditor').mockReturnValue(mockDiffEditor as any);

        fixture.componentRef.setInput('mode', 'diff');
        fixture.detectChanges();

        expect(comp['_diffEditor']).toBeDefined();

        // Switch back to normal
        fixture.componentRef.setInput('mode', 'normal');
        fixture.detectChanges();

        expect(mockDiffEditor.dispose).toHaveBeenCalled();
        // Since we share the model, valid code implies we don't manually set value back
        expect(editorSetValueSpy).not.toHaveBeenCalled();
    });

    it('should apply diff content in diff mode', () => {
        fixture.detectChanges();

        const setValueSpy = vi.fn();
        const layoutSpy = vi.fn();

        const mockDiffEditor = createMockDiffEditor();
        // Override specific spies
        mockDiffEditor.layout = layoutSpy;
        const modifiedEditorMock = {
            getValue: vi.fn().mockReturnValue(''),
            setValue: setValueSpy,
            onDidChangeModelContent: vi.fn(),
            onDidFocusEditorText: vi.fn(),
            updateOptions: vi.fn(),
            dispose: vi.fn(),
            addCommand: vi.fn(),
        };
        mockDiffEditor.getModifiedEditor.mockReturnValue(modifiedEditorMock);

        vi.spyOn(comp['monacoEditorService'], 'createStandaloneDiffEditor').mockReturnValue(mockDiffEditor as any);

        fixture.componentRef.setInput('mode', 'diff');
        fixture.detectChanges();

        comp.applyDiffContent('new content');

        expect(setValueSpy).toHaveBeenCalledWith('new content');
        expect(layoutSpy).toHaveBeenCalled();
    });

    it('should share the same model between normal editor and modified diff editor', () => {
        fixture.detectChanges();
        const mockModel = {
            getValue: vi.fn().mockReturnValue('shared content'),
            getLanguageId: vi.fn().mockReturnValue('typescript'),
            dispose: vi.fn(),
        };
        // Ensure the normal editor returns this model
        vi.spyOn(comp['_editor'], 'getModel').mockReturnValue(mockModel as any);

        const setModelSpy = vi.fn();
        const mockDiffEditor = createMockDiffEditor();
        mockDiffEditor.setModel = setModelSpy;

        vi.spyOn(comp['monacoEditorService'], 'createStandaloneDiffEditor').mockReturnValue(mockDiffEditor as any);

        fixture.componentRef.setInput('mode', 'diff');
        fixture.detectChanges();

        // Verify that setModel was called with the shared model as 'modified'
        expect(setModelSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                modified: mockModel,
            }),
        );
    });
});
