import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import * as monaco from 'monaco-editor';
import { TranslateService } from '@ngx-translate/core';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { MonacoTextEditorAdapter } from 'app/editor/monaco-editor/model/actions/adapter/monaco-text-editor.adapter';
import { TextEditorCompleter } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-completer.model';
import { TextEditorCompletionItem, TextEditorCompletionItemKind } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-completion-item.model';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';

const originalResizeObserver = globalThis.ResizeObserver;

describe('MonacoTextEditorAdapter', () => {
    let fixture: ComponentFixture<MonacoEditorComponent>;
    let comp: MonacoEditorComponent;
    let adapter: MonacoTextEditorAdapter;
    let registerSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MonacoEditorComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(MonacoEditorComponent);
        comp = fixture.componentInstance;
        global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;
        fixture.detectChanges();
        adapter = new MonacoTextEditorAdapter(comp.getEditor());
        registerSpy = vi.spyOn(monaco.languages, 'registerCompletionItemProvider');
    });

    afterEach(() => {
        vi.restoreAllMocks();
        globalThis.ResizeObserver = originalResizeObserver;
    });

    function createCompleter(overrides: Partial<TextEditorCompleter<string>> = {}): TextEditorCompleter<string> {
        return {
            triggerCharacter: ':',
            incomplete: true,
            searchItems: vi.fn().mockResolvedValue(['joy']),
            mapCompletionItem: (item: string, range: TextEditorRange, searchTerm: string, index: number) =>
                new TextEditorCompletionItem(`:${item}:`, '😂', '😂', TextEditorCompletionItemKind.Default, range, `:${searchTerm}`, String(index).padStart(3, '0')),
            ...overrides,
        };
    }

    async function provideCompletions(text: string, column: number) {
        const provider = registerSpy.mock.calls[registerSpy.mock.calls.length - 1][1] as monaco.languages.CompletionItemProvider;
        comp.setText(text);
        const model = comp.getModel()!;
        // Context and token are unused by the adapter's provider implementation.
        return provider.provideCompletionItems(model, { lineNumber: 1, column } as monaco.Position, undefined as never, undefined as never);
    }

    it('should pass filterText and sortText through to the Monaco suggestion', async () => {
        adapter.addCompleter(createCompleter());
        const result = (await provideCompletions(':jo', 4)) as monaco.languages.CompletionList;
        expect(result.suggestions).toHaveLength(1);
        expect(result.suggestions[0].label).toBe(':joy:');
        expect(result.suggestions[0].insertText).toBe('😂');
        expect(result.suggestions[0].filterText).toBe(':jo');
        expect(result.suggestions[0].sortText).toBe('000');
        expect(result.incomplete).toBe(true);
    });

    it('should leave filterText and sortText undefined when the completion item does not set them', async () => {
        adapter.addCompleter(
            createCompleter({
                mapCompletionItem: (item: string, range: TextEditorRange) =>
                    new TextEditorCompletionItem(`:${item}:`, undefined, `:${item}:`, TextEditorCompletionItemKind.Default, range),
            }),
        );
        const result = (await provideCompletions(':jo', 4)) as monaco.languages.CompletionList;
        expect(result.suggestions[0].filterText).toBeUndefined();
        expect(result.suggestions[0].sortText).toBeUndefined();
    });

    it('should include the trigger character in the replacement range', async () => {
        adapter.addCompleter(createCompleter());
        const result = (await provideCompletions(':jo', 4)) as monaco.languages.CompletionList;
        const range = result.suggestions[0].range as monaco.IRange;
        expect(range.startColumn).toBe(1);
        expect(range.endColumn).toBe(4);
    });

    it('should pass the typed term and result index to mapCompletionItem', async () => {
        const mapSpy = vi.fn(
            (item: string, range: TextEditorRange, searchTerm: string, index: number) =>
                new TextEditorCompletionItem(`:${item}:`, undefined, `:${item}:`, TextEditorCompletionItemKind.Default, range, `:${searchTerm}`, String(index)),
        );
        adapter.addCompleter(createCompleter({ searchItems: vi.fn().mockResolvedValue(['joy', 'joy_cat']), mapCompletionItem: mapSpy }));
        await provideCompletions(':jo', 4);
        expect(mapSpy).toHaveBeenCalledTimes(2);
        expect(mapSpy.mock.calls[0][2]).toBe('jo');
        expect(mapSpy.mock.calls[0][3]).toBe(0);
        expect(mapSpy.mock.calls[1][3]).toBe(1);
    });
});
