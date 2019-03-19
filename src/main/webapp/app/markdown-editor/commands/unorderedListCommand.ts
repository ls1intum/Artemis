import { Command } from './command';

export class UnorderedListCommand extends Command {

    buttonIcon = 'list-ul';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.unorderedList';

    execute(): void {
        const selectedText = this.editor.getSelectedText();
        this.splitText(selectedText);
    }

    splitText(selectedText: string): void {
        const parseArray = selectedText.split('\n');
        parseArray.forEach( element => this.replaceText(element));
    }

    replaceText(element: string): void {
        if (element.includes('-')) {
            const textToAdd = element.slice(2);
            const text = `${textToAdd}\n`;
            this.editor.insert(text);
        } else {
            const range = this.editor.selection.getRange();
            element = `- ${element}\n`;
            this.editor.session.replace(range, element);
            this.editor.focus();
        }
    }
}
