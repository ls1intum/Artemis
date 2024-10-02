import { Injectable, effect, inject } from '@angular/core';
import * as monaco from 'monaco-editor';
import { CUSTOM_MARKDOWN_CONFIG, CUSTOM_MARKDOWN_LANGUAGE, CUSTOM_MARKDOWN_LANGUAGE_ID } from 'app/shared/monaco-editor/model/languages/monaco-custom-markdown.language';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { toSignal } from '@angular/core/rxjs-interop';

@Injectable({ providedIn: 'root' })
export class MonacoEditorService {
    static readonly LIGHT_THEME_ID = 'vs';
    static readonly DARK_THEME_ID = 'vs-dark';

    private readonly themeService: ThemeService = inject(ThemeService);
    private readonly currentTheme = toSignal(this.themeService.getCurrentThemeObservable(), { requireSync: true });

    constructor() {
        monaco.languages.register({ id: CUSTOM_MARKDOWN_LANGUAGE_ID });
        monaco.languages.setLanguageConfiguration(CUSTOM_MARKDOWN_LANGUAGE_ID, CUSTOM_MARKDOWN_CONFIG);
        monaco.languages.setMonarchTokensProvider(CUSTOM_MARKDOWN_LANGUAGE_ID, CUSTOM_MARKDOWN_LANGUAGE);

        effect(() => {
            this.applyTheme(this.currentTheme());
        });
    }

    /**
     * Applies the given theme to the Monaco editor.
     * @param artemisTheme The theme to apply.
     * @private
     */
    private applyTheme(artemisTheme: Theme): void {
        monaco.editor.setTheme(artemisTheme === Theme.LIGHT ? MonacoEditorService.LIGHT_THEME_ID : MonacoEditorService.DARK_THEME_ID);
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
            fontSize: 12,
        });
    }
}
