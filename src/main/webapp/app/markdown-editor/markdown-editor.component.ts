import {
    AfterViewInit,
    Component,
    ContentChild,
    ElementRef,
    EventEmitter,
    Input,
    OnInit,
    Output,
    ViewChild
} from '@angular/core';
import {AceEditorComponent} from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import {Command, BoldCommand, ItalicCommand, UnderlineCommand} from 'app/markdown-editor/commands';
import {ArtemisMarkdown} from 'app/components/util/markdown.service';
import {SpecialCommand} from 'app/markdown-editor/specialCommands';

@Component({
    selector: 'jhi-markdown-editor',
    styleUrls: ['./markdown-editor.scss'],
    providers: [ArtemisMarkdown],
    templateUrl: './markdown-editor.component.html'
})
export class MarkdownEditorComponent implements AfterViewInit, OnInit {
    @ViewChild('aceEditor')
    aceEditorContainer: AceEditorComponent;
    aseEditorOptions = {
        autoUpdateContent: true,
        mode: 'markdown',
    };

    @Input() defaultText: string;
    @Input() specialCommands: SpecialCommand[];
    @Input() useMarkdownPreview: boolean;
    @Input() parsingValue: string;

    @Output() html = new EventEmitter<string>();
    @Output() textWithSpecialCommandFound = new EventEmitter<[string, SpecialCommand]>();
    @Output() previewModeChange = new EventEmitter<boolean>();
    @Output() changesInMarkdown = new EventEmitter<boolean>();

    @ContentChild('preview') previewChild: ElementRef;


    previewTextAsHtml: string;
    previewMode = false;
    defaultCommands: Command[] = [new BoldCommand(), new ItalicCommand(), new UnderlineCommand()];

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    get previewButtonTranslateString(): string {
        return this.previewMode ? 'entity.action.edit' : 'entity.action.preview';
    }

    get previewButtonIcon(): string {
        return this.previewMode ? 'pencil-alt' : 'eye';
    }

    get showDefaultPreview(): boolean {
        return this.previewChild == null;
    }

    addCommand(command: Command) {
        this.defaultCommands.push(command);
    }

    removeCommand(classRef: typeof Command) {
        setTimeout(() =>
            this.defaultCommands = this.defaultCommands.filter(element => !(element instanceof classRef))
        );
    }

    ngAfterViewInit(): void {
        this.setupMarkdownEditor();
    }

    ngOnInit(): void {
        [...this.defaultCommands, ...this.specialCommands || []].forEach(command => {
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
        this.aceEditorContainer.getEditor().on(
            "focus",
            () => {
                if (this.parsingValue === 'parse'){
                    console.log('ich parse');
                    this.parse();
                }
                if (this.parsingValue === 'value'){
                    console.log('ich emit value');
                    this.changesInMarkdown.emit(true);
                }
            },
            this
        );
    }

    /**
     * If Special Commands are defined, this emits line by line with the corresponding command.
     * Otherwise, markdown is parsed to HTML and emitted. Result is displayed using default preview.
     */
    parse(): void {
        // Only generate HTML if no Special Commands are defined.
        // Special Commands require special parsing by the client.
        if (this.specialCommands == null || this.specialCommands.length === 0) {
            const htmlForPreview = this.artemisMarkdown.htmlForMarkdown(this.defaultText);

            // Only store HTML is default preview is used.
            if (this.showDefaultPreview) {
                this.previewTextAsHtml = htmlForPreview;
            }

            // Emit to Clients
            this.html.emit(htmlForPreview);

            return;
        }

        const textLines = this.defaultText.split('\n');
        for (const textLine of textLines) {
            this.parseLineForSpecialCommand(textLine);
        }
    }

    private parseLineForSpecialCommand(textLine: string) {
        for (const specialCommand of this.specialCommands) {
            const possibleCommandIdentifier = [specialCommand.getIdentifier(), specialCommand.getIdentifier().toLowerCase(), specialCommand.getIdentifier().toUpperCase()];
            if (possibleCommandIdentifier.some(identifier => textLine.indexOf(identifier) !== -1))  {

                // TODO one possible extension would be to search for opening and closing tags and send all text in-between (potentially multiple lines) into the emitter
                const trimmedLineWithoutIdentifier = possibleCommandIdentifier.reduce((line, identifier) => line.replace(identifier, ''), textLine).trim();
                this.textWithSpecialCommandFound.emit([trimmedLineWithoutIdentifier, specialCommand]);
                return;
            }
        }
        this.textWithSpecialCommandFound.emit([textLine.trim(), null]);
    }

    /**
     * @function togglePreview
     * @desc Toggles the preview in the template
     */
    togglePreview(): void {
        this.previewMode = !this.previewMode;
        this.previewModeChange.emit(this.previewMode);
        this.parse();
    }

}
