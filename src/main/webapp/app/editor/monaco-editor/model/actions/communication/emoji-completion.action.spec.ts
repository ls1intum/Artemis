import { vi } from 'vitest';
import * as monaco from 'monaco-editor';
import { TranslateService } from '@ngx-translate/core';
import { EmojiSearch } from '@ctrl/ngx-emoji-mart';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { EmojiCompletionAction } from 'app/editor/monaco-editor/model/actions/communication/emoji-completion.action';
import { MonacoTextEditorAdapter } from 'app/editor/monaco-editor/model/actions/adapter/monaco-text-editor.adapter';
import { TextEditorCompleter } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-completer.model';
import { TextEditorPosition } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-position.model';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorCompletionItemKind } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-completion-item.model';
import { EMOTICON_TO_EMOJI } from 'app/editor/monaco-editor/model/emoticon-emoji.util';

function emojiData(id: string, native?: string): EmojiData {
    return { id, native } as EmojiData;
}

describe('EmojiCompletionAction', () => {
    let action: EmojiCompletionAction;
    let emojiSearch: EmojiSearch;
    let searchSpy: ReturnType<typeof vi.fn>;

    const editor = new MonacoTextEditorAdapter({} as monaco.editor.IStandaloneCodeEditor);
    const translateService = { instant: (key: string) => key } as TranslateService;

    beforeEach(() => {
        searchSpy = vi.fn().mockReturnValue([emojiData('joy', '😂'), emojiData('joy_cat', '😹')]);
        emojiSearch = { search: searchSpy } as unknown as EmojiSearch;
        action = new EmojiCompletionAction(emojiSearch);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function registerAndCaptureCompleter(): TextEditorCompleter<unknown> {
        vi.spyOn(editor, 'addAction').mockReturnValue({ dispose: vi.fn() });
        const addCompleterSpy = vi.spyOn(editor, 'addCompleter').mockReturnValue({ dispose: vi.fn() });
        action.register(editor, translateService);
        return addCompleterSpy.mock.calls[0][0];
    }

    it('should be hidden from the editor toolbar', () => {
        expect(action.hideInEditor).toBe(true);
    });

    it('should register a completer with trigger character, incomplete list, word boundary, and raised scan length', () => {
        const completer = registerAndCaptureCompleter();
        expect(completer.triggerCharacter).toBe(':');
        expect(completer.incomplete).toBe(true);
        expect(completer.requireWordBoundaryBeforeTrigger).toBe(true);
        expect(completer.scanLengthLimit).toBe(128);
    });

    it('should dispose the completion provider on dispose', () => {
        registerAndCaptureCompleter();
        const disposeSpy = vi.spyOn(action.disposableCompletionProvider!, 'dispose');
        action.dispose();
        expect(disposeSpy).toHaveBeenCalledOnce();
    });

    it.each([undefined, ''])('should return no suggestions for an empty term (%s)', async (term) => {
        await expect(action.searchEmojisForTerm(term)).resolves.toEqual([]);
        expect(searchSpy).not.toHaveBeenCalled();
    });

    it.each([' joy', 'jo y', 'jo!', 'jo.', 'jo:', '(jo'])('should return no suggestions for a term outside the shortcode grammar (%s)', async (term) => {
        await expect(action.searchEmojisForTerm(term)).resolves.toEqual([]);
        expect(searchSpy).not.toHaveBeenCalled();
    });

    it.each(Object.keys(EMOTICON_TO_EMOJI))('should not compete with the emoticon %s', async (emoticon) => {
        await expect(action.searchEmojisForTerm(emoticon.slice(1))).resolves.toEqual([]);
        expect(searchSpy).not.toHaveBeenCalled();
    });

    it('should search and return emojis for a valid term', async () => {
        const result = await action.searchEmojisForTerm('joy');
        expect(searchSpy).toHaveBeenCalledWith('joy', undefined, 30);
        expect(result).toEqual([emojiData('joy', '😂'), emojiData('joy_cat', '😹')]);
    });

    it('should filter out emojis without a native glyph before applying the cap', async () => {
        const withNative = Array.from({ length: 16 }, (_, i) => emojiData(`emoji${i}`, '😀'));
        searchSpy.mockReturnValue([emojiData('broken1'), ...withNative.slice(0, 8), emojiData('broken2'), ...withNative.slice(8)]);
        const result = await action.searchEmojisForTerm('emo');
        expect(result).toHaveLength(5);
        expect(result).toEqual(withNative.slice(0, 5));
    });

    it('should return no suggestions when the search yields nothing', async () => {
        searchSpy.mockReturnValue(null);
        await expect(action.searchEmojisForTerm('nomatch')).resolves.toEqual([]);
    });

    it('should map emojis to completion items with shortcode label, native glyph, filter text, and ordered sort text', () => {
        const completer = registerAndCaptureCompleter();
        const range = new TextEditorRange(new TextEditorPosition(1, 1), new TextEditorPosition(1, 4));
        const item = completer.mapCompletionItem(emojiData('joy', '😂'), range, 'jo', 3);
        expect(item.getLabel()).toBe('😂 :joy:');
        expect(item.getDetailText()).toBeUndefined();
        expect(item.getInsertText()).toBe('😂');
        expect(item.getKind()).toBe(TextEditorCompletionItemKind.Default);
        expect(item.getRange()).toBe(range);
        expect(item.getFilterText()).toBe(':jo');
        expect(item.getSortText()).toBe('003');
    });
});
