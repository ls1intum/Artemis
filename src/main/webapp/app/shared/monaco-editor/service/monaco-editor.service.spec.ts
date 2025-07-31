import { TestBed } from '@angular/core/testing';
import * as monaco from 'monaco-editor';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { MonacoEditorService } from 'app/shared/monaco-editor/service/monaco-editor.service';
import { CUSTOM_MARKDOWN_LANGUAGE_ID } from 'app/shared/monaco-editor/model/languages/monaco-custom-markdown.language';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MONACO_LIGHT_THEME_DEFINITION } from 'app/shared/monaco-editor/model/themes/monaco-light.theme';
import { MONACO_DARK_THEME_DEFINITION } from 'app/shared/monaco-editor/model/themes/monaco-dark.theme';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';

describe('MonacoEditorService', () => {
    let monacoEditorService: MonacoEditorService;
    let setThemeSpy: jest.SpyInstance;
    let registerLanguageSpy: jest.SpyInstance;

    let themeService: ThemeService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: ThemeService, useClass: MockThemeService }],
        });
        // Avoids an error with the diff editor, which uses a ResizeObserver.
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        registerLanguageSpy = jest.spyOn(monaco.languages, 'register');
        setThemeSpy = jest.spyOn(monaco.editor, 'setTheme');
        themeService = TestBed.inject(ThemeService);
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
        TestBed.tick();
        // Initialization: The editor should be in light mode since that is what we initialized the themeSubject with
        expect(setThemeSpy).toHaveBeenCalledExactlyOnceWith(MONACO_LIGHT_THEME_DEFINITION.id);
        // Switch to dark theme
        themeService.applyThemePreference(Theme.DARK);
        TestBed.tick();
        expect(setThemeSpy).toHaveBeenCalledTimes(2);
        expect(setThemeSpy).toHaveBeenNthCalledWith(2, MONACO_DARK_THEME_DEFINITION.id);
        // Switch back to light theme
        themeService.applyThemePreference(Theme.LIGHT);
        TestBed.tick();
        expect(setThemeSpy).toHaveBeenCalledTimes(3);
        expect(setThemeSpy).toHaveBeenNthCalledWith(3, MONACO_LIGHT_THEME_DEFINITION.id);
    });

    it.each([
        {
            className: 'monaco-editor',
            createFn: (element: HTMLElement) => monacoEditorService.createStandaloneCodeEditor(element),
        },
        {
            className: 'monaco-diff-editor',
            createFn: (element: HTMLElement) => monacoEditorService.createStandaloneDiffEditor(element),
        },
    ])(
        'should insert an editor ($className) into the provided DOM element',
        ({ className, createFn }: { className: string; createFn: (element: HTMLElement) => monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor }) => {
            const element = document.createElement('div');
            const editor = createFn(element);
            expect(editor.getContainerDomNode()).toBe(element);
            expect(element.children).toHaveLength(1);
            expect(element.children.item(0)!.classList).toContain(className);
        },
    );
});
