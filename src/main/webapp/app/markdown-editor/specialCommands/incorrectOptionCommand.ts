import { SpecialCommand } from 'app/markdown-editor/specialCommands/specialCommand';
import {BDelegate} from 'app/markdown-editor';
import {Question} from 'app/entities/question';

export class IncorrectOptionCommand extends SpecialCommand {
    buttonTitle = 'Incorrect Option';
    identifier = '[x]';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addInCorrectAnswerOption';

    /**
     * @function addAnswerOptionTextToEditor
     * @desc Adds the markdown for a correct or incorrect answerOption at the end of the current markdown text
     * @param mode {boolean} mode true sets the text for an correct answerOption, false for an incorrect one
     */
    execute(): void {
        const addedText = '\n[ ] Enter an incorrect answer option here';
        this.editor.focus();
        this.editor.clearSelection();
        this.editor.moveCursorTo(this.editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        this.editor.insert(addedText);
        const range = this.editor.selection.getRange();
        range.setStart(range.start.row, 6);
        this.editor.selection.setRange(range);
    }

    parsing(text: string): void {
        const questionParts = text.split(/\[\]|\[ \]|\[x\]|\[X\]/g);
        const questionText = questionParts[0];

        this.artemisMarkdown.parseTextHintExplanation(questionText, this.question);
    }
}
