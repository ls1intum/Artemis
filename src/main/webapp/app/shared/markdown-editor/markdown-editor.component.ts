import { AfterViewInit, Component, ContentChild, ElementRef, EventEmitter, Input, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
// Note: this import has to be before the 'brace' imports
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import 'brace/mode/latex';
import 'brace/ext/language_tools';
import { Interactable } from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { DomainTagCommand } from './domainCommands/domainTag.command';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { UnderlineCommand } from 'app/shared/markdown-editor/commands/underline.command';
import { ColorPickerCommand } from 'app/shared/markdown-editor/commands/colorPicker.command';
import { BoldCommand } from 'app/shared/markdown-editor/commands/bold.command';
import { AttachmentCommand } from 'app/shared/markdown-editor/commands/attachmentCommand';
import { ReferenceCommand } from 'app/shared/markdown-editor/commands/reference.command';
import { DomainMultiOptionCommand } from 'app/shared/markdown-editor/domainCommands/domainMultiOptionCommand';
import { FullscreenCommand } from 'app/shared/markdown-editor/commands/fullscreen.command';
import { HeadingOneCommand } from 'app/shared/markdown-editor/commands/headingOne.command';
import { Command } from 'app/shared/markdown-editor/commands/command';
import { ItalicCommand } from 'app/shared/markdown-editor/commands/italic.command';
import { OrderedListCommand } from 'app/shared/markdown-editor/commands/orderedListCommand';
import { HeadingTwoCommand } from 'app/shared/markdown-editor/commands/headingTwo.command';
import { LinkCommand } from 'app/shared/markdown-editor/commands/link.command';
import { CodeCommand } from 'app/shared/markdown-editor/commands/code.command';
import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';
import { UnorderedListCommand } from 'app/shared/markdown-editor/commands/unorderedListCommand';
import { HeadingThreeCommand } from 'app/shared/markdown-editor/commands/headingThree.command';
import { CodeBlockCommand } from 'app/shared/markdown-editor/commands/codeblock.command';
import { faAngleRight, faGripLines, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';
import { v4 as uuid } from 'uuid';

export enum MarkdownEditorHeight {
    INLINE = 100,
    SMALL = 200,
    MEDIUM = 500,
    LARGE = 1000,
}

export enum EditorMode {
    NONE = 'none',
    LATEX = 'latex',
}

const getAceMode = (mode: EditorMode) => {
    switch (mode) {
        case EditorMode.LATEX:
            return 'ace/mode/latex';
        case EditorMode.NONE:
            return null;
        default:
            return null;
    }
};

@Component({
    selector: 'jhi-markdown-editor',
    templateUrl: './markdown-editor.component.html',
    styleUrls: ['./markdown-editor.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class MarkdownEditorComponent implements AfterViewInit {
    public MultiOptionCommand = MultiOptionCommand;
    public DomainMultiOptionCommand = DomainMultiOptionCommand;
    public DomainTagCommand = DomainTagCommand;
    // This ref is used for entering the fullscreen mode.
    @ViewChild('wrapper', { read: ElementRef, static: false }) wrapper: ElementRef;
    @ViewChild('aceEditor', { static: false })
    aceEditorContainer: AceEditorComponent;
    aceEditorOptions = {
        autoUpdateContent: true,
        mode: 'markdown',
    };
    @ViewChild(ColorSelectorComponent, { static: false }) colorSelector: ColorSelectorComponent;

    /** {string} which is initially displayed in the editor generated and passed on from the parent component*/
    @Input() markdown?: string;
    @Input() editorMode = EditorMode.NONE;
    @Input() showLineNumbers = false;
    @Output() markdownChange = new EventEmitter<string>();
    @Output() html = new EventEmitter<SafeHtml | null>();

    /** default colors for the markdown editor*/
    markdownColors = ['#ca2024', '#3ea119', '#ffffff', '#000000', '#fffa5c', '#0d3cc2', '#b05db8', '#d86b1f'];
    selectedColor = '#000000';
    /** {array} containing all colorPickerCommands
     * IMPORTANT: If you want to use the colorpicker you have to implement <div class="markdown-preview"></div>
     * because the class definitions are saved within that method*/
    @Input() colorCommands: Command[] = [new ColorPickerCommand()];

    /**
     * Use this array for commands that are not related to the markdown but to the editor (e.g. fullscreen mode).
     * These elements will be displayed on the right side of the command bar.
     */
    @Input() metaCommands: Command[] = [new FullscreenCommand()];

    /** {array} containing all default commands accessible for the editor per default */
    @Input() defaultCommands: Command[] = [
        new BoldCommand(),
        new ItalicCommand(),
        new UnderlineCommand(),
        new ReferenceCommand(),
        new CodeCommand(),
        new CodeBlockCommand(),
        new LinkCommand(),
        new AttachmentCommand(),
        new OrderedListCommand(),
        new UnorderedListCommand(),
    ];

    /** {array} containing all header commands accessible for the markdown editor per default*/
    @Input() headerCommands: Command[] = [new HeadingOneCommand(), new HeadingTwoCommand(), new HeadingThreeCommand()];

    /** {domainCommands} containing all domain commands which need to be set by the parent component which contains the markdown editor */
    @Input() domainCommands: Array<DomainCommand>;

    /** {textWithDomainCommandsFound} emits an {array} of text lines with the corresponding domain command to the parent component which contains the markdown editor */
    @Output() textWithDomainCommandsFound = new EventEmitter<[string, DomainCommand | null][]>();

    @Output() onPreviewSelect = new EventEmitter();
    @Output() onEditSelect = new EventEmitter();

    /** {showPreviewButton}
     * 1. true -> the preview of the editor is used
     * 2. false -> the preview of the parent component is used, parent has to set this value to false with an input */
    @Input() showPreviewButton = true;

    @Input() showEditButton = true;

    /** {previewTextAsHtml} text that is emitted to the parent component if the parent does not use any domain commands */
    previewTextAsHtml: SafeHtml | null;

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
    @Input()
    resizableMinHeight = MarkdownEditorHeight.SMALL;
    interactResizable: Interactable;

    /** {enableFileUpload}
     * whether to show the file upload field and enable the drag and drop functionality
     * enabled by default
     */
    @Input()
    enableFileUpload = true;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faGripLines = faGripLines;
    faAngleRight = faAngleRight;

    uniqueMarkdownEditorId: string;

    constructor(private artemisMarkdown: ArtemisMarkdownService, private fileUploaderService: FileUploaderService, private alertService: AlertService) {
        this.uniqueMarkdownEditorId = 'markdown-editor-' + uuid();
    }

    /** {boolean} true when the plane html view is needed, false when the preview content is needed from the parent */
    get showDefaultPreview(): boolean {
        return this.previewChild == undefined;
    }

    /** opens the button for selecting the color */
    openColorSelector(event: MouseEvent) {
        const marginTop = 35;
        const height = 110;
        this.colorSelector.openColorSelector(event, marginTop, height);
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
     * @param classRef Command
     * @desc customize the user interface of the markdown editor by removing a command
     */
    removeCommand(classRef: typeof Command) {
        setTimeout(() => (this.defaultCommands = this.defaultCommands.filter((element) => !(element instanceof classRef))));
    }

    ngAfterViewInit(): void {
        // Commands may want to add custom completers - remove standard completers of the ace editor.
        this.aceEditorContainer.getEditor().setOptions({
            enableBasicAutocompletion: true,
            enableLiveAutocompletion: true,
        });
        this.aceEditorContainer.getEditor().completers = [];

        if (this.domainCommands == undefined || this.domainCommands.length === 0) {
            [...this.defaultCommands, ...this.colorCommands, ...(this.headerCommands || []), ...this.metaCommands].forEach((command) => {
                command.setEditor(this.aceEditorContainer.getEditor());
                command.setMarkdownWrapper(this.wrapper);
            });
        } else {
            [...this.defaultCommands, ...this.domainCommands, ...this.colorCommands, ...(this.headerCommands || []), ...this.metaCommands].forEach((command) => {
                command.setEditor(this.aceEditorContainer.getEditor());
                command.setMarkdownWrapper(this.wrapper);
            });
        }
        this.setupMarkdownEditor();
        const selectedAceMode = getAceMode(this.editorMode);
        if (selectedAceMode) {
            this.aceEditorContainer.getEditor().getSession().setMode(selectedAceMode);
        }

        if (this.enableResize) {
            this.setupResizable();
        }
    }

    /**
     * @function setupQuestionEditor
     * @desc Initializes the ace editor
     */
    setupMarkdownEditor(): void {
        this.aceEditorContainer.getEditor().renderer.setShowGutter(this.showLineNumbers);
        this.aceEditorContainer.getEditor().renderer.setPadding(10);
        this.aceEditorContainer.getEditor().renderer.setScrollMargin(8, 8);
        this.aceEditorContainer.getEditor().setHighlightActiveLine(false);
        this.aceEditorContainer.getEditor().setShowPrintMargin(false);
        this.aceEditorContainer.getEditor().clearSelection();
        this.aceEditorContainer.getEditor().setAutoScrollEditorIntoView(true);
        this.aceEditorContainer.getEditor().setOptions({ wrap: true });
    }

    /**
     * @function setupResizable
     * @desc Sets up resizable to enable resizing for the user
     */
    setupResizable(): void {
        // Use a unique, random ID to select the editor
        // This is required to select the correct one in case multiple editors are used at the same time
        const selector = '#' + this.uniqueMarkdownEditorId;

        // unregister previously set event listeners for class elements
        interact(selector).unset();

        this.interactResizable = interact(selector)
            .resizable({
                // Enable resize from top edge; triggered by class rg-top
                edges: { left: false, right: false, bottom: '.rg-bottom', top: false },
                // Set min and max height
                modifiers: [
                    interact.modifiers!.restrictSize({
                        min: { width: 0, height: this.resizableMinHeight },
                        max: { width: 2000, height: this.resizableMaxHeight },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', (event: any) => {
                const target = event.target;
                // Update element height
                target.style.height = event.rect.height + 'px';
                this.aceEditorContainer.getEditor().resize();
            });
    }

    /**
     * Parses markdown to generate a preview if the standard preview is used and/or searches for domain command identifiers.
     * Will emit events for both the generated preview and domain commands.
     *
     */
    parse(): void {
        if (this.showDefaultPreview) {
            this.previewTextAsHtml = this.artemisMarkdown.safeHtmlForMarkdown(this.markdown);
            this.html.emit(this.previewTextAsHtml);
        }
        if (this.domainCommands && this.domainCommands.length && this.markdown) {
            /** create array with domain command identifier */
            const domainCommandIdentifiersToParse = this.domainCommands.map((command) => command.getOpeningIdentifier());
            /** create empty array which
             * will contain the splitted text with the corresponding domainCommandIdentifier which
             * will be emitted to the parent component */
            const commandTextsMappedToCommandIdentifiers: [string, DomainCommand | null][] = [];
            /** create a remainingMarkdownText of the markdown text to loop through it and find the domainCommandIdentifier */
            let remainingMarkdownText = this.markdown.slice(0);

            /** create string with the identifiers to use for RegEx by deleting the [] of the domainCommandIdentifiers */
            const commandIdentifiersString = domainCommandIdentifiersToParse
                .map((tag) => tag.replace('[', '').replace(']', ''))
                .map(escapeStringForUseInRegex)
                .join('|');

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

            /** iterating loop as long as the remainingMarkdownText of the markdown text exists and split the remainingMarkdownText when a domainCommand identifier is found */
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
    private parseLineForDomainCommand = (text: string): [string, DomainCommand | null] => {
        for (const domainCommand of this.domainCommands) {
            const possibleOpeningCommandIdentifier = [
                domainCommand.getOpeningIdentifier(),
                domainCommand.getOpeningIdentifier().toLowerCase(),
                domainCommand.getOpeningIdentifier().toUpperCase(),
            ];
            if (possibleOpeningCommandIdentifier.some((identifier) => text.indexOf(identifier) !== -1)) {
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
        if (this.previewMode) {
            this.onPreviewSelect.emit();
        } else {
            this.onEditSelect.emit();
        }
        // The text must only be parsed when the active tab before event was edit, otherwise the text can't have changed.
        if (event.activeId === 'editor_edit') {
            this.parse();
        }
    }

    /**
     * @function onFileUpload
     * @desc handle file upload for input
     * @param event
     */
    onFileUpload(event: any): void {
        if (event.target.files.length >= 1) {
            this.embedFiles(Array.from(event.target.files));
        }
    }

    /**
     * @function onFileDrop
     * @desc handle drop of files
     * @param {DragEvent} event
     */
    onFileDrop(event: DragEvent): void {
        event.preventDefault();
        if (event.dataTransfer?.items) {
            // Use DataTransferItemList interface to access the file(s)
            const files = new Array<File>();
            for (let i = 0; i < event.dataTransfer.items.length; i++) {
                // If dropped items aren't files, reject them
                if (event.dataTransfer.items[i].kind === 'file') {
                    const file = event.dataTransfer.items[i].getAsFile();
                    if (file) {
                        files.push(file);
                    }
                }
            }
            this.embedFiles(files);
        } else if (event.dataTransfer?.files) {
            // Use DataTransfer interface to access the file(s)
            this.embedFiles(Array.from(event.dataTransfer.files));
        }
    }

    /**
     * @function onFilePaste
     * @desc handle paste of files
     * @param {ClipboardEvent} event
     */
    onFilePaste(event: ClipboardEvent): void {
        if (event.clipboardData?.items) {
            const images = new Array<File>();
            for (let i = 0; i < event.clipboardData.items.length; i++) {
                if (event.clipboardData.items[i].kind === 'file') {
                    const file = event.clipboardData.items[i].getAsFile();
                    if (file) {
                        images.push(file);
                    }
                }
            }
            this.embedFiles(images);
        }
    }

    /**
     * @function embedFiles
     * @desc generate and embed markdown code for files
     * @param {FileList} files
     */
    embedFiles(files: File[]): void {
        const aceEditor = this.aceEditorContainer.getEditor();
        files.forEach((file: File) => {
            this.fileUploaderService.uploadMarkdownFile(file).then(
                (res) => {
                    const extension = file.name.split('.').pop()!.toLocaleLowerCase();

                    let textToAdd = `[${file.name}](${res.path})\n`;
                    if (extension !== 'pdf') {
                        textToAdd = '!' + textToAdd;
                    }

                    aceEditor.insert(textToAdd);
                },
                (error: Error) => {
                    this.alertService.addAlert({
                        type: AlertType.DANGER,
                        message: error.message,
                        disableTranslation: true,
                    });
                },
            );
        });
    }

    markdownTextChange(value: any) {
        this.markdown = value;
        this.markdownChange.emit(value as string);
    }
}
