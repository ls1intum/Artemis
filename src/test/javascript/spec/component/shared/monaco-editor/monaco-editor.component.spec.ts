import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { BehaviorSubject } from 'rxjs';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { MonacoEditorAnnotationType } from 'app/shared/monaco-editor/model/monaco-editor-annotation.model';

describe('MonacoEditorComponent', () => {
    let fixture: ComponentFixture<MonacoEditorComponent>;
    let comp: MonacoEditorComponent;
    let mockThemeService: ThemeService;

    const singleLineText = 'public class Main { }';
    const multiLineText = ['public class Main {', 'static void main() {', 'foo();', '}', '}'].join('\n');

    const buildAnnotationArray: Annotation[] = [{ fileName: 'example.java', row: 1, column: 0, timestamp: 0, type: MonacoEditorAnnotationType.ERROR, text: 'example error' }];

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
                global.ResizeObserver = jest.fn().mockImplementation((...args) => new MockResizeObserver(args));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).toBeDefined();
    });

    it('should set the text of the editor', () => {
        fixture.detectChanges();
        comp.setText(singleLineText);
        expect(comp.getText()).toEqual(singleLineText);
    });

    it('should notify when the text changes', () => {
        const valueCallbackSpy = jest.fn();
        fixture.detectChanges();
        comp.textChanged.subscribe(valueCallbackSpy);
        comp.setText(singleLineText);
        expect(valueCallbackSpy).toHaveBeenCalledExactlyOnceWith(singleLineText);
    });

    it('should only send a notification once per delay interval', fakeAsync(() => {
        const delay = 1000;
        const valueCallbackSpy = jest.fn();
        comp.textChangedEmitDelay = delay;
        fixture.detectChanges();
        comp.textChanged.subscribe(valueCallbackSpy);
        comp.setText('too early');
        tick(1);
        comp.setText(singleLineText);
        tick(delay);
        expect(valueCallbackSpy).toHaveBeenCalledExactlyOnceWith(singleLineText);
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
        const subscribeSpy = jest.spyOn(mockThemeService, 'getCurrentThemeObservable').mockReturnValue(themeSubject.asObservable());
        const changeThemeSpy = jest.spyOn(comp, 'changeTheme');
        fixture.detectChanges();
        themeSubject.next(Theme.DARK);
        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(changeThemeSpy).toHaveBeenCalledTimes(2);
        expect(changeThemeSpy).toHaveBeenNthCalledWith(1, Theme.LIGHT);
        expect(changeThemeSpy).toHaveBeenNthCalledWith(2, Theme.DARK);
    });

    it('should display hidden line widgets', () => {
        const lineWidgetDiv = document.createElement('div');
        lineWidgetDiv.style.display = 'none';
        const widgetId = 'test-widget';
        fixture.detectChanges();
        comp.setText(multiLineText);
        comp.addLineWidget(2, widgetId, lineWidgetDiv);
        expect(lineWidgetDiv.style.display).toBe('unset');
    });

    it('should display build annotations', () => {
        const annotation = buildAnnotationArray[0];
        const buildAnnotationId = `monaco-editor-annotation-${annotation.fileName}:${annotation.row + 1}:${annotation.text}`;
        fixture.detectChanges();
        comp.setAnnotations(buildAnnotationArray);
        comp.setText(multiLineText);
        const element = document.getElementById(buildAnnotationId);
        expect(element).not.toBeNull();
        expect(element!.style.visibility).toBe('visible');
    });

    it('should hide build annotations that are out of bounds', () => {
        const annotation = buildAnnotationArray[0];
        const buildAnnotationId = `monaco-editor-annotation-${annotation.fileName}:${annotation.row + 1}:${annotation.text}`;
        fixture.detectChanges();
        comp.setAnnotations(buildAnnotationArray);
        comp.setText(singleLineText);
        const element = document.getElementById(buildAnnotationId);
        expect(element).not.toBeNull();
        expect(element!.style.visibility).toBe('hidden');
    });
});
