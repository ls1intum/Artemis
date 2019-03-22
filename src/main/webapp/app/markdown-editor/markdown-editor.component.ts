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
        AttachmentCommand,
        OrderedListCommand,
        UnorderedListCommand,
        ReferenceCommand,
} from 'app/markdown-editor/commands';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { DomainCommand } from 'app/markdown-editor/domainCommands';

@Component({
    selector: 'jhi-markdown-editor',
    styleUrls: ['./markdown-editor.scss'],
    providers: [ArtemisMarkdown],
    templateUrl: './markdown-editor.component.html'
})

export class MarkdownEditorComponent implements AfterViewInit, OnInit {
    @ViewChild('aceEditor')
    aceEditorContainer: AceEditorComponent;
    aceEditorOptions = {
        autoUpdateContent: true,
        mode: 'markdown',
    };

    /** {string} which is initially displayed in the editor generated and passed on from the parent component*/
    @Input() markdown: string;
    @Output() markdownChange = new EventEmitter<string>();
    @Output() html = new EventEmitter<string>();

    /** {array} containing all default commands accessible for the editor per default */
    defaultCommands: Command[] = [
        new BoldCommand(),
        new ItalicCommand(),
        new UnderlineCommand(),
        new ReferenceCommand(),
        new CodeCommand(),
        new LinkCommand(),
        new AttachmentCommand(),
        new OrderedListCommand(),
        new UnorderedListCommand(),
    ];

    /** {array} containing all header commands accessible for the markdown editor per defaulT*/
    headerCommands: Command[] = [
        new HeadingOneCommand(),
        new HeadingTwoCommand(),
        new HeadingThreeCommand(),
    ];

    /** {array} containing all domain commands which need to be set by the parent component which contains the markdown editor */
    @Input() domainCommands: DomainCommand[];

    /** {EventEmitter} emits an {array} of the textLine with the corresponding domain command to the parent component which contains the markdown editoR */
    @Output() textWithDomainCommandFound = new EventEmitter<[string, DomainCommand][]>();

    /**{boolean} 1. true -> the preview of the editor is used
     *           2. false -> the preview of the parent component is used, parent has to set this value to false with an input*/
    @Input() showPreviewButton = true;

    /** {string} text that is emitted to the parent component if the parent does not use any domain commands */
    previewTextAsHtml: string;

    /** {boolean} when editor is created the preview is set to false, since the edit mode is set active */
    previewMode = false;

    /** Is not null when the parent component is responsible for the preview content
     * -> parent component has to implement ng-content and set the showPreviewButton on true through an input */
    @ContentChild('preview') previewChild: ElementRef;

    constructor(private artemisMarkdown: ArtemisMarkdown) {
    }

    get previewButtonTranslateString(): string {
        return this.previewMode ? 'entity.action.edit' : 'entity.action.preview';
    }

    get previewButtonIcon(): string {
        return this.previewMode ? 'pencil-alt' : 'eye';
    }

    /** {boolean} true when the plane html view is needed, false when the preview content is needed from the parent */
    get showDefaultPreview(): boolean {
        return this.previewChild == null;
    }

    /**
     * @function addCommand
     * @param command
     * @desc customize the user interface of the markdown editor by adding a command
     */
    addCommand(command: Command) {
        this.defaultCommands.push(command);
    }

    /**
     * @function removeCommand
     * @param typeof Command
     * @desc customize the user interface of the markdown editor by removing a command
     */
    removeCommand(classRef: typeof Command) {
        setTimeout(() =>
            this.defaultCommands = this.defaultCommands.filter(element => !(element instanceof classRef))
        );
    }

    ngAfterViewInit(): void {
        this.setupMarkdownEditor();
    }

    ngOnInit(): void {
        if (this.domainCommands == null || this.domainCommands.length === 0) {
        [...this.defaultCommands, ...this.headerCommands || []].forEach(command => {
            command.setEditor(this.aceEditorContainer.getEditor());
        });
        } else {
            [...this.defaultCommands, ...this.domainCommands, ...this.headerCommands || []].forEach(command => {
                command.setEditor(this.aceEditorContainer.getEditor());
            });
        }
    }

    /**
     * @function setupQuestionEditor
     * @desc Initializes the ace editor
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
     * @desc Check if domainCommands are contained within the text to decide how to parse the text
     *       1. If no domainCommands are contained parse markdown to HTML and emit the result to the parent component
     *       2. Otherwise divide the text by "[-", a common identifier for all domainCommands,
     *       2a. Call the method parseLineForDomainCommand for each textLine
     *       2b. Emit the result to parent component to assign the value of the array to the right attributes
     */
    parse(): void {
        /** check if domainCommands are contained */
        if (this.domainCommands == null || this.domainCommands.length === 0) {
                this.previewTextAsHtml = this.artemisMarkdown.htmlForMarkdown(this.markdown);

                /** emit to parent component*/
                this.html.emit(this.previewTextAsHtml);
            return;
        } else {
            /** separate the markdown text by [- */
            const parseArray = this.markdown
            .split('\[-')
            .map(this.parseLineForDomainCommand);
            this.textWithDomainCommandFound.emit(parseArray);
        }
    }

    /**
     * @function parseLineForDomainCommand
     * @desc Couple each textLine with the domainCommand identifier to emit that to the parent component for assignment
     *       1. Check which domainCommand identifier is contained within the textLine
     *       2. Remove the domainCommand identifier from the textLine
     *       3. Create an array with first element textLine and second element the domainCommand identifier
     * @param textLine {string} from the parse function
     * @return array of the textLine with the domainCommand identifier
     */
    private parseLineForDomainCommand = (textLine: string): [string, DomainCommand] => {
        for (const specialCommand of this.domainCommands) {
            const possibleCommandIdentifier = [specialCommand.getIdentifier(), specialCommand.getIdentifier().toLowerCase(), specialCommand.getIdentifier().toUpperCase()];
            if (possibleCommandIdentifier.some(identifier => textLine.indexOf(identifier) !== -1)) {
                const trimmedLineWithoutIdentifier = possibleCommandIdentifier.reduce((line, identifier) => line.replace(identifier, ''), textLine).trim();
                return [trimmedLineWithoutIdentifier, specialCommand];
            }
        }
        return [textLine.trim(), null];
    };

    /**
     * @function togglePreview
     * @desc Toggle the preview in the template and parse the text
     */
    togglePreview(): void {
        this.previewMode = !this.previewMode;
        this.parse();
    }

}
