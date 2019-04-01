import { Command } from './command';

export class ColorPickerCommand extends Command {
    buttonIcon = '';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.color';

    /**
     *
     * @function execute
     * @desc Set/ Remove color
     *       1. Check if the selected text includes (<span)
     *       2. If included reduce the selected text by the html elements for setting the color and replace the selected text by textToAdd
     *       3. If not included insert the html element that change the style of the selectedText by setting the chosen color
     *       4. Color changes occure
     * Note: Markdown does not support color changes - this is why html injection is used
     */
    execute(color: string): void {
        const selectedText = this.getSelectedText();

        if (selectedText.includes('<span')) {
            const textToAdd = selectedText.slice(28, -7);
            this.insertText(textToAdd);
        } else {
            let textToAdd = '';
            switch (color) {
                case '#ca2024':
                    textToAdd = `<span style="color:#ca2024">` + `${selectedText}` + `</span>`;
                    this.insertText(textToAdd);
                    break;
                case '#3ea119':
                    textToAdd = `<span style="color:#3ea119">` + `${selectedText}` + `</span>`;
                    this.insertText(textToAdd);
                    break;
                case '#ffffff':
                    textToAdd = `<span style="color:#ffffff">` + `${selectedText}` + `</span>`;
                    this.insertText(textToAdd);
                    break;
                case '#000000':
                    textToAdd = `<span style="color:#000000">` + `${selectedText}` + `</span>`;
                    this.insertText(textToAdd);
                    break;
                case '#fffa5c':
                    textToAdd = `<span style="color:#fffa5c">` + `${selectedText}` + `</span>`;
                    this.insertText(textToAdd);
                    break;
                case '#0d3cc2':
                    textToAdd = `<span style="color:#0d3cc2">` + `${selectedText}` + `</span>`;
                    this.insertText(textToAdd);
                    break;
                case '#b05db8':
                    textToAdd = `<span style="color:#b05db8">` + `${selectedText}` + `</span>`;
                    this.insertText(textToAdd);
                    break;
                case '#d89770':
                    textToAdd = `<span style="color:#d89770">` + `${selectedText}` + `</span>`;
                    this.insertText(textToAdd);
                    break;
            }
        }
    }
}
