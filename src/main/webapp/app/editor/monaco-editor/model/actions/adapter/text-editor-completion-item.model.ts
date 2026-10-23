import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';

/**
 * Represents a completion item in a text editor.
 * Completion items are used to provide suggestions for the user to complete their input.
 */
export class TextEditorCompletionItem {
    /**
     * Creates a new text editor completion item.
     * @param label The label of the completion item, i.e., the text shown in the completion list.
     * @param detailText An optional detail text to display alongside the label.
     * @param insertText The text to insert into the editor when the completion item is selected.
     * @param range The range in the editor where the completion item's text should be inserted.
     */
    constructor(
        private readonly label: string,
        private readonly detailText: string | undefined,
        private readonly insertText: string,
        private readonly range: TextEditorRange,
    ) {}

    getLabel(): string {
        return this.label;
    }

    getDetailText(): string | undefined {
        return this.detailText;
    }

    getInsertText(): string {
        return this.insertText;
    }

    getRange(): TextEditorRange {
        return this.range;
    }
}
