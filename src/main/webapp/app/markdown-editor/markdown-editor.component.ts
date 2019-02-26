import { Component, AfterViewInit, ViewChild, Input, Output, EventEmitter, OnChanges, SimpleChanges, OnInit} from '@angular/core';
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { Command } from 'app/markdown-editor/commands/command';
import { BoldCommand } from 'app/markdown-editor/commands/bold.command';
import { ItalicCommand } from 'app/markdown-editor/commands/italic.command';
import { UnderlineCommand } from 'app/markdown-editor/commands/underline.command';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { HintCommand } from 'app/markdown-editor/specialCommands/hint.command';
import { CorrectOptionCommand } from 'app/markdown-editor/specialCommands/correctOptionCommand';
import { IncorrectOptionCommand } from 'app/markdown-editor/specialCommands/incorrectOptionCommand';
import { ExplanationCommand } from 'app/markdown-editor/specialCommands/explanation.command';

export interface BDelegate {
    togglePreview():void;
    parseMarkdown(text: string): void;
    handleResponse(response: any): void;
}

@Component({
    selector: 'jhi-markdown-editor',
    styleUrls: ['./markdown-editor.scss'],
    providers: [ArtemisMarkdown],
    templateUrl: './markdown-editor.component.html'
})
export class MarkdownEditorComponent implements AfterViewInit, OnChanges, OnInit {
    @ViewChild('aceEditor')
    aceEditorContainer: AceEditorComponent;

    @Input() delegate: BDelegate;

    @Input() defaultText: string;

    @Output() defaultTextChanged = new EventEmitter();

    questionEditorText = '';
    questionEditorAutoUpdate = true;

    showPreview: boolean;



    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    commands: Command[];
    defaultCommands: Command[] = [new BoldCommand(), new ItalicCommand(), new UnderlineCommand()];
    @Input() additionalCommands: Command[];

    ngAfterViewInit(): void {
        requestAnimationFrame(this.setupMarkdownEditor.bind(this));
    }

    ngOnInit(): void {
        this.commands = [...this.defaultCommands, ...this.additionalCommands];
        this.commands.forEach(command => {
            command.setEditor(this.aceEditorContainer.getEditor());
            command.setArtemisMarkdownService(this.artemisMarkdown);
        });
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


    searchForTheParsingCommand(): void {
        const text = this.defaultText;
        const questionParts = text.split(/\[\]|\[ \]|\[x\]|\[X\]/g);

        /*for (const element of this.commands){
             element.parsing(text);
        }*/
    }

}
