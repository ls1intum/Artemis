import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { Command } from './command';

export class ColorPickerCommand extends Command {
    buttonIcon = '' as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.color';

    /**
     * @function execute
     * @desc Set/ Remove color
     *       1. Check if the selected text includes (<span)
     *       2. If included reduce the selected text by the html elements for setting the color and replace the selected text by textToAdd
     *       3. If not included insert the html element that change the style of the selectedText by setting the chosen color
     *       4. Color changes occur
     */
    execute(color: string): void {
        const selectedText = this.getSelectedText();

        if (selectedText.includes('class="green"') || selectedText.includes('class="white"')) {
            const textToAdd = selectedText.slice(20, -7);
            this.insertText(textToAdd);
        } else if (selectedText.includes('class="yellow"') || selectedText.includes('class="orange"')) {
            const textToAdd = selectedText.slice(21, -7);
            this.insertText(textToAdd);
        } else if (selectedText.includes('class="red"')) {
            const textToAdd = selectedText.slice(18, -7);
            this.insertText(textToAdd);
        } else if (selectedText.includes('class="blue"') || selectedText.includes('class="lila"')) {
            const textToAdd = selectedText.slice(19, -7);
            this.insertText(textToAdd);
        } else {
            this.insertHTMLForColorChange(selectedText, color);
        }
    }

    /**
     * @function insertHTMLForColorChange
     * @desc Insert a html span with the color changes
     * Note: Markdown does not support color changes - this is why html injection is used
     */
    insertHTMLForColorChange(selectedText: string, color: string) {
        let textToAdd = '';
        switch (color) {
            case '#ca2024':
                textToAdd = `<span class="red">` + `${selectedText}` + `</span>`;
                this.insertText(textToAdd);
                break;
            case '#3ea119':
                textToAdd = `<span class="green">` + `${selectedText}` + `</span>`;
                this.insertText(textToAdd);
                break;
            case '#ffffff':
                textToAdd = `<span class="white">` + `${selectedText}` + `</span>`;
                this.insertText(textToAdd);
                break;
            case '#000000':
                textToAdd = `${selectedText}`;
                // this.execute('#000000');
                // textToAdd = `<span class="black">` + `${selectedText}` + `</span>`;
                this.insertText(textToAdd);
                break;
            case '#fffa5c':
                textToAdd = `<span class="yellow">` + `${selectedText}` + `</span>`;
                this.insertText(textToAdd);
                break;
            case '#0d3cc2':
                textToAdd = `<span class="blue">` + `${selectedText}` + `</span>`;
                this.insertText(textToAdd);
                break;
            case '#b05db8':
                textToAdd = `<span class="lila">` + `${selectedText}` + `</span>`;
                this.insertText(textToAdd);
                break;
            case '#d86b1f':
                textToAdd = `<span class="orange">` + `${selectedText}` + `</span>`;
                this.insertText(textToAdd);
                break;
        }
    }
}
