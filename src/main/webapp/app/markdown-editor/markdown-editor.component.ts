import { Component, AfterViewInit, ViewChild, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { Command } from 'app/markdown-editor/commands/command';
import { BoldCommand } from 'app/markdown-editor/commands/bold.command';
import { ItalicCommand } from 'app/markdown-editor/commands/italic.command';
import { UnderlineCommand } from 'app/markdown-editor/commands/underline.command';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { HintCommand } from 'app/markdown-editor/specialcommands/hint.command';
import { CorrectOptionCommand } from 'app/markdown-editor/specialcommands/correctOptionCommand';
import { IncorrectoptionCommand } from 'app/markdown-editor/specialcommands/incorrectoption.command';
import { ExplanationCommand } from 'app/markdown-editor/specialcommands/explanation.command';

@Component({
    selector: 'jhi-markdown-editor',
    styleUrls: ['./markdown-editor.scss'],
    providers: [ArtemisMarkdown],
    templateUrl: './markdown-editor.component.html'
})
export class MarkdownEditorComponent implements AfterViewInit, OnChanges {
    @ViewChild('aceEditor')
    aceEditorContainer: AceEditorComponent;

    @Input() defaultText: string;

    @Output() defaultTextChanged = new EventEmitter();

    questionEditorText = '';
    questionEditorAutoUpdate = true;

    hintCommand = new HintCommand();
    correctCommand = new CorrectOptionCommand();
    incorrectCommand = new IncorrectoptionCommand();
    explanationCommand = new ExplanationCommand();
    boldCommand = new BoldCommand();
    italicCommand = new ItalicCommand();
    underlineCommand = new UnderlineCommand();

    commands: Command[] = [new BoldCommand(), new ItalicCommand(), new UnderlineCommand()];

    ngAfterViewInit(): void {
        requestAnimationFrame(this.setupMarkdownEditor.bind(this));
    }

    ngOnChanges(changes: SimpleChanges): void {
        this.aceEditorContainer.getEditor().on(
            'blur',
            () => {
                this.defaultTextChanged.emit(this.defaultText);
            },
            this
        );
    }


    /**
     * @function setupQuestionEditor
     * @desc Initializes the ace editor for the mc question
     */

    /** Currently responsible for making the editor appear nicely**/
    setupMarkdownEditor(): void {
        this.aceEditorContainer.setTheme('chrome');
        this.aceEditorContainer.getEditor().renderer.setShowGutter(false);
        this.aceEditorContainer.getEditor().renderer.setPadding(10);
        this.aceEditorContainer.getEditor().renderer.setScrollMargin(8, 8);
        this.aceEditorContainer.getEditor().setHighlightActiveLine(false);
        this.aceEditorContainer.getEditor().setShowPrintMargin(false);
        this.aceEditorContainer.getEditor().clearSelection();
        this.defaultText;
        this.aceEditorContainer.getEditor().on(
            'blur',
            () => {
                this.defaultTextChanged.emit(this.defaultText);
            },
            this
        );
    }

}
