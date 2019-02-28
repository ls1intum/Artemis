import { Component, AfterViewInit, ViewChild, Input, Output, EventEmitter, OnChanges, SimpleChanges, OnInit} from '@angular/core';
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { Command } from 'app/markdown-editor/commands/command';
import { BoldCommand } from 'app/markdown-editor/commands/bold.command';
import { ItalicCommand } from 'app/markdown-editor/commands/italic.command';
import { UnderlineCommand } from 'app/markdown-editor/commands/underline.command';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { SpecialCommand } from 'app/markdown-editor/specialCommands/specialCommand';

@Component({
    selector: 'jhi-markdown-editor',
    styleUrls: ['./markdown-editor.scss'],
    providers: [ArtemisMarkdown],
    templateUrl: './markdown-editor.component.html'
})
export class MarkdownEditorComponent implements AfterViewInit, OnChanges, OnInit {
    @ViewChild('aceEditor')
    aceEditorContainer: AceEditorComponent;

    @Input() defaultText: string;

    @Output() defaultTextChanged = new EventEmitter();

    @Output() textWithSpecialCommandFound = new EventEmitter<[string, SpecialCommand]>();

    questionEditorText = '';
    questionEditorAutoUpdate = true;

    showPreview: boolean;



    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    defaultCommands: Command[] = [new BoldCommand(), new ItalicCommand(), new UnderlineCommand()];
    
    addCommand(command: Command) {
        this.defaultCommands.push(command);
    }
    
    removeCommand(command: Command) {
        // TODO: remove this command from the default commands
    }
    
    
    
    @Input() specialCommands: SpecialCommand[];

    ngAfterViewInit(): void {
        requestAnimationFrame(this.setupMarkdownEditor.bind(this));
    }

    ngOnInit(): void {
        [...this.defaultCommands, ...this.specialCommands].forEach(command => {
            command.setEditor(this.aceEditorContainer.getEditor());
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


    // TODO this method should be invoked by the Preview button of the Markdown Editor (in case it is active) and the client should be able to invoke it
    parse(): void {
        
        if (this.specialCommands == null || this.specialCommands.length === 0) {
            // we are already done, TODO we just need to invoke the standard markdown --> html parser
            return;
        }
        
        const text = this.defaultText;
        const textLines = text.split("\n");
        for (const textLine of textLines) {

            for (const specialCommand of this.specialCommands) {
                if (textLine.indexOf(specialCommand.getIdentifier()) != -1
                    || textLine.indexOf(specialCommand.getIdentifier().toLowerCase()) != -1
                    || textLine.indexOf(specialCommand.getIdentifier().toUpperCase()) != -1) {

                    // TODO one possible extension would be to search for opening and closing tags and send all text in-between (potentially multiple lines) into the emitter
                    this.textWithSpecialCommandFound.emit(
                        [textLine.replace(specialCommand.getIdentifier(), ''), specialCommand]
                    );
                    break;
                }
            }
        }
    }

}
