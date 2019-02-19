import { Component, AfterViewInit, ViewChild, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
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
export class MarkdownEditorComponent implements AfterViewInit, OnChanges {
    @ViewChild('aceEditor')
    aceEditorContainer: AceEditorComponent;

    @Input() delegate: BDelegate;

    @Input() defaultText: string;

    @Output() defaultTextChanged = new EventEmitter();

    questionEditorText = '';
    questionEditorAutoUpdate = true;

    showPreview: boolean;

    hintCommand = new HintCommand();
    correctCommand = new CorrectOptionCommand(this.artemisMarkdown);
    incorrectCommand = new IncorrectOptionCommand();
    explanationCommand = new ExplanationCommand();
    boldCommand = new BoldCommand();
    italicCommand = new ItalicCommand();
    underlineCommand = new UnderlineCommand();

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

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

    ngOnInit() {
        this.delegate.togglePreview();
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

        for( let element of questionParts){
            if (element.includes('[\]')){
                this.correctCommand.parsing(this.delegate, element)
            } else if (element.includes('[ \]')){
                this.correctCommand.parsing(this.delegate, element)
            } else if (element.includes('[ \]')){
                this.incorrectCommand.parsing(this.delegate, element)
            } else {
                this.incorrectCommand.parsing(this.delegate, element)
            }
        }
    }
}
