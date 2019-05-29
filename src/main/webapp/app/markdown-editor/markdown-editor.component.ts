import { AfterViewInit, Component, ContentChild, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { AceEditorComponent } from 'ng2-ace-editor';
import { WindowRef } from 'app/core/websocket/window.service';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import {
    Command,
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
    ColorPickerCommand,
} from 'app/markdown-editor/commands';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { DomainCommand, DomainMultiOptionCommand } from 'app/markdown-editor/domainCommands';
import { ColorSelectorComponent } from 'app/components/color-selector/color-selector.component';
import { DomainTagCommand } from './domainCommands/domainTag.command';

export enum MarkdownEditorHeight {
    SMALL = 200,
    MEDIUM = 500,
    LARGE = 1000,
}

@Component({
    selector: 'jhi-markdown-editor',
    providers: [ArtemisMarkdown],
    templateUrl: './markdown-editor.component.html',
    styleUrls: ['./markdown-editor.component.scss'],
})
export class MarkdownEditorComponent implements AfterViewInit {
    public DomainMultiOptionCommand = DomainMultiOptionCommand;
    public DomainTagCommand = DomainTagCommand;
    public MarkdownEditorHeight = MarkdownEditorHeight;
    @ViewChild('aceEditor', { static: false })
    aceEditorContainer: AceEditorComponent;
    aceEditorOptions = {
        autoUpdateContent: true,
        mode: 'markdown',
    };
    @ViewChild(ColorSelectorComponent, { static: false }) colorSelector: ColorSelectorComponent;

    /** {string} which is initially displayed in the editor generated and passed on from the parent component*/
    @Input() markdown: string;
    @Output() markdownChange = new EventEmitter<string>();
    @Output() html = new EventEmitter<string>();

    /** default colors for the markdown editor*/
    markdownColors = ['#ca2024', '#3ea119', '#ffffff', '#000000', '#fffa5c', '#0d3cc2', '#b05db8', '#d86b1f'];
    selectedColor = '#000000';
    /** {array} containing all colorPickerCommands
     * IMPORTANT: If you want to use the colorpicker you have to implement <div class="markdown-preview"></div>
     * because the class definitions are saved within that method*/
    colorCommands: Command[] = [new ColorPickerCommand()];

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
    headerCommands: Command[] = [new HeadingOneCommand(), new HeadingTwoCommand(), new HeadingThreeCommand()];

    /** {domainCommands} containing all domain commands which need to be set by the parent component which contains the markdown editor */
    @Input() domainCommands: Array<DomainCommand>;

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
    @ContentChild('preview', { static: false }) previewChild: ElementRef;

    /** Resizable constants **/
    @Input()
    enableResize = false;
    @Input()
    resizableMaxHeight = MarkdownEditorHeight.LARGE;
    resizableMinHeight = MarkdownEditorHeight.SMALL;
    interactResizable: Interactable;

    constructor(private artemisMarkdown: ArtemisMarkdown, private $window: WindowRef) {}

    /** {boolean} true when the plane html view is needed, false when the preview content is needed from the parent */
    get showDefaultPreview(): boolean {
        return this.previewChild == null;
    }

    /** opens the button for selecting the color */
    openColorSelector(event: MouseEvent) {
        this.colorSelector.openColorSelector(event);
    }

    /** selected text is changed into the chosen color */
    onSelectedColor(selectedColor: string) {
        this.selectedColor = selectedColor;
        this.colorCommands[0].execute(selectedColor);
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
        setTimeout(() => (this.defaultCommands = this.defaultCommands.filter(element => !(element instanceof classRef))));
    }

    ngAfterViewInit(): void {
        if (this.domainCommands == null || this.domainCommands.length === 0) {
            [...this.defaultCommands, ...this.colorCommands, ...(this.headerCommands || [])].forEach(command => {
                command.setEditor(this.aceEditorContainer);
            });
        } else {
            [...this.defaultCommands, ...this.domainCommands, ...this.colorCommands, ...(this.headerCommands || [])].forEach(command => {
                command.setEditor(this.aceEditorContainer);
            });
        }
        this.setupMarkdownEditor();

        if (this.enableResize) {
            this.setupResizable();
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
        this.aceEditorContainer.getEditor().setAutoScrollEditorIntoView(true);
    }

    /**
     * @function setupResizable
     * @desc Sets up resizable to enable resizing for the user
     */
    setupResizable(): void {
        this.resizableMinHeight = this.$window.nativeWindow.screen.height / 7;
        this.interactResizable = interact('.markdown-editor')
            .resizable({
                // Enable resize from top edge; triggered by class rg-top
                edges: { left: false, right: false, bottom: '.rg-bottom', top: false },
                // Set min and max height
                restrictSize: {
                    min: { height: this.resizableMinHeight },
                    max: { height: this.resizableMaxHeight },
                },
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.aceEditorContainer.getEditor().resize();
            })
            .on('resizemove', function(event: any) {
                const target = event.target;
                // Update element height
                target.style.height = event.rect.height + 'px';
            });
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
            const commandTextsMappedToCommandIdentifiers = new Array<[string, DomainCommand]>();
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
             *  i: case insensitive, neglecting capital letters
             *  m: match the regex over multiple lines*/
            const regex = new RegExp(`(?=\\[(${commandIdentifiersString})\\])`, 'gmi');

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
     * @desc Couple each text with the domainCommandIdentifier to emit that to the parent component for the value assignment
     *       1. Check which domainCommand identifier is contained within the text
     *       2. Remove the domainCommand identifier from the text
     *       3. Create an array with first element text and second element the domainCommand identifier
     * @param text {string} from the parse function
     * @return array of the text with the domainCommand identifier
     */
    private parseLineForDomainCommand = (text: string): [string, DomainCommand] => {
        for (const domainCommand of this.domainCommands) {
            const possibleOpeningCommandIdentifier = [
                domainCommand.getOpeningIdentifier(),
                domainCommand.getOpeningIdentifier().toLowerCase(),
                domainCommand.getOpeningIdentifier().toUpperCase(),
            ];
            if (possibleOpeningCommandIdentifier.some(identifier => text.indexOf(identifier) !== -1)) {
                // TODO when closingIdentifiers are used write a method to extract them from the text
                const trimmedLineWithoutIdentifier = possibleOpeningCommandIdentifier.reduce((line, identifier) => line.replace(identifier, ''), text).trim();
                return [trimmedLineWithoutIdentifier, domainCommand];
            }
        }
        return [text.trim(), null];
    };

    /**
     * @function togglePreview
     * @desc Toggle the preview in the template and parse the text
     */
    togglePreview(event: any): void {
        this.previewMode = !this.previewMode;
        // The text must only be parsed when the active tab before event was edit, otherwise the text can't have changed.
        if (event.activeId === 'editor_edit') {
            this.parse();
        }
    }
}
