import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { Command } from './command';

export class CodeBlockCommand extends Command {
    buttonIcon = 'file-code' as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.codeBlock';

    /**
     * @function execute
     * @desc add (```java) before and (```) after the selected text
     */
    execute(): void {
        let selectedText = this.getSelectedText();
        const range = this.getRange();
        const initText = 'Source Code';
        selectedText = '```java\n ' + (selectedText || initText) + '\n```';
        this.replace(range, selectedText);
        this.focus();
    }
}
