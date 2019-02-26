import { Component } from '@angular/core';
import { SpecialCommand } from 'app/markdown-editor/specialCommands/specialCommand';
import { MarkDownElement, Question} from 'app/entities/question';
import { ArtemisMarkdown } from '../../components/util/markdown.service';

@Component({
    providers: [ArtemisMarkdown]
})

export class ExplanationCommand extends SpecialCommand {
    buttonTitle = 'Explanation';
    identifier = '[-e]';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addExplanation';

    execute(): void {
        const addedText = '\n\t[-e] Add an explanation here (only visible in feedback after quiz has ended)';
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
