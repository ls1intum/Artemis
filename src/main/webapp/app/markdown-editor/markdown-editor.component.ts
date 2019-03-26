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

    /** {domainCommands} containing all domain commands which need to be set by the parent component which contains the markdown editor */
    @Input() domainCommands: DomainCommand[];

    /** {textWithDomainCommandsFound} emits an {array} of text lines with the corresponding domain command to the parent component which contains the markdown editor */
    @Output() textWithDomainCommandsFound = new EventEmitter<[string, DomainCommand][]>();

    /** {showPreviewButton}
     * 1. true -> the preview of the editor is used
     * 2. false -> the preview of the parent component is used, parent has to set this value to false with an input */
    @Input() showPreviewButton = true;

    /** {previewTextAsHtml} text that is emitted to the parent component if the parent does not use any domain commands */
    previewTextAsHtml: string;

    /** {previewMode} when editor is created the preview is set to false, since the edit mode is set active */
    previewMode = false;

    /** {previewChild} Is not null when the parent component is responsible for the preview content
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
            command.setEditor(this.aceEditorContainer);
        });
        } else {
            [...this.defaultCommands, ...this.domainCommands, ...this.headerCommands || []].forEach(command => {
                command.setEditor(this.aceEditorContainer);
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
     *       2. Otherwise create an array containing all domainCommands identifier passed on from the client,
     *       3. Create a copy of the markdown text
     *       4. Create the regEx Expression which searches for the domainCommand identifier
     *       5. Go through the copy of the markdown text until it is empty and split it as soon as a domainCommand identifier is found into [command]
     *           5a. One command can contain text over several lines
     *           5b. All the text between two identifiers is mapped to the first identifier
     *       6. Reduce the copy by the length of the command
     *       7. Call the parseLineForDomainCommand for command and save it into content
     *       8. Emit the content to parent component to assign the values of the array to the right attributes
     */
    parse(): void {
        /** check if domainCommands are passed on from the parent component */
        if (this.domainCommands == null || this.domainCommands.length === 0) {
            /** if no domainCommands contained emit the markdown text converted to html to parent component to display */
                this.previewTextAsHtml = this.artemisMarkdown.htmlForMarkdown(this.markdown);
                this.html.emit(this.previewTextAsHtml);
            return;
        } else {
            /** create array with domain command identifier */
            const domainCommandIdentifiersToParse = this.domainCommands.map(command => command.getOpeningIdentifier());
            /** create empty array which
             * will contain the splitted text with the corresponding domainCommandIdentifier which
             * will be emitted to the parent component */
            const commandTextsMappedToCommandIdentifiers = [];
            /** create a remainingMarkdownText of the markdown text to loop trough it and find the domainCommandIdentifier */
            let remainingMarkdownText = this.markdown.slice(0);

            /** create string with the identifiers to use for RegEx by deleting the [] of the domainCommandIdentifiers */
            const commandIdentifiersString = domainCommandIdentifiersToParse.map(tag => tag.replace('[', '').replace(']', '')).join('|');

            /** create a new regex expression which searches for the domainCommands identifiers
             * (?=   If a command is found, add the command identifier to the result of the split
             * \\[  look for the character '[' to determine the beginning of the command identifier
             * (${commandIdentifiersString}) look if after the '[' one of the element of commandIdentifiersString is contained
             * \\] look for the character ']' to determine the end of the command identifier
             * )  close the bracket
             *  g: search in the whole string
             *  m: match the regex over multiple lines*/
            const regex = new RegExp(`(?=\\[(${commandIdentifiersString})\\])`, 'gm');

            /** iterating loop as long as the remainingMarkdownText of the markdown text exists and split the remainingMarkdownText as soon as a domainCommand identifier is found */
            while (remainingMarkdownText.length) {
                /** As soon as an identifier is with regEx the remainingMarkdownText of the markdown text is split and saved into {array} textWithCommandIdentifier
                 *  split: saves its values into an {array}
                 *  limit 1: indicated that as soon as an identifier is found remainingMarkdownText is split */
                const [textWithCommandIdentifier] = remainingMarkdownText.split(regex, 1);
                /** substring: reduces the {string} by the length in the brackets
                 *  Split the remainingMarkdownText by the length of {array} textWithCommandIdentifier to get the remaining array
                 *  and save it into remainingMarkdownText to start the loop again and search for further domainCommandIdentifiers
                 *  when remainingMarkdownText is empty the while loop will terminate*/
                remainingMarkdownText = remainingMarkdownText.substring(textWithCommandIdentifier.length);
                /** call the parseLineForDomainCommand for each extracted textWithCommandIdentifier
                *   trim: reduced the whitespacing linebreaks */
                const commandTextWithCommandIdentifier = this.parseLineForDomainCommand(textWithCommandIdentifier.trim());
                /** push the commandTextWithCommandIdentifier into the commandTextsMappedToCommandIdentifiers*/
                commandTextsMappedToCommandIdentifiers.push(commandTextWithCommandIdentifier);
            }
            /** emit the {array} commandTextsMappedToCommandIdentifiers to the client*/
            this.textWithDomainCommandsFound.emit(commandTextsMappedToCommandIdentifiers);
        }
    }

    /**
     * @function parseLineForDomainCommand
     * @desc Couple each textLine with the domainCommandIdentifier to emit that to the parent component for the value assignment
     *       1. Check which domainCommand identifier is contained within the textLine
     *       2. Remove the domainCommand identifier from the textLine
     *       3. Create an array with first element textLine and second element the domainCommand identifier
     * @param textLine {string} from the parse function
     * @return array of the textLine with the domainCommand identifier
     */
    private parseLineForDomainCommand = (textLine: string): [string, DomainCommand] => {
        for (const domainCommand of this.domainCommands) {
            const possibleOpeningCommandIdentifier = [domainCommand.getOpeningIdentifier(), domainCommand.getOpeningIdentifier().toLowerCase(), domainCommand.getOpeningIdentifier().toUpperCase()];
            if (possibleOpeningCommandIdentifier.some(identifier => textLine.indexOf(identifier) !== -1)) {
                // TODO when closingIdentifiers are used write a method to extract them from the textLine
                const trimmedLineWithoutIdentifier = possibleOpeningCommandIdentifier.reduce((line, identifier) => line.replace(identifier, ''), textLine).trim();
                        return [trimmedLineWithoutIdentifier, domainCommand];
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
