import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';
import { MonacoEditorBuildAnnotationType } from 'app/shared/monaco-editor/model/monaco-editor-build-annotation.model';
import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';
import { MonacoEditorLineDecorationsHoverButton } from 'app/shared/monaco-editor/model/monaco-editor-line-decorations-hover-button.model';
import { Annotation } from 'app/exercises/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { MonacoEditorOptionPreset } from 'app/shared/monaco-editor/model/monaco-editor-option-preset.model';

describe('MonacoEditorComponent', () => {
    let fixture: ComponentFixture<MonacoEditorComponent>;
    let comp: MonacoEditorComponent;

    const singleLineText = 'public class Main { }';
    const multiLineText = ['public class Main {', 'static void main() {', 'foo();', '}', '}'].join('\n');
    const textWithEmoticons = 'Hello :)';
    const textWithEmojis = 'Hello 🙂';
    const thumbsUpShortcut = ':+1:';
    const thumbsUpEmoji = '👍';

    const buildAnnotationArray: Annotation[] = [{ fileName: 'example.java', row: 1, column: 0, timestamp: 0, type: MonacoEditorBuildAnnotationType.ERROR, text: 'example error' }];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MonacoEditorComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MonacoEditorComponent);
                comp = fixture.componentInstance;
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set the text of the editor', () => {
        fixture.detectChanges();
        comp.setText(singleLineText);
        expect(comp.getText()).toEqual(singleLineText);
    });

    it('should notify when the text changes', () => {
        const valueCallbackStub = jest.fn();
        fixture.detectChanges();
        comp.textChanged.subscribe(valueCallbackStub);
        comp.setText(singleLineText);
        expect(valueCallbackStub).toHaveBeenCalledExactlyOnceWith(singleLineText);
    });

    it('should only send a notification once per delay interval', fakeAsync(() => {
        const delay = 1000;
        const valueCallbackStub = jest.fn();
        fixture.componentRef.setInput('textChangedEmitDelay', delay);
        fixture.detectChanges();
        comp.textChanged.subscribe(valueCallbackStub);
        comp.setText('too early');
        tick(1);
        comp.setText(singleLineText);
        tick(delay);
        expect(valueCallbackStub).toHaveBeenCalledExactlyOnceWith(singleLineText);
    }));

    it('should be set to readOnly depending on the input', () => {
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();
        expect(comp.isReadOnly()).toBeTrue();
        fixture.componentRef.setInput('readOnly', false);
        fixture.detectChanges();
        expect(comp.isReadOnly()).toBeFalse();
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
        expect(comp.buildAnnotations[0].isOutdated()).toBeTrue();
    });

    it('should mark build annotations as outdated when a keyboard input is made', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);
        comp.setAnnotations(buildAnnotationArray, false);
        expect(comp.buildAnnotations).toHaveLength(1);
        expect(comp.buildAnnotations[0].isOutdated()).toBeFalse();
        comp.triggerKeySequence('typing');
        expect(comp.buildAnnotations[0].isOutdated()).toBeTrue();
    });

    it('should highlight line ranges with the specified classnames', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);
        comp.highlightLines(1, 2, 'test-class-name', 'test-margin-class-name');
        comp.highlightLines(4, 4, 'test-class-name', 'test-margin-class-name');
        // The editor must be large enough to display all lines.
        comp.layoutWithFixedSize(400, 400);
        const documentHighlightedLines = document.getElementsByClassName('test-class-name');
        const documentHighlightedMargins = document.getElementsByClassName('test-margin-class-name');
        // Two highlight elements, each representing a range.
        expect(comp.getLineHighlights()).toHaveLength(2);
        // In total, three lines (including their margins) are highlighted.
        expect(documentHighlightedLines).toHaveLength(3);
        expect(documentHighlightedMargins).toHaveLength(3);
    });

    it('should get the number of lines in the editor', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);
        expect(comp.getNumberOfLines()).toBe(5);
    });

    it('should pass the current line number to the line decorations hover button when clicked', () => {
        const clickCallbackStub = jest.fn();
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
        expect(button.isVisible()).toBeFalse();
        button.moveAndUpdate(1);
        expect(button.isVisible()).toBeTrue();
        // Case 2 - undefined is passed as line number
        button.moveAndUpdate(undefined);
        expect(button.isVisible()).toBeFalse();
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
        comp.setLineDecorationsHoverButton('testClass', jest.fn());
        comp.highlightLines(1, 1);
        const disposeAnnotationSpy = jest.spyOn(comp.buildAnnotations[0], 'dispose');
        const disposeWidgetSpy = jest.spyOn(comp.lineWidgets[0], 'dispose');
        const disposeHoverButtonSpy = jest.spyOn(comp.lineDecorationsHoverButton!, 'dispose');
        const disposeLineHighlightSpy = jest.spyOn(comp.lineHighlights[0], 'dispose');
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
        const modelDisposeSpy = jest.spyOn(model, 'dispose');
        comp.ngOnDestroy();
        expect(comp.models).toBeEmpty();
        expect(modelDisposeSpy).toHaveBeenCalledOnce();
        expect(model.isDisposed()).toBeTrue();
    });

    it('should correctly set the start line number', () => {
        fixture.detectChanges();
        comp.changeModel('file', multiLineText);
        comp.setStartLineNumber(5);
        // Ensure that the editor is large enough to display all lines.
        comp.layoutWithFixedSize(400, 400);
        const lineNumbers = fixture.debugElement.nativeElement.querySelectorAll('.line-numbers');
        expect(lineNumbers).toHaveLength(5);
        expect([...lineNumbers].map((elem: HTMLElement) => elem.textContent)).toContainAllValues(['5', '6', '7', '8', '9']);
    });

    it('should apply option presets to the editor', () => {
        fixture.detectChanges();
        const preset = new MonacoEditorOptionPreset({ lineNumbers: 'off' });
        const applySpy = jest.spyOn(preset, 'apply');
        comp.applyOptionPreset(preset);
        expect(applySpy).toHaveBeenCalledExactlyOnceWith(comp['_editor']);
    });

    it('should convert text emoticons to emojis using convertTextToEmoji', () => {
        fixture.detectChanges();
        const result = comp.convertTextToEmoji(textWithEmoticons);
        expect(result).toBe(textWithEmojis);
    });

    it('should detect if text is converted to emoji using isConvertedToEmoji', () => {
        fixture.detectChanges();
        const isConverted = comp.isConvertedToEmoji(textWithEmoticons, textWithEmojis);
        expect(isConverted).toBeTrue();

        const notConverted = comp.isConvertedToEmoji(textWithEmojis, textWithEmojis);
        expect(notConverted).toBeFalse();
    });

    it('should not change the editor text if no conversion is needed', () => {
        fixture.detectChanges();
        const originalText = 'Hello 😊';
        comp.setText(originalText);
        expect(comp.getText()).toBe(originalText);

        comp.setText(originalText);
        expect(comp.getText()).toBe(originalText);
    });

    it('should detect if :+1: is converted to thumbs-up emoji', () => {
        fixture.detectChanges();
        const isConverted = comp.isConvertedToEmoji(thumbsUpShortcut, thumbsUpEmoji);
        expect(isConverted).toBeTrue();

        const notConverted = comp.isConvertedToEmoji(thumbsUpEmoji, thumbsUpEmoji);
        expect(notConverted).toBeFalse();
    });

    it('should update the editor text with the thumbs-up emoji when :+1: is typed', () => {
        fixture.detectChanges();
        comp.setText(thumbsUpShortcut);
        expect(comp.getText()).toBe(thumbsUpEmoji);
    });
});
