import { Component, AfterViewInit, ViewChild, Input, Output, EventEmitter, OnInit,  SimpleChanges, OnChanges} from '@angular/core';
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
export class MarkdownEditorComponent implements AfterViewInit, OnInit, OnChanges {
    @ViewChild('aceEditor')
    aceEditorContainer: AceEditorComponent;

    @Input() defaultText: string;
    @Input() specialCommands: SpecialCommand[];

    @Output() textWithSpecialCommandFound = new EventEmitter<[string, SpecialCommand]>();
    @Output() triggerParsingInClient = new EventEmitter();

    questionEditorText = '';
    questionEditorAutoUpdate = true;

    showPreview: boolean;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    defaultCommands: Command[] = [new BoldCommand(), new ItalicCommand(), new UnderlineCommand()];
    
    addCommand(command: Command) {
        this.defaultCommands.push(command);
    }
    
    removeCommand(command: Command) {
        this.defaultCommands.forEach( (item, index) => {
            if(item === command) this.defaultCommands.splice(index,1);
        });
    }

    ngOnChanges(changes: SimpleChanges): void {
        console.log('change');
    }


    ngAfterViewInit(): void {
        requestAnimationFrame(this.setupMarkdownEditor.bind(this));
    }

    ngOnInit(): void {
        [...this.defaultCommands, ...this.specialCommands].forEach(command => {
            command.setEditor(this.aceEditorContainer.getEditor());
        });
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
    }

    // TODO this method should be invoked by the Preview button of the Markdown Editor (in case it is active) and the client should be able to invoke it
    parse(): void {
        
        if (this.specialCommands == null || this.specialCommands.length === 0) {
            // we are already done, TODO we just need to invoke the standard markdown --> html parser
            this.artemisMarkdown.htmlForMarkdown(this.defaultText);
            return;
        }

        const text = this.defaultText;
        const textLines = text.split("\n");
        for (const textLine of textLines) {

            //for the normal text without and commands
            //if (!textLine.includes('[') && textLine.length > 0){
             //  this.textWithSpecialCommandFound.emit([textLine, null]);
              //  console.log(textLine);
            //}

            let didEmit = false;
            //if commands are contained
            for (const specialCommand of this.specialCommands) {

                if (textLine.indexOf(specialCommand.getIdentifier()) != -1
                    || textLine.indexOf(specialCommand.getIdentifier().toLowerCase()) != -1
                    || textLine.indexOf(specialCommand.getIdentifier().toUpperCase()) != -1) {

                    // TODO one possible extension would be to search for opening and closing tags and send all text in-between (potentially multiple lines) into the emitter
                    this.textWithSpecialCommandFound.emit(
                        [textLine.replace(specialCommand.getIdentifier(), ''), specialCommand]
                    );
                    didEmit = true;
                    console.log(textLine)
                    break;
                }
            } if (!didEmit) {this.textWithSpecialCommandFound.emit([textLine, null])}
        }
    }

    /**
     * @function togglePreview
     * @desc Toggles the preview in the template
     */
    togglePreview(): void {
        this.triggerParsingInClient.emit();
    }

}
