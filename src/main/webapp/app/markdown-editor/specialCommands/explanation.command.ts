import { Component } from '@angular/core';
import { SpecialCommand } from 'app/markdown-editor/specialCommands/specialCommand';
import { MarkDownElement, Question} from 'app/entities/question';
import { ArtemisMarkdown } from '../../components/util/markdown.service';

@Component({
    providers: [ArtemisMarkdown]
})

export class ExplanationCommand extends SpecialCommand {
    buttonTitle = 'Explanation';
    iidentifier = '[-e]';

    //constructor(private artemisMarkdown: ArtemisMarkdown) { super() }

    execute(editor: any): void {
        const addedText = '\n\t[-e] Add an explanation here (only visible in feedback after quiz has ended)';
        editor.focus();
        editor.clearSelection();
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        editor.insert(addedText);
        const range = editor.selection.getRange();
        range.setStart(range.start.row, 6);
        editor.selection.setRange(range);
    }

    parsing(text: string, question: Question): void {
        //this.artemisMarkdown.parseTextHintExplanation(text, question);
    }
}
