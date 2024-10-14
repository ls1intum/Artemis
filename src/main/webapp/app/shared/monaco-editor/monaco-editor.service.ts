import { Injectable, effect, inject } from '@angular/core';
import * as monaco from 'monaco-editor';
import { CUSTOM_MARKDOWN_CONFIG, CUSTOM_MARKDOWN_LANGUAGE, CUSTOM_MARKDOWN_LANGUAGE_ID } from 'app/shared/monaco-editor/model/languages/monaco-custom-markdown.language';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { MONACO_LIGHT_THEME_DEFINITION } from 'app/shared/monaco-editor/model/themes/monaco-light.theme';
import { MonacoEditorTheme } from 'app/shared/monaco-editor/model/themes/monaco-editor-theme.model';
import { MONACO_DARK_THEME_DEFINITION } from 'app/shared/monaco-editor/model/themes/monaco-dark.theme';

/**
 * Service providing shared functionality for the Monaco editor.
 * This service is intended to be used by components that need to create and manage Monaco editors.
 * It also ensures that the editor's theme matches the current theme of Artemis.
 */
@Injectable({ providedIn: 'root' })
export class MonacoEditorService {
    private readonly themeService: ThemeService = inject(ThemeService);
    private readonly currentTheme = toSignal(this.themeService.getCurrentThemeObservable(), { requireSync: true });

    private lightTheme: MonacoEditorTheme;
    private darkTheme: MonacoEditorTheme;

    constructor() {
        this.registerCustomThemes();
        this.registerCustomMarkdownLanguage();

        effect(() => {
            this.applyTheme(this.currentTheme());
        });
    }

    private registerCustomThemes(): void {
        this.lightTheme = new MonacoEditorTheme(MONACO_LIGHT_THEME_DEFINITION);
        this.darkTheme = new MonacoEditorTheme(MONACO_DARK_THEME_DEFINITION);
        this.lightTheme.register();
        this.darkTheme.register();
    }

    private registerCustomMarkdownLanguage(): void {
        monaco.languages.register({ id: CUSTOM_MARKDOWN_LANGUAGE_ID });
        monaco.languages.setLanguageConfiguration(CUSTOM_MARKDOWN_LANGUAGE_ID, CUSTOM_MARKDOWN_CONFIG);
        monaco.languages.setMonarchTokensProvider(CUSTOM_MARKDOWN_LANGUAGE_ID, CUSTOM_MARKDOWN_LANGUAGE);
    }

    /**
     * Applies the given theme to the Monaco editor.
     * @param artemisTheme The theme to apply.
     * @private
     */
    private applyTheme(artemisTheme: Theme): void {
        monaco.editor.setTheme(artemisTheme === Theme.LIGHT ? this.lightTheme.getId() : this.darkTheme.getId());
    }

    /**
     * Creates a standalone code editor (see {@link MonacoEditorComponent}) with sensible default settings and inserts it into the given DOM element.
     * @param domElement The DOM element to insert the editor into.
     */
    createStandaloneCodeEditor(domElement: HTMLElement): monaco.editor.IStandaloneCodeEditor {
        const editor = monaco.editor.create(domElement, {
            value: '',
            glyphMargin: true,
            minimap: { enabled: false },
            lineNumbersMinChars: 4,
            scrollBeyondLastLine: false,
            scrollbar: {
                alwaysConsumeMouseWheel: false, // Prevents the editor from consuming the mouse wheel event, allowing the parent element to scroll.
            },
        });
        editor.getModel()?.setEOL(monaco.editor.EndOfLineSequence.LF);
        return editor;
    }

    /**
     * Creates a standalone diff editor (see {@link MonacoDiffEditorComponent}) with sensible default settings and inserts it into the given DOM element.
     * @param domElement The DOM element to insert the editor into.
     */
    createStandaloneDiffEditor(domElement: HTMLElement): monaco.editor.IStandaloneDiffEditor {
        return monaco.editor.createDiffEditor(domElement, {
            automaticLayout: true,
            glyphMargin: true,
            minimap: { enabled: false },
            readOnly: true,
            renderSideBySide: true,
            scrollBeyondLastLine: false,
            stickyScroll: {
                enabled: false,
            },
            renderOverviewRuler: false,
            scrollbar: {
                vertical: 'hidden',
                handleMouseWheel: true,
                alwaysConsumeMouseWheel: false,
            },
            hideUnchangedRegions: {
                enabled: true,
            },
            guides: {
                indentation: false,
            },
            renderLineHighlight: 'none',
            fontSize: 12,
        });
    }
}
