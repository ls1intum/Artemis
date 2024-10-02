import { TestBed } from '@angular/core/testing';
import * as monaco from 'monaco-editor';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { MonacoEditorService } from '../../../../../../main/webapp/app/shared/monaco-editor/monaco-editor.service';
import { ArtemisTestModule } from '../../../test.module';
import { CUSTOM_MARKDOWN_LANGUAGE_ID } from 'app/shared/monaco-editor/model/languages/monaco-custom-markdown.language';
import { BehaviorSubject } from 'rxjs';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';

describe('MonacoEditorService', () => {
    let monacoEditorService: MonacoEditorService;
    let setThemeSpy: jest.SpyInstance;
    let registerLanguageSpy: jest.SpyInstance;
    const themeSubject = new BehaviorSubject(Theme.LIGHT);

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        });
        // Avoids an error with the diff editor, which uses a ResizeObserver.
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        registerLanguageSpy = jest.spyOn(monaco.languages, 'register');
        setThemeSpy = jest.spyOn(monaco.editor, 'setTheme');
        const themeService = TestBed.inject(ThemeService);
        jest.spyOn(themeService, 'getCurrentThemeObservable').mockReturnValue(themeSubject.asObservable());
        monacoEditorService = TestBed.inject(MonacoEditorService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should register the custom markdown language', () => {
        const customMarkdownLanguage = monaco.languages.getLanguages().find((l) => l.id === CUSTOM_MARKDOWN_LANGUAGE_ID);
        expect(customMarkdownLanguage).toBeDefined();
        expect(registerLanguageSpy).toHaveBeenCalledExactlyOnceWith({ id: customMarkdownLanguage!.id });
    });

    it('should correctly handle themes', () => {
        // Initialization: The editor should be in light mode since that is what we initialized the themeSubject with
        expect(setThemeSpy).toHaveBeenCalledExactlyOnceWith(MonacoEditorService.LIGHT_THEME_ID);
        // Switch to dark theme
        themeSubject.next(Theme.DARK);
        TestBed.flushEffects();
        expect(setThemeSpy).toHaveBeenCalledTimes(2);
        expect(setThemeSpy).toHaveBeenNthCalledWith(2, MonacoEditorService.DARK_THEME_ID);
        // Switch back to light theme
        themeSubject.next(Theme.LIGHT);
        TestBed.flushEffects();
        expect(setThemeSpy).toHaveBeenCalledTimes(3);
        expect(setThemeSpy).toHaveBeenNthCalledWith(3, MonacoEditorService.LIGHT_THEME_ID);
    });

    it('should inject an editor into the provided DOM element', () => {
        const element = document.createElement('div');
        const editor = monacoEditorService.createStandaloneCodeEditor(element);
        expect(editor.getContainerDomNode()).toBe(element);
        expect(element.children).toHaveLength(1);
        expect(element.children.item(0)!.className).toContain('monaco-editor');
    });

    it('should inject a diff editor into the provided DOM element', () => {
        const element = document.createElement('div');
        const diffEditor = monacoEditorService.createStandaloneDiffEditor(element);
        expect(diffEditor.getContainerDomNode()).toBe(element);
        expect(element.children).toHaveLength(1);
        expect(element.children.item(0)!.className).toContain('monaco-diff-editor');
    });
});
