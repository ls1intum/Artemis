import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { BehaviorSubject } from 'rxjs';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { MonacoEditorBuildAnnotationType } from 'app/shared/monaco-editor/model/monaco-editor-build-annotation.model';
import { MonacoCodeEditorElement } from 'app/shared/monaco-editor/model/monaco-code-editor-element.model';

describe('MonacoEditorComponent', () => {
    let fixture: ComponentFixture<MonacoEditorComponent>;
    let comp: MonacoEditorComponent;
    let mockThemeService: ThemeService;

    const singleLineText = 'public class Main { }';
    const multiLineText = ['public class Main {', 'static void main() {', 'foo();', '}', '}'].join('\n');

    const buildAnnotationArray: Annotation[] = [{ fileName: 'example.java', row: 1, column: 0, timestamp: 0, type: MonacoEditorBuildAnnotationType.ERROR, text: 'example error' }];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MonacoEditorModule],
            declarations: [MonacoEditorComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                mockThemeService = TestBed.inject(ThemeService);
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
        comp.textChangedEmitDelay = delay;
        fixture.detectChanges();
        comp.textChanged.subscribe(valueCallbackStub);
        comp.setText('too early');
        tick(1);
        comp.setText(singleLineText);
        tick(delay);
        expect(valueCallbackStub).toHaveBeenCalledExactlyOnceWith(singleLineText);
    }));

    it('should be set to readOnly depending on the input', () => {
        comp.readOnly = true;
        fixture.detectChanges();
        expect(comp.isReadOnly()).toBeTrue();
        comp.readOnly = false;
        fixture.detectChanges();
        expect(comp.isReadOnly()).toBeFalse();
    });

    it('should adjust its theme to the global theme', () => {
        const themeSubject = new BehaviorSubject<Theme>(Theme.LIGHT);
        const subscribeStub = jest.spyOn(mockThemeService, 'getCurrentThemeObservable').mockReturnValue(themeSubject.asObservable());
        const changeThemeSpy = jest.spyOn(comp, 'changeTheme');
        fixture.detectChanges();
        themeSubject.next(Theme.DARK);
        expect(subscribeStub).toHaveBeenCalledOnce();
        expect(changeThemeSpy).toHaveBeenCalledTimes(2);
        expect(changeThemeSpy).toHaveBeenNthCalledWith(1, Theme.LIGHT);
        expect(changeThemeSpy).toHaveBeenNthCalledWith(2, Theme.DARK);
    });

    it('should unsubscribe from the global theme when destroyed', () => {
        const themeSubject = new BehaviorSubject<Theme>(Theme.LIGHT);
        const subscribeStub = jest.spyOn(mockThemeService, 'getCurrentThemeObservable').mockReturnValue(themeSubject.asObservable());
        fixture.detectChanges();
        const unsubscribeStub = jest.spyOn(comp.themeSubscription!, 'unsubscribe').mockImplementation();
        comp.ngOnDestroy();
        expect(subscribeStub).toHaveBeenCalledOnce();
        expect(unsubscribeStub).toHaveBeenCalledOnce();
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
        expect(comp.editorBuildAnnotations).toHaveLength(1);
        expect(element).not.toBeNull();
        expect(element).toEqual(comp.editorBuildAnnotations[0].getGlyphMarginDomNode());
    });

    it('should not display build annotations that are out of bounds', () => {
        const annotation = buildAnnotationArray[0];
        const buildAnnotationId = `monaco-editor-glyph-margin-widget-${annotation.fileName}:${annotation.row + 1}:${annotation.text}`;
        fixture.detectChanges();
        comp.setAnnotations(buildAnnotationArray, false);
        comp.setText(singleLineText);
        const element = document.getElementById(buildAnnotationId);
        expect(comp.editorBuildAnnotations).toHaveLength(1);
        // Ensure that the element is actually there, but not displayed in the DOM.
        expect(element).toBeNull();
        expect(comp.editorBuildAnnotations[0].getGlyphMarginDomNode().id).toBe(buildAnnotationId);
    });

    it('should mark build annotations as outdated if specified', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);
        comp.setAnnotations(buildAnnotationArray, true);
        expect(comp.editorBuildAnnotations).toHaveLength(1);
        expect(comp.editorBuildAnnotations[0].isOutdated()).toBeTrue();
    });

    it('should mark build annotations as outdated when a keyboard input is made', () => {
        fixture.detectChanges();
        comp.setText(multiLineText);
        comp.setAnnotations(buildAnnotationArray, false);
        expect(comp.editorBuildAnnotations).toHaveLength(1);
        expect(comp.editorBuildAnnotations[0].isOutdated()).toBeFalse();
        comp.triggerKeySequence('typing');
        expect(comp.editorBuildAnnotations[0].isOutdated()).toBeTrue();
    });

    it('should not allow editing in readonly mode', () => {
        comp.readOnly = true;
        fixture.detectChanges();
        comp.setText(singleLineText);
        comp.triggerKeySequence('some ignored input');
        expect(comp.getText()).toBe(singleLineText);
    });

    it('should dispose and destroy its widgets and annotations when destroyed', () => {
        fixture.detectChanges();
        comp.setAnnotations(buildAnnotationArray);
        comp.addLineWidget(1, 'widget', document.createElement('div'));
        const disposeAnnotationSpy = jest.spyOn(comp.editorBuildAnnotations[0], 'dispose');
        const disposeWidgetSpy = jest.spyOn(comp.lineWidgets[0], 'dispose');
        comp.ngOnDestroy();
        expect(disposeWidgetSpy).toHaveBeenCalledOnce();
        expect(disposeAnnotationSpy).toHaveBeenCalledOnce();
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
});
