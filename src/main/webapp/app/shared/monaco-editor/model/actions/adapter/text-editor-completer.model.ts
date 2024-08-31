import { TextEditorCompletionItem } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-completion-item.model';

/**
 * An interface for a completer that can be used with a text editor.
 * @typeparam ItemType The type of item that the completer returns.
 */
export interface TextEditorCompleter<ItemType> {
    /**
     * The trigger characters that should cause the completer to search for completions.
     */
    triggerCharacters: string[];

    /**
     * Whether the completer has more completions that can be searched for (e.g. if the results depend on a rest call).
     */
    completionsAreIncomplete: boolean;

    /**
     * Searches for completion items based on the given search string.
     * @param searchTerm The text input to use to search for completion items.
     */
    searchItems(searchTerm: string): Promise<ItemType[]>;

    /**
     * Maps a completion item to a text editor completion item.
     * @param item The completion item to map.
     */
    mapCompletionItem(item: ItemType): TextEditorCompletionItem;
}
