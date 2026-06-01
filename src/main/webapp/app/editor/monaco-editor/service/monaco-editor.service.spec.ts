import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { type MockInstance, vi } from 'vitest';
import * as monaco from 'monaco-editor';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { MonacoEditorService } from 'app/editor/monaco-editor/service/monaco-editor.service';
import { CUSTOM_MARKDOWN_LANGUAGE_ID } from 'app/editor/monaco-editor/model/languages/monaco-custom-markdown.language';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MONACO_LIGHT_THEME_DEFINITION } from 'app/editor/monaco-editor/model/themes/monaco-light.theme';
import { MONACO_DARK_THEME_DEFINITION } from 'app/editor/monaco-editor/model/themes/monaco-dark.theme';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';

// Capture the global ResizeObserver provided by the test setup so it can be restored after each test.
const originalResizeObserver = globalThis.ResizeObserver;

describe('MonacoEditorService', () => {
    setupTestBed({ zoneless: true });

    let monacoEditorService: MonacoEditorService;
    let setThemeSpy: MockInstance;
    let registerLanguageSpy: MockInstance;

    let themeService: ThemeService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: ThemeService, useClass: MockThemeService }],
        });
        // Avoids an error with the diff editor, which uses a ResizeObserver.
        global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;
        registerLanguageSpy = vi.spyOn(monaco.languages, 'register');
        setThemeSpy = vi.spyOn(monaco.editor, 'setTheme');
        themeService = TestBed.inject(ThemeService);
        monacoEditorService = TestBed.inject(MonacoEditorService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        globalThis.ResizeObserver = originalResizeObserver;
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
