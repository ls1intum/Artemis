import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/monaco-diff-editor.component';

describe('MonacoDiffEditorComponent', () => {
    let fixture: ComponentFixture<MonacoDiffEditorComponent>;
    let comp: MonacoDiffEditorComponent;
    let mockThemeService: ThemeService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MonacoEditorModule],
            declarations: [MonacoDiffEditorComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
                mockThemeService = TestBed.inject(ThemeService);
                fixture = TestBed.createComponent(MonacoDiffEditorComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should adjust its theme to the global theme', () => {
        const changeThemeSpy = jest.spyOn(comp, 'changeTheme');
        fixture.detectChanges();
        mockThemeService.applyThemePreference(Theme.DARK);
        TestBed.flushEffects();

        expect(changeThemeSpy).toHaveBeenCalledTimes(2);
        expect(changeThemeSpy).toHaveBeenNthCalledWith(1, Theme.LIGHT);
        expect(changeThemeSpy).toHaveBeenNthCalledWith(2, Theme.DARK);
    });

    it('should dispose its listeners and subscriptions when destroyed', () => {
        fixture.detectChanges();
        const resizeObserverDisconnectSpy = jest.spyOn(comp.resizeObserver!, 'disconnect');
        const themeSubscriptionUnsubscribeSpy = jest.spyOn(comp.themeSubscription!, 'unsubscribe');
        const listenerDisposeSpies = comp.listeners.map((listener) => jest.spyOn(listener, 'dispose'));
        comp.ngOnDestroy();
        for (const spy of [resizeObserverDisconnectSpy, themeSubscriptionUnsubscribeSpy, ...listenerDisposeSpies]) {
            expect(spy).toHaveBeenCalledOnce();
        }
    });

    it('should update the size of its container and layout the editor', () => {
        const layoutSpy = jest.spyOn(comp, 'layout');
        fixture.detectChanges();
        const element = document.createElement('div');
        comp.monacoDiffEditorContainerElement = element;
        comp.adjustHeightAndLayout(100);
        expect(element.style.height).toBe('100px');
        expect(layoutSpy).toHaveBeenCalledOnce();
    });

    it('should set the text of the editor', () => {
        const original = 'some original content';
        const modified = 'some modified content';
        fixture.detectChanges();
        comp.setFileContents(original, 'originalFileName.java', modified, 'modifiedFileName.java');
        expect(comp.getText()).toEqual({ original, modified });
    });

    it('should notify about its readiness to display', async () => {
        const readyCallbackStub = jest.fn();
        comp.onReadyForDisplayChange.subscribe(readyCallbackStub);
        fixture.detectChanges();
        comp.setFileContents('original', 'file', 'modified', 'file');
        // Wait for the diff computation, which is handled by Monaco.
        await new Promise((r) => setTimeout(r, 200));
        expect(readyCallbackStub).toHaveBeenCalledTimes(2);
        expect(readyCallbackStub).toHaveBeenNthCalledWith(1, false);
        expect(readyCallbackStub).toHaveBeenNthCalledWith(2, true);
    });
});
