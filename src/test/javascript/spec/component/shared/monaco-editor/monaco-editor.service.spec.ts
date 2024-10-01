import { TestBed } from '@angular/core/testing';
import * as monaco from 'monaco-editor';
import { Theme } from 'app/core/theme/theme.service';
import { MonacoEditorService } from '../../../../../../main/webapp/app/shared/monaco-editor/monaco-editor.service';
import { ArtemisTestModule } from '../../../test.module';
import { CUSTOM_MARKDOWN_LANGUAGE_ID } from 'app/shared/monaco-editor/model/languages/monaco-custom-markdown.language';

describe('MonacoEditorService', () => {
    let service: MonacoEditorService;
    let registerLanguageSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        });
        registerLanguageSpy = jest.spyOn(monaco.languages, 'register');
        service = TestBed.inject(MonacoEditorService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should register the custom markdown language', () => {
        const customMarkdownLanguage = monaco.languages.getLanguages().find((l) => l.id === CUSTOM_MARKDOWN_LANGUAGE_ID);
        expect(customMarkdownLanguage).toBeDefined();
        expect(registerLanguageSpy).toHaveBeenCalledExactlyOnceWith({ id: customMarkdownLanguage!.id });
    });

    it.each([Theme.LIGHT, Theme.DARK])('should apply the correct theme for $identifier mode', (theme) => {
        const setThemeSpy = jest.spyOn(monaco.editor, 'setTheme');
        service.applyTheme(theme);
        expect(setThemeSpy).toHaveBeenCalledExactlyOnceWith(theme === Theme.LIGHT ? 'vs' : 'vs-dark');
    });
});
