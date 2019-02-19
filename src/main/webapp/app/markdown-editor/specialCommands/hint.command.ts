import { Component } from '@angular/core';
import { SpecialCommand } from 'app/markdown-editor/specialCommands/specialCommand';
import { MarkDownElement, Question} from 'app/entities/question';
import { ArtemisMarkdown } from '../../components/util/markdown.service';
import { BDelegate } from 'app/markdown-editor';

@Component({
    providers: [ArtemisMarkdown]
})

export class HintCommand extends SpecialCommand {
    buttonTitle = 'Hint';
    identifier = '[-h]';

    //constructor(private artemisMarkdown: ArtemisMarkdown) { super() }

    execute(editor: any): void {
        const addedText = "\n\t[-h] Add a hint here (visible during the quiz via '?'-Button)";
        editor.focus();
        editor.clearSelection();
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        editor.insert(addedText);
        const range = editor.selection.getRange();
        range.setStart(range.start.row, 6);
        editor.selection.setRange(range);
    }

    parsing(delegate: BDelegate, text: string /*, question: Question*/): void {
        //this.artemisMarkdown.parseTextHintExplanation(text, question);
        console.log('parsing in HintCommand and forward result to ', delegate);
        delegate.handleResponse(text);
    }
}
