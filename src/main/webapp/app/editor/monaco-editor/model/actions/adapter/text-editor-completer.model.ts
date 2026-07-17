import { TextEditorCompletionItem } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-completion-item.model';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';

/**
 * An interface for a completer that can be used with a text editor.
 * @typeparam ItemType The type of item that the completer returns.
 */
export interface TextEditorCompleter<ItemType> {
    /**
     * An optional character that, upon being typed, triggers the completer to show completion items.
     */
    triggerCharacter?: string;

    /**
     * Whether the completer has more completions that can be searched for (e.g. if the results depend on a rest call).
     */
    incomplete: boolean;

    /**
     * Searches for completion items based on the given search string.
     * @param searchTerm The text input to use to search for completion items.
     */
    searchItems(searchTerm: string): Promise<ItemType[]>;

    /**
     * Maps a completion item to a text editor completion item.
     * @param item The completion item to map.
     * @param range The range in the editor where the completion item should be inserted.
     * @param searchTerm The term the user has typed after the trigger character, used e.g. to build a filter text.
     * @param index The position of the item in the search results, used e.g. to build a sort text that preserves the result order.
     */
    mapCompletionItem(item: ItemType, range: TextEditorRange, searchTerm: string, index: number): TextEditorCompletionItem;
}
