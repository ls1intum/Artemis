import { Command } from './command';

export class HeadingCommand extends Command {

    buttonIcon = 'heading';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.heading';

    headingFonts  = new Array<string>();

    execute(): void {
        this.headingFonts = ['headingOne','headingTwo','headingThree'];
        if (!this.editor) { return; }
        let selectedText = this.editor.getSelectedText();
        const isSelected = !!selectedText;
        const startSize = 2;
        let initText = '';
        const range = this.editor.selection.getRange();
        initText = 'Heading';
        for (const element of this.headingFonts) {
            if (element === 'headingOne'){
                selectedText = `# ${selectedText || initText}`;
                break;
            }
            if (element === 'headingTwo') {
                selectedText = `## ${selectedText || initText}`;
                break;
            }
        }
        this.editor.session.replace(range, selectedText);
        if (!isSelected) {
            range.start.column += startSize;
            range.end.column = range.start.column + initText.length;
            this.editor.selection.setRange(range);
        }
        this.editor.focus();

    }
}
