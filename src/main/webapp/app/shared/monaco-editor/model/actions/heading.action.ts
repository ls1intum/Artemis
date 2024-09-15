import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { faHeading } from '@fortawesome/free-solid-svg-icons';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

function getTranslationKeyForLevel(level: number): string {
    const suffix = level === 3 ? 'Three' : level === 2 ? 'Two' : 'One';
    return `artemisApp.multipleChoiceQuestion.editor.heading${suffix}`;
}

const HEADING_CHARACTER = '#';
const HEADING_TEXT = 'Heading';

/**
 * Action to toggle heading text in the editor. It wraps the selected text with the heading delimiter, e.g. switching between text and # text for level 1.
 */
export class HeadingAction extends TextEditorAction {
    level: number;

    /**
     * Constructor for the heading action. Its ID and translation key are based on the heading level.
     * @param level The level of the heading, e.g. 1 for "# Heading", 2 for "## Heading", ...
     */
    constructor(level: number) {
        super(`heading-${level}.action`, getTranslationKeyForLevel(level), faHeading);
        this.level = level;
    }

    /**
     * Toggles the heading prefix ("# ", "## ", ...) for the selected text based on the heading level. If the selected text is already a heading, the prefix is removed.
     * @param editor The editor in which to toggle heading text.
     */
    run(editor: TextEditor) {
        const selection = editor.getSelection();
        const selectedText = selection ? this.getTextAtRange(editor, selection) : undefined;
        if (selection && selectedText !== undefined) {
            const headingText = this.getTextWithToggledHeading(selectedText || `${HEADING_TEXT} ${this.level}`, this.level);
            this.replaceTextAtRange(editor, selection, headingText);
        }
        editor.focus();
    }

    private getTextWithToggledHeading(selectedText: string, level: number): string {
        if (selectedText.startsWith(`${HEADING_CHARACTER.repeat(level)} `)) {
            return selectedText.slice(level + 1);
        }
        return `${HEADING_CHARACTER.repeat(level)} ${selectedText}`;
    }
}
