import { Command } from './command';

export class OrderedListCommand extends Command {

    buttonIcon = 'list-ol';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.orderedList';

    execute(): void {
        let selectedText = this.editor.getSelectedText();
        this.splitText(selectedText);
    }

    splitText(selectedText: string): void {
        let parseArray = selectedText.split('\n');
        let addAmount = parseArray.length-1;
        for (let element of parseArray){
             this.replaceText(element, parseArray.length - addAmount);
             addAmount--;
        }
    }

    replaceText(element: string, value: number): void {
        if (element.includes('.')) {
            const textToAdd = element.slice(3);
            const text = `${textToAdd}\n`;
            this.editor.insert(text);
        }else if (element === '') {
            const range = this.editor.selection.getRange();
            element = `1. ${element}`;
            this.editor.session.replace(range, element);
            this.editor.focus();
        } else {
            element = `${value}. ${element}\n`;
            this.editor.insert(element)
        }
    }
}
