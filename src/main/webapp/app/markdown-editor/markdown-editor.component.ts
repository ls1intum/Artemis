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
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { Command,
        BoldCommand,
        ItalicCommand,
        UnderlineCommand,
        HeadingOneCommand,
        HeadingTwoCommand,
        HeadingThreeCommand,
        CodeCommand,
        LinkCommand,
        PictureuploadCommand,
        OrderedListCommand,
        UnorderedListCommand,
        ReferenceCommand,
} from 'app/markdown-editor/commands';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { SpecialCommand } from 'app/markdown-editor/specialCommands';

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

    @Input() markdown: string;
    @Output() markdownChange = new EventEmitter<string>();
    @Output() html = new EventEmitter<string>();

    defaultCommands: Command[] = [
        new BoldCommand(),
        new ItalicCommand(),
        new UnderlineCommand(),
        new ReferenceCommand(),
        new CodeCommand(),
        new LinkCommand(),
        new PictureuploadCommand(),
        new OrderedListCommand(),
        new UnorderedListCommand(),
    ];

    headerCommands: Command[] = [
        new HeadingOneCommand(),
        new HeadingTwoCommand(),
        new HeadingThreeCommand(),
    ];

    @Input() specialCommands: SpecialCommand[];
    @Output() textWithSpecialCommandFound = new EventEmitter<[string, SpecialCommand][]>();

    @Input() showPreviewButton = true;
    previewTextAsHtml: string;
    previewMode = false;
    @ContentChild('preview') previewChild: ElementRef;

    constructor(private artemisMarkdown: ArtemisMarkdown) {
    }

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
        if (this.specialCommands == null || this.specialCommands.length === 0) {
        [...this.defaultCommands, ...this.headerCommands || []].forEach(command => {
            command.setEditor(this.aceEditorContainer.getEditor());
        });
        } else {
            [...this.defaultCommands, ...this.specialCommands, ...this.headerCommands || []].forEach(command => {
                command.setEditor(this.aceEditorContainer.getEditor());
            });
        }
    }

    /**
     * @function setupQuestionEditor
     * @desc Initializes the ace editor for the mc question
     */
    setupMarkdownEditor(): void {
        this.aceEditorContainer.setTheme('chrome');
        this.aceEditorContainer.getEditor().renderer.setShowGutter(false);
        this.aceEditorContainer.getEditor().renderer.setPadding(10);
        this.aceEditorContainer.getEditor().renderer.setScrollMargin(8, 8);
        this.aceEditorContainer.getEditor().setHighlightActiveLine(false);
        this.aceEditorContainer.getEditor().setShowPrintMargin(false);
        this.aceEditorContainer.getEditor().clearSelection();
    }

    /**
     * @function parse
     * @desc check if special commands are contained
     *       if no special commands are contained parse markdown parse to HTML and emit to parent
     *       otherwise divide it by [, call the method parseLineForSpecialCommand for each textLine and emit the result to parent
     *       result is displayed using default preview
     */
    parse(): void {
        // check if specialCommands are contained
        if (this.specialCommands == null || this.specialCommands.length === 0) {
                this.previewTextAsHtml = this.artemisMarkdown.htmlForMarkdown(this.markdown);

                // Emit to Clients
                this.html.emit(this.previewTextAsHtml);
            return;
        } else {
            // seperate the text by [
            const parseArray = this.markdown
            .split('\[-')
            .map(this.parseLineForSpecialCommand);
            this.textWithSpecialCommandFound.emit(parseArray);
        }
    }

    /**
     * @function parseLineForSpecialCommand
     * @desc check which specialCommand is contained within the textLine and remove the specialCommand from the textLine
     * @param textLine {string} from the parse function
     * @return array of the textLine with the corresponding specialCommand
     */
    private parseLineForSpecialCommand = (textLine: string): [string, SpecialCommand] => {
        for (const specialCommand of this.specialCommands) {
            const possibleCommandIdentifier = [specialCommand.getIdentifier(), specialCommand.getIdentifier().toLowerCase(), specialCommand.getIdentifier().toUpperCase()];
            if (possibleCommandIdentifier.some(identifier => textLine.indexOf(identifier) !== -1)) {

                // TODO one possible extension would be to search for opening and closing tags and send all text in-between (potentially multiple lines) into the emitter
                const trimmedLineWithoutIdentifier = possibleCommandIdentifier.reduce((line, identifier) => line.replace(identifier, ''), textLine).trim();
                return [trimmedLineWithoutIdentifier, specialCommand];
            }
        }
        return [textLine.trim(), null];
    };

    /**
     * @function togglePreview
     * @desc Toggle the preview in the template
     */
    togglePreview(): void {
        this.previewMode = !this.previewMode;
        this.parse();
    }

}
