import {Component} from '@angular/core';
import {SpecialCommand} from 'app/markdown-editor/specialCommands/specialCommand';
import {ArtemisMarkdown} from '../../components/util/markdown.service';

@Component({
    providers: [ArtemisMarkdown]
})

export class HintCommand extends SpecialCommand {
    buttonTitle = 'Hint';
    identifier = '[-h]';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addHint';

    execute(): void {
        const addedText = "\n\t[-h] Add a hint here (visible during the quiz via '?'-Button)";
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
