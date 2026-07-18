import { faSmile } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { EmojiSearch } from '@ctrl/ngx-emoji-mart';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { TextEditorAction } from 'app/editor/monaco-editor/model/actions/text-editor-action.model';
import { Disposable } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';
import { TextEditor } from 'app/editor/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorCompletionItem, TextEditorCompletionItemKind } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-completion-item.model';
import { EMOTICON_TO_EMOJI } from 'app/editor/monaco-editor/model/emoticon-emoji.util';

/**
 * An emoji whose native glyph is guaranteed to be present. All standard emojis carry one
 * (the emoji data is uncompressed eagerly), but the {@link EmojiData} typing marks it optional.
 */
type NativeEmojiData = EmojiData & { native: string };

/**
 * The pattern a typed shortcode term must match for completion to run. Anything else
 * (whitespace, punctuation) deterministically closes the suggestion popup.
 */
const SHORTCODE_TERM_PATTERN = /^[A-Za-z0-9_+-]+$/;

/**
 * The maximum number of suggestions shown at once. Monaco re-queries on every keystroke
 * (the completer reports its list as incomplete), so capped results stay reachable by typing more.
 */
const MAX_SUGGESTIONS = 5;

/**
 * How many results to request from the emoji search before filtering out entries without a native glyph.
 */
const SEARCH_LIMIT = 30;

/**
 * How many characters before the cursor are scanned to find the ':' trigger. Generously covers
 * the longest shortcodes, keywords, and aliases (e.g. 'rolling_on_the_floor_laughing').
 */
const SCAN_LENGTH_LIMIT = 128;

/**
 * Action to complete emoji shortcodes in the editor. Users that type ':' followed by at least one
 * word character or digit will see a list of matching emojis (searched by id, name, keywords, and
 * aliases) and can insert the native emoji glyph. A bare ':+' or ':-' shows no popup ('+' and '-'
 * neither trigger completion nor quick suggestions), but ':+1' and ':-1' complete via the digit.
 * Hidden from the toolbar; the emoji picker button covers that use case.
 */
export class EmojiCompletionAction extends TextEditorAction {
    disposableCompletionProvider?: Disposable;

    static readonly ID = 'emoji-completion.action';
    static readonly DEFAULT_INSERT_TEXT = ':';

    constructor(private readonly emojiSearch: EmojiSearch) {
        super(EmojiCompletionAction.ID, 'artemisApp.metis.editor.emoji', faSmile, undefined, true);
    }

    /**
     * Registers this action in the provided editor. This will register a completion provider that shows
     * matching emojis while the user types an emoji shortcode.
     * @param editor The editor to register the action in.
     * @param translateService The translate service to use for translations, e.g. the label.
     */
    override register(editor: TextEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        this.disposableCompletionProvider = this.registerCompletionProviderForCurrentModel<NativeEmojiData>(
            editor,
            this.searchEmojisForTerm.bind(this),
            (emoji: NativeEmojiData, range: TextEditorRange, searchTerm: string, index: number) =>
                new TextEditorCompletionItem(
                    // The glyph leads the label (GitHub-style); no detail text, since Monaco renders details in the
                    // right-aligned part of the row, far away from the shortcode.
                    `${emoji.native} :${emoji.id}:`,
                    undefined,
                    emoji.native,
                    TextEditorCompletionItemKind.Default,
                    range,
                    // Filter against what the user typed so that the glyph-led label and keyword/alias matches
                    // (e.g. ':party' -> 'tada') survive Monaco's filtering.
                    `:${searchTerm}`,
                    // Preserve the search's relevance order instead of Monaco's label-based ordering.
                    String(index).padStart(3, '0'),
                ),
            ':',
            true,
            { requireWordBoundaryBeforeTrigger: true, scanLengthLimit: SCAN_LENGTH_LIMIT },
        );
    }

    /**
     * Inserts the text ':' into the editor, focuses it, and triggers the completion provider. Since the
     * search term right after a bare ':' is empty, {@link searchEmojisForTerm} returns no results and the
     * suggestion popup only appears once the user types at least one character following the colon.
     * @param editor The editor to insert the text into.
     */
    run(editor: TextEditor) {
        this.replaceTextAtCurrentSelection(editor, EmojiCompletionAction.DEFAULT_INSERT_TEXT);
        editor.triggerCompletion();
        editor.focus();
    }

    /**
     * Disposes the action and the registered completion provider.
     */
    override dispose(): void {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
    }

    /**
     * Searches for emojis matching the given term by id, name, keywords, and aliases.
     * Returns no results for empty terms (a bare ':' shows no popup), terms outside the shortcode
     * grammar (whitespace or punctuation closes the popup), and exact emoticon prefixes
     * (typing ':D' must not open a popup that would swallow the Enter used to send the message).
     * @param searchTerm The term the user typed after the ':' trigger character.
     */
    async searchEmojisForTerm(searchTerm?: string): Promise<NativeEmojiData[]> {
        if (!searchTerm || !SHORTCODE_TERM_PATTERN.test(searchTerm) || EMOTICON_TO_EMOJI[`:${searchTerm}`] !== undefined) {
            return [];
        }
        const results = this.emojiSearch.search(searchTerm, undefined, SEARCH_LIMIT) ?? [];
        return results.filter((emoji): emoji is NativeEmojiData => !!emoji.native).slice(0, MAX_SUGGESTIONS);
    }
}
